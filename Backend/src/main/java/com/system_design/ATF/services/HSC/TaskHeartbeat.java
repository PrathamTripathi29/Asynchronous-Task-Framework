package com.system_design.ATF.services.HSC;

import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskHeartbeat {

    private final TaskRepository taskRepository;
    private final LambdaRepository lambdaRepository;

    @Transactional
    public void heartbeat(UUID taskId, String workerId){
        Optional<Task> taskOpt = taskRepository.findByIdForUpdate(taskId);
        if(taskOpt.isEmpty()) return;

        Task task = taskOpt.get();

        if(task.getClaimedBy() == null || !task.getClaimedBy().equals(workerId)){
            log.warn("Worker {} attempted to heartbeat task {} owned by {}", workerId, taskId, task.getClaimedBy());
            return;
        }

        if(task.getStatus() != TaskStatus.CLAIMED && task.getStatus() != TaskStatus.PROCESSING){
            log.debug("Worker {} attempted to heartbeat task {} in terminal/invalid state {}", workerId, taskId, task.getStatus());
            return;
        }

        if(task.getStatus() == TaskStatus.CLAIMED){
            task.setStatus(TaskStatus.PROCESSING);
            log.info("Task {} transitioned to PROCESSING via first heartbeat from worker {}", taskId, workerId);
        }

        Lambda lambda = lambdaRepository.findByName(task.getLambdaName()).orElse(null);
        int heartbeatTimeout = (lambda != null) ? lambda.getHeartbeatTimeoutSeconds() : 60;

        OffsetDateTime now = OffsetDateTime.now();
        task.setLastHeartBeat(now);
        task.setNextTriggerAt(now.plusSeconds(heartbeatTimeout));
        taskRepository.save(task);
    }
}
