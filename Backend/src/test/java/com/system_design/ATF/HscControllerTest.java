package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.controllers.HSCController;
import com.system_design.ATF.dtos.SetResultRequest;
import com.system_design.ATF.dtos.WorkerRequest;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Outcome;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.HSC.TaskClaimer;
import com.system_design.ATF.services.HSC.TaskHeartbeat;
import com.system_design.ATF.services.HSC.TaskResultSetter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HSCController.class)
public class HscControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Faking the underlying services so we can test the HTTP layer in isolation
    @MockitoBean private TaskClaimer taskClaimer;
    @MockitoBean private TaskHeartbeat heartbeatService;
    @MockitoBean private TaskResultSetter resultService;
    @MockitoBean private TaskRepository taskRepository;
    @MockitoBean private LambdaRepository lambdaRepository;

    private Task setupMockTask(UUID taskId) {
        Task task = Task.builder().id(taskId).lambdaName("test_lambda").build();
        Lambda lambda = Lambda.builder()
                .name("test_lambda")
                .callbackUrl("https://webhook.com")
                .heartbeatIntervalSeconds(10)
                .heartbeatTimeoutSeconds(60)
                .build();

        when(lambdaRepository.findByName("test_lambda")).thenReturn(Optional.of(lambda));
        return task;
    }

    @Test
    void testClaimTaskSuccessReturns200WithTaskDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = setupMockTask(taskId);
        WorkerRequest request = new WorkerRequest("worker-1");

        when(taskClaimer.claim(taskId, "worker-1")).thenReturn(Optional.of(task));

        mockMvc.perform(post("/internal/v1/tasks/" + taskId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.callbackUrl").value("https://webhook.com"))
                .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(10));
    }

    @Test
    void testClaimTaskFailsReturns409Conflict() throws Exception {
        UUID taskId = UUID.randomUUID();
        WorkerRequest request = new WorkerRequest("worker-1");

        // Simulate another worker beating this one to the DB lock
        when(taskClaimer.claim(taskId, "worker-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/internal/v1/tasks/" + taskId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()); // 409
    }

    @Test
    void testHeartbeatReturns204NoContent() throws Exception {
        UUID taskId = UUID.randomUUID();
        WorkerRequest request = new WorkerRequest("worker-1");

        mockMvc.perform(post("/internal/v1/tasks/" + taskId + "/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent()); // 204

        verify(heartbeatService).heartbeat(taskId, "worker-1");
    }

    @Test
    void testSetResultReturns204NoContent() throws Exception {
        UUID taskId = UUID.randomUUID();
        SetResultRequest request = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.SUCCESS)
                .build();

        mockMvc.perform(post("/internal/v1/tasks/" + taskId + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent()); // 204

        verify(resultService).setResult(eq(taskId), any(SetResultRequest.class));
    }

    @Test
    void testGetTaskReturns200WithTaskDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = setupMockTask(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        mockMvc.perform(get("/internal/v1/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.lambdaName").value("test_lambda"));
    }

    @Test
    void testGetNonExistentTaskReturns404NotFound() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/tasks/" + taskId))
                .andExpect(status().isNotFound()); // 404
    }
}