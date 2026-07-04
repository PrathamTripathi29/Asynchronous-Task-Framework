package com.system_design.ATF.controllers;

import com.system_design.ATF.dtos.SetResultRequest;
import com.system_design.ATF.dtos.TaskDetail;
import com.system_design.ATF.dtos.WorkerRequest;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.HSC.TaskClaimer;
import com.system_design.ATF.services.HSC.TaskHeartbeat;
import com.system_design.ATF.services.HSC.TaskResultSetter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/tasks")
@RequiredArgsConstructor
public class HSCController {
    private final TaskClaimer taskClaimer;
    private final TaskHeartbeat heartbeatService;
    private final TaskResultSetter resultSetter;
    private final TaskRepository taskRepository;
    private final LambdaRepository lambdaRepository;

    private TaskDetail buildTaskDetail(Task task){
        Lambda lambda = lambdaRepository.findByName(task.getLambdaName())
                .orElseThrow(()-> new IllegalStateException("Lambda config missing for task"));

        return TaskDetail.builder()
                .id(task.getId())
                .lambdaName(task.getLambdaName())
                .collectionName(task.getCollectionName())
                .payload(task.getPayload())
                .attemptCount(task.getAttemptCount())
                .callbackUrl(lambda.getCallbackUrl())
                .heartbeatIntervalSeconds(lambda.getHeartbeatIntervalSeconds())
                .heartbeatTimeoutSeconds(lambda.getHeartbeatTimeoutSeconds())
                .build();
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<TaskDetail> claimTask(@PathVariable UUID id, @Valid @RequestBody WorkerRequest request){
        Optional<Task> claimedTaskOpt = taskClaimer.claim(id, request.getWorkerId());
        if(claimedTaskOpt.isEmpty()){
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); //409
        }
        return ResponseEntity.ok(buildTaskDetail(claimedTaskOpt.get()));
    }

    @PostMapping("/{id}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT) //204
    public void heartbeat(@PathVariable UUID id, @Valid @RequestBody WorkerRequest request){
        heartbeatService.heartbeat(id, request.getWorkerId());
    }

    @PostMapping("/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setResult(@PathVariable UUID id, @Valid @RequestBody SetResultRequest request){
        resultSetter.setResult(id, request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDetail> getTask(@PathVariable UUID id){
        Optional<Task> taskOpt = taskRepository.findById(id);
        if(taskOpt.isEmpty()){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildTaskDetail(taskOpt.get()));
    }
}
