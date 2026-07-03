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
public class TaskClaimer {
    private final TaskRepository taskRepository;
    private final LambdaRepository lambdaRepository;

    @Transactional
    public Optional<Task> claim(UUID taskId, String workerId){
        Optional<Task> lockedTaskOpt = taskRepository.findByIdForUpdate(taskId);

        if(lockedTaskOpt.isEmpty()){
            return Optional.empty();
        }

        Task task = lockedTaskOpt.get();

        if(task.getStatus() != TaskStatus.ENQUEUED){
            log.debug("Task {} is in state {}, rejecting claim for worker {}", taskId, task.getStatus(), workerId);
            return Optional.empty();
        }

        Lambda lambda = lambdaRepository.findByName(task.getLambdaName()).orElse(null);
        int claimTimeout = (lambda != null) ? lambda.getClaimTimeoutSeconds() : 300;
        task.setStatus(TaskStatus.PROCESSING);
        task.setClaimedBy(workerId);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setNextTriggerAt(OffsetDateTime.now().plusSeconds(claimTimeout));

        taskRepository.save(task);

        log.info("Task {} successfully claimed by worker {}", taskId, workerId);

        return Optional.of(task);
    }
}
