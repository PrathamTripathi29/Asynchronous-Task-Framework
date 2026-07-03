package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.HSC.TaskClaimer;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class TaskClaimerTest {

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
    private TaskClaimer taskClaimer;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LambdaRepository lambdaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setupLambda(String name) {
        Lambda lambda = Lambda.builder()
                .name(name)
                .status(LambdaStatus.ACTIVE)
                .claimTimeoutSeconds(120)
                .callbackUrl("http://dummy-url.com") // Satisfies NOT NULL constraint
                .ownerTeam("core-team")              // Satisfies NOT NULL constraint
                .build();
        lambdaRepository.save(lambda);
    }

    private Task setupTask(String lambdaName, TaskStatus status) {
        Task task = Task.builder()
                .lambdaName(lambdaName)
                .status(status)
                .priority(Priority.MEDIUM)
                .attemptCount(0)
                .scheduledAt(OffsetDateTime.now())
                .nextTriggerAt(OffsetDateTime.now())
                .payload(objectMapper.createObjectNode().put("test", "data")) // Satisfies NOT NULL constraint
                .build();
        return taskRepository.save(task);
    }

    @Test
    void testClaimEnqueuedTaskSuccess() {
        setupLambda("test_claim");
        Task task = setupTask("test_claim", TaskStatus.ENQUEUED);

        Optional<Task> claimed = taskClaimer.claim(task.getId(), "worker-1");

        assertTrue(claimed.isPresent());
        assertEquals("worker-1", claimed.get().getClaimedBy());
        assertEquals(TaskStatus.PROCESSING, claimed.get().getStatus());
        assertEquals(1, claimed.get().getAttemptCount());

        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), claimed.get().getNextTriggerAt());
        assertTrue(diffSeconds >= 115 && diffSeconds <= 125);
    }

    @Test
    void testClaimProcessingTaskFails() {
        setupLambda("test_processing");
        Task task = setupTask("test_processing", TaskStatus.PROCESSING);

        Optional<Task> claimed = taskClaimer.claim(task.getId(), "worker-1");
        assertFalse(claimed.isPresent());
    }

    @Test
    void testClaimNewTaskFails() {
        setupLambda("test_new");
        Task task = setupTask("test_new", TaskStatus.NEW);

        Optional<Task> claimed = taskClaimer.claim(task.getId(), "worker-1");
        assertFalse(claimed.isPresent());
    }

    @Test
    void testConcurrentClaims() throws InterruptedException {
        setupLambda("test_concurrent");
        Task task = setupTask("test_concurrent", TaskStatus.ENQUEUED);
        UUID taskId = task.getId();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String workerId = "worker-" + i;
            executor.submit(() -> {
                try {
                    // Block until the latch drops so all 5 threads hit the DB at the exact same millisecond
                    readyLatch.await();

                    Optional<Task> result = taskClaimer.claim(taskId, workerId);
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.countDown(); // Release the hounds!
        doneLatch.await(); // Wait for all threads to finish

        // Assert strictly 1 winner and 4 losers
        assertEquals(1, successCount.get(), "Exactly one worker should succeed");
        assertEquals(4, failCount.get(), "Exactly four workers should fail");

        // Verify the winning worker is correctly saved in the DB
        Task finalTask = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskStatus.PROCESSING, finalTask.getStatus());
        assertNotNull(finalTask.getClaimedBy());
        assertTrue(finalTask.getClaimedBy().startsWith("worker-"));
    }
}