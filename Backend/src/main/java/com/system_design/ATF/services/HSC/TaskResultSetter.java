package com.system_design.ATF.services.HSC;

import com.system_design.ATF.dtos.SetResultRequest;
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
public class TaskResultSetter {

    private final TaskRepository taskRepository;
    private final LambdaRepository lambdaRepository;

    @Transactional
    public void setResult(UUID taskId, SetResultRequest request){
        Optional<Task> taskOpt = taskRepository.findById(taskId);

        if(taskOpt.isEmpty()){
            log.warn("Attempted to set result for non-existent task {}", taskId);
            return;
        }

        Task task = taskOpt.get();

        if(task.getClaimedBy() == null || !task.getClaimedBy().equals(request.getWorkerId())){
            log.warn("Worker {} attempted to set result for task {} owned by {}", request.getWorkerId(), taskId, task.getClaimedBy());
            return;
        }

        if(task.getStatus() == TaskStatus.SUCCESS ||
            task.getStatus() == TaskStatus.FATAL_FAILURE ||
            task.getStatus() == TaskStatus.DROPPED){
            log.debug("Task {} is already in terminal state {}.", taskId, task.getStatus());
            return;
        }

        switch (request.getOutcome()) {
            case SUCCESS:
                task.setStatus(TaskStatus.SUCCESS);
                task.setResult(request.getResult());
                task.setHttpStatusCode(request.getHttpStatusCode());
                log.info("Task {} completed successfully by worker {}", taskId, request.getWorkerId());
                break;

            case FATAL_FAILURE:
                task.setStatus(TaskStatus.FATAL_FAILURE);
                task.setErrorMessage(request.getErrorMessage());
                task.setHttpStatusCode(request.getHttpStatusCode());
                log.error("Task {} suffered a FATAL failure: {}", taskId, request.getErrorMessage());
                break;

            case RETRIABLE_FAILURE:
                Lambda lambda = lambdaRepository.findByName(task.getLambdaName()).orElse(null);
                int maxAttempts = (lambda != null) ? lambda.getMaxAttempts() : 3;

                if (task.getAttemptCount() >= maxAttempts) {

                    task.setStatus(TaskStatus.FATAL_FAILURE);
                    task.setErrorMessage("Max retries exceeded. Last Error: " + request.getErrorMessage());
                    task.setHttpStatusCode(request.getHttpStatusCode());
                    log.warn("Task {} exceeded max retries ({}). Promoted to FATAL_FAILURE.", taskId, maxAttempts);
                } else {

                    task.setStatus(TaskStatus.RETRIABLE_FAILURE);
                    task.setErrorMessage(request.getErrorMessage());
                    task.setHttpStatusCode(request.getHttpStatusCode());

                    int exponent = Math.max(0, task.getAttemptCount() - 1);
                    long delaySeconds = 60L * (long) Math.pow(2, exponent);

                    task.setNextTriggerAt(OffsetDateTime.now().plusSeconds(delaySeconds));
                    log.info("Task {} suffered RETRIABLE failure. Backing off for {} seconds.", taskId, delaySeconds);
                }
                break;
        }
        taskRepository.save(task);
    }
}
