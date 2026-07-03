package com.system_design.ATF.services;

import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.CollectionRepository;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoreConsumer {
    private final TaskRepository taskRepository;
    private final LambdaRepository lambdaRepository;
    private final CollectionRepository collectionRepository;
    private final RedisStreamPublisher redisStreamPublisher;
    private static final int POLL_BATCH_SIZE = 100;

    private void markFatal(Task task, String reason){
        task.setStatus(TaskStatus.FATAL_FAILURE);
        task.setErrorMessage(reason);
        taskRepository.save(task);
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void poll(){
        OffsetDateTime current = OffsetDateTime.now();
        List<Task> dueTasks = taskRepository.findDueTasks(current, POLL_BATCH_SIZE);

        if(dueTasks.isEmpty()) return;
        log.debug("Store consumer found {} due tasks", dueTasks.size());

        for(Task task : dueTasks){
            processDueTask(task, current);
        }
    }

    private void processDueTask(Task task, OffsetDateTime current){
        Lambda lambda = lambdaRepository.findByName(task.getLambdaName()).orElse(null);

        if(lambda == null){
            markFatal(task, "Lambda not registered: " + task.getLambdaName());
            return;
        }

        if(lambda.getStatus() == LambdaStatus.PAUSED || lambda.getStatus() == LambdaStatus.GATED){
            if(lambda.getGateAction() == GateAction.PAUSE){
                task.setNextTriggerAt(current.plusSeconds(60));
                taskRepository.save(task);
                return;
            }
        }

        if(task.getCollectionName() != null && !task.getCollectionName().isBlank()){
            Optional<Collection> collection = collectionRepository.findByLambdaNameAndName(task.getLambdaName(), task.getCollectionName());

            if(collection.isPresent() && (collection.get().getStatus() == LambdaStatus.PAUSED || collection.get().getStatus() == LambdaStatus.GATED)){
                if(collection.get().getGateAction() == GateAction.PAUSE){
                    task.setNextTriggerAt(current.plusSeconds(60));
                    taskRepository.save(task);
                    return;
                }
            }
        }

        redisStreamPublisher.publish(task);
        task.setStatus(TaskStatus.ENQUEUED);
        task.setNextTriggerAt(current.plusSeconds(lambda.getEnqueueTimeoutSeconds()));
        taskRepository.save(task);
    }
}
