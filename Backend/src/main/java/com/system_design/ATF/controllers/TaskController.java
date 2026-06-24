package com.system_design.ATF.controllers;

import com.system_design.ATF.dtos.ScheduleTaskRequest;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import com.system_design.ATF.services.TaskSchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1")
@RequiredArgsConstructor
public class TaskController {
    private final TaskSchedulingService taskSchedulingService;

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task scheduleTask(@Valid @RequestBody ScheduleTaskRequest request){
        return taskSchedulingService.schedule(request);
    }

    @GetMapping("/tasks/{id}")
    public Task getTask(@PathVariable UUID id){
        return taskSchedulingService.getTask(id);
    }

    @GetMapping("/lambdas/{name}/tasks")
    public Page<Task> getTasksByLambda(@PathVariable String name, @RequestParam(required = false) TaskStatus status, Pageable pageable){
        return taskSchedulingService.getTasksByLambda(name, status, pageable);
    }
}
