package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.system_design.ATF.dtos.SetResultRequest;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.HSC.TaskResultSetter;
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
public class TaskResultServiceTest {

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
    private TaskResultSetter taskResultService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LambdaRepository lambdaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setupLambda(String name, int maxAttempts) {
        Lambda lambda = Lambda.builder()
                .name(name)
                .status(LambdaStatus.ACTIVE)
                .maxAttempts(maxAttempts)
                .callbackUrl("http://dummy.url") // Satisfies DB Constraints
                .ownerTeam("team-a")             // Satisfies DB Constraints
                .build();
        lambdaRepository.save(lambda);
    }

    private Task setupTask(String lambdaName, TaskStatus status, String workerId, int attemptCount) {
        Task task = Task.builder()
                .lambdaName(lambdaName)
                .status(status)
                .claimedBy(workerId)
                .attemptCount(attemptCount)
                .priority(Priority.MEDIUM)
                .scheduledAt(OffsetDateTime.now())
                .nextTriggerAt(OffsetDateTime.now())
                .payload(objectMapper.createObjectNode().put("dummy", "payload")) // Satisfies DB Constraints
                .build();
        return taskRepository.save(task);
    }

    @Test
    void testSetResultSuccess() {
        setupLambda("lambda_success", 3);
        Task task = setupTask("lambda_success", TaskStatus.PROCESSING, "worker-1", 1);

        ObjectNode resultJson = objectMapper.createObjectNode().put("data", "abc");
        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.SUCCESS)
                .result(resultJson)
                .httpStatusCode(200)
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.SUCCESS, updatedTask.getStatus());
        assertEquals("abc", updatedTask.getResult().get("data").asText());
        assertEquals(200, updatedTask.getHttpStatusCode());
    }

    @Test
    void testSetResultFatalFailure() {
        setupLambda("lambda_fatal", 3);
        Task task = setupTask("lambda_fatal", TaskStatus.PROCESSING, "worker-1", 1);

        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.FATAL_FAILURE)
                .errorMessage("Hard crash")
                .httpStatusCode(500)
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.FATAL_FAILURE, updatedTask.getStatus());
        assertEquals("Hard crash", updatedTask.getErrorMessage());
        assertEquals(500, updatedTask.getHttpStatusCode());
    }

    @Test
    void testSetResultRetriableFailureAttempt1() {
        setupLambda("lambda_retry_1", 3);
        // Attempt 1 -> Next retry should be 60 seconds from now
        Task task = setupTask("lambda_retry_1", TaskStatus.PROCESSING, "worker-1", 1);

        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.RETRIABLE_FAILURE)
                .errorMessage("Temporary network blip")
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.RETRIABLE_FAILURE, updatedTask.getStatus());

        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), updatedTask.getNextTriggerAt());
        assertTrue(diffSeconds >= 55 && diffSeconds <= 65); // Approx 60s
    }

    @Test
    void testSetResultRetriableFailureAttempt2() {
        setupLambda("lambda_retry_2", 3);
        // Attempt 2 -> Next retry should be 120 seconds from now (60 * 2^(2-1))
        Task task = setupTask("lambda_retry_2", TaskStatus.PROCESSING, "worker-1", 2);

        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.RETRIABLE_FAILURE)
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.RETRIABLE_FAILURE, updatedTask.getStatus());

        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), updatedTask.getNextTriggerAt());
        assertTrue(diffSeconds >= 115 && diffSeconds <= 125); // Approx 120s
    }

    @Test
    void testSetResultRetriableFailureMaxAttemptsReached() {
        setupLambda("lambda_retry_max", 3);
        // Attempt 3, and max attempts is 3. Should promote to FATAL.
        Task task = setupTask("lambda_retry_max", TaskStatus.PROCESSING, "worker-1", 3);

        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-1")
                .outcome(Outcome.RETRIABLE_FAILURE)
                .errorMessage("Still failing")
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        // Verifying the promotion
        assertEquals(TaskStatus.FATAL_FAILURE, updatedTask.getStatus());
        assertTrue(updatedTask.getErrorMessage().contains("Max retries exceeded"));
    }

    @Test
    void testSetResultWrongWorkerRejected() {
        setupLambda("lambda_wrong_worker", 3);
        Task task = setupTask("lambda_wrong_worker", TaskStatus.PROCESSING, "worker-1", 1);

        // A malicious/misconfigured worker-2 tries to finish worker-1's task
        SetResultRequest req = SetResultRequest.builder()
                .workerId("worker-2")
                .outcome(Outcome.SUCCESS)
                .build();

        taskResultService.setResult(task.getId(), req);

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        // Verify no DB changes occurred
        assertEquals(TaskStatus.PROCESSING, updatedTask.getStatus());
    }
}