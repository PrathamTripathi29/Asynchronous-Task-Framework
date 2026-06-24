package com.system_design.ATF.services;

import com.system_design.ATF.dtos.ScheduleTaskRequest;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.exception.TaskNotFoundException;
import com.system_design.ATF.repository.CollectionRepository;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskSchedulingService {
    private final LambdaRepository lambdaRepository;
    private final TaskRepository taskRepository;
    private final CollectionRepository collectionRepository;

    public Task schedule(ScheduleTaskRequest request){
        Lambda lambda = lambdaRepository.findByName(request.getLambdaName())
                .orElseThrow(() -> new LambdaNotFoundException("Lambda " + request.getLambdaName() + " not found."));

        if(GateAction.DROP.equals(lambda.getGateAction())){
            return Task.builder().status(TaskStatus.DROPPED).build();
        }

        GateAction activeGate = lambda.getGateAction();

        if(request.getCollectionName() != null && !request.getCollectionName().isBlank()){
            Collection collection = collectionRepository.findByLambdaNameAndName(request.getLambdaName(), request.getCollectionName())
                    .orElse(null);

            if(collection != null){
                if(GateAction.DROP.equals(collection.getGateAction())){
                    return Task.builder().status(TaskStatus.DROPPED).build();
                }
                if(GateAction.PAUSE.equals(collection.getGateAction())){
                    activeGate = GateAction.PAUSE;
                }
            }
        }

        Priority priority = request.getPriority() != null ? request.getPriority() : Priority.MEDIUM;

        OffsetDateTime scheduledAt = request.getScheduledAt() != null ? request.getScheduledAt() : OffsetDateTime.now();
        OffsetDateTime nextTriggerAt = scheduledAt;

        if(GateAction.PAUSE.equals(activeGate)){
            nextTriggerAt = OffsetDateTime.now().plusDays(36500);
        }

        Task task = Task.builder()
                .lambdaName(request.getLambdaName())
                .collectionName(request.getCollectionName())
                .priority(priority)
                .status(TaskStatus.NEW)
                .scheduledAt(scheduledAt)
                .nextTriggerAt(nextTriggerAt)
                .attemptCount(0)
                .build();

        return taskRepository.save(task);
    }

    public Task getTask(UUID id){
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found."));
    }

    public Page<Task> getTasksByLambda(String lambdaName, TaskStatus status, Pageable pageable){
        if(status != null){
            return taskRepository.findByLambdaNameAndStatus(lambdaName, status, pageable);
        }
        return taskRepository.findByLambdaName(lambdaName, pageable);
    }
}
