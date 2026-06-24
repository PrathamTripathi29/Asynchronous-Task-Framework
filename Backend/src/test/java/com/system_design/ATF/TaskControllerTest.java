package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.controllers.TaskController;
import com.system_design.ATF.dtos.ScheduleTaskRequest;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import com.system_design.ATF.exception.TaskNotFoundException;
import com.system_design.ATF.services.TaskSchedulingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Instantiated manually. findAndRegisterModules() ensures OffsetDateTime is handled!
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // Completely fake the service layer
    @MockitoBean
    private TaskSchedulingService taskSchedulingService;

    // TEST 1: POST /api/v1/tasks with valid body -> 201
    @Test
    void testScheduleValidTask() throws Exception {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().lambdaName("EmailLambda").build();
        Task savedTask = Task.builder()
                .id(UUID.randomUUID())
                .lambdaName("EmailLambda")
                .status(TaskStatus.NEW)
                .build();

        when(taskSchedulingService.schedule(any())).thenReturn(savedTask);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.lambdaName").value("EmailLambda"));
    }

    // TEST 2: POST /api/v1/tasks with missing lambdaName -> 400
    @Test
    void testScheduleMissingLambdaName() throws Exception {
        // lambdaName is missing (null) which violates @NotBlank in the DTO
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().build();

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // TEST 3: GET /api/v1/tasks/{nonexistent} -> 404
    @Test
    void testGetNonExistentTask() throws Exception {
        UUID ghostId = UUID.randomUUID();
        when(taskSchedulingService.getTask(ghostId)).thenThrow(new TaskNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/tasks/" + ghostId))
                .andExpect(status().isNotFound());
    }

    // TEST 4: GET /api/v1/lambdas/{name}/tasks?status=SUCCESS -> 200
    @Test
    void testGetTasksByLambdaAndStatus() throws Exception {
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .lambdaName("send_email")
                .status(TaskStatus.SUCCESS)
                .build();

        when(taskSchedulingService.getTasksByLambda(eq("send_email"), eq(TaskStatus.SUCCESS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        mockMvc.perform(get("/api/v1/lambdas/send_email/tasks")
                        .param("status", "SUCCESS")) // Pass the status query param
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }
}