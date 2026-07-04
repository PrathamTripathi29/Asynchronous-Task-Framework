package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.HSC.TaskHeartbeat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class TaskHeartbeatServiceTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaskHeartbeat taskHeartbeatService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LambdaRepository lambdaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setupLambda(String name) {
        Lambda lambda = Lambda.builder()
                .name(name)
                .status(LambdaStatus.ACTIVE)
                .heartbeatTimeoutSeconds(120) // Custom timeout for testing
                .callbackUrl("http://dummy-url.com")
                .ownerTeam("core-team")
                .build();
        lambdaRepository.save(lambda);
    }

    private Task setupTask(String lambdaName, TaskStatus status, String workerId) {
        Task task = Task.builder()
                .lambdaName(lambdaName)
                .status(status)
                .claimedBy(workerId)
                .priority(Priority.MEDIUM)
                .attemptCount(1)
                .scheduledAt(OffsetDateTime.now())
                .nextTriggerAt(OffsetDateTime.now().plusSeconds(30)) // Short initial trigger
                .payload(objectMapper.createObjectNode().put("test", "data"))
                .build();
        return taskRepository.save(task);
    }

    @Test
    void testHeartbeatClaimedTaskTransitionsToProcessing() {
        setupLambda("test_hb_claim");
        Task task = setupTask("test_hb_claim", TaskStatus.CLAIMED, "worker-1");

        taskHeartbeatService.heartbeat(task.getId(), "worker-1");

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.PROCESSING, updatedTask.getStatus());
        assertNotNull(updatedTask.getLastHeartBeat());

        // Verify the next trigger was pushed forward by exactly the heartbeat timeout amount (120s)
        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), updatedTask.getNextTriggerAt());
        assertTrue(diffSeconds >= 115 && diffSeconds <= 125);
    }

    @Test
    void testHeartbeatProcessingTaskExtendsTimeout() throws InterruptedException {
        setupLambda("test_hb_proc");
        Task task = setupTask("test_hb_proc", TaskStatus.PROCESSING, "worker-1");
        OffsetDateTime initialNextTrigger = task.getNextTriggerAt();

        // Wait slightly so the newly generated nextTriggerAt is measurably different
        Thread.sleep(1000);

        taskHeartbeatService.heartbeat(task.getId(), "worker-1");

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.PROCESSING, updatedTask.getStatus());
        assertTrue(updatedTask.getNextTriggerAt().isAfter(initialNextTrigger));

        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), updatedTask.getNextTriggerAt());
        assertTrue(diffSeconds >= 115 && diffSeconds <= 125);
    }

    @Test
    void testHeartbeatWrongWorkerRejected() {
        setupLambda("test_hb_wrong_worker");
        Task task = setupTask("test_hb_wrong_worker", TaskStatus.CLAIMED, "worker-1");

        // Malicious or misconfigured worker tries to heartbeat a task it doesn't own
        taskHeartbeatService.heartbeat(task.getId(), "worker-2");

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.CLAIMED, updatedTask.getStatus()); // Status must be untouched
        assertNull(updatedTask.getLastHeartBeat()); // Timestamps must be untouched
    }

    @Test
    void testHeartbeatSuccessTaskRejected() {
        setupLambda("test_hb_success");
        Task task = setupTask("test_hb_success", TaskStatus.SUCCESS, "worker-1");

        taskHeartbeatService.heartbeat(task.getId(), "worker-1");

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.SUCCESS, updatedTask.getStatus()); // Untouched
        assertNull(updatedTask.getLastHeartBeat());
    }
}