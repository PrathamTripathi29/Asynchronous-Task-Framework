package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Priority;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class StoreConsumerQueryTest {

    // THE FIX: Removed the <?> and <> because Testcontainers 2.0 dropped generics
    @Container
    public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("atf_db")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LambdaRepository lambdaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private Task createTask(TaskStatus status, OffsetDateTime nextTriggerAt) {
        return Task.builder()
                .lambdaName("ConcurrencyLambda")
                .status(status)
                .priority(Priority.MEDIUM)
                .payload(mapper.createObjectNode().put("test", "data"))
                .nextTriggerAt(nextTriggerAt.truncatedTo(ChronoUnit.MICROS))
                .scheduledAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS))
                .build();
    }

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll(); // Clean slate for each test

        if (lambdaRepository.count() == 0) {
            lambdaRepository.save(Lambda.builder().name("ConcurrencyLambda").callbackUrl("http://localhost").build());
        }
    }

    @Test
    void testFindDueTasksBasicRules() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Past NEW task (Should be returned)
        taskRepository.save(createTask(TaskStatus.NEW, now.minusHours(1)));

        // 2. Future NEW task (Should NOT be returned)
        taskRepository.save(createTask(TaskStatus.NEW, now.plusHours(1)));

        // 3. Past SUCCESS task (Terminal, should NOT be returned)
        taskRepository.save(createTask(TaskStatus.SUCCESS, now.minusHours(1)));

        // 4. Past FATAL_FAILURE task (Terminal, should NOT be returned)
        taskRepository.save(createTask(TaskStatus.FATAL_FAILURE, now.minusHours(1)));

        List<Task> results = taskRepository.findDueTasks(now, 10);
        assertEquals(1, results.size(), "Only the past NEW task should be returned");
    }

    @Test
    void testBatchSizeLimits() {
        OffsetDateTime now = OffsetDateTime.now();
        // Insert 15 due tasks
        for (int i = 0; i < 15; i++) {
            taskRepository.save(createTask(TaskStatus.NEW, now.minusMinutes(1)));
        }

        List<Task> results = taskRepository.findDueTasks(now, 10);
        assertEquals(10, results.size(), "Should respect the batch size limit of 10");
    }

    @Test
    void testAllActiveStatusesArePolled() {
        OffsetDateTime now = OffsetDateTime.now();
        taskRepository.save(createTask(TaskStatus.NEW, now.minusMinutes(1)));
        taskRepository.save(createTask(TaskStatus.ENQUEUED, now.minusMinutes(1)));
        taskRepository.save(createTask(TaskStatus.CLAIMED, now.minusMinutes(1)));
        taskRepository.save(createTask(TaskStatus.PROCESSING, now.minusMinutes(1)));
        taskRepository.save(createTask(TaskStatus.RETRIABLE_FAILURE, now.minusMinutes(1)));

        List<Task> results = taskRepository.findDueTasks(now, 10);
        assertEquals(5, results.size(), "All non-terminal statuses should be fetched if their trigger time has passed");
    }

    // =================================================================
    // THE ULTIMATE TEST: PROVING "SKIP LOCKED" PREVENTS RACE CONDITIONS
    // =================================================================
    @Test
    void testConcurrencyWithSkipLocked() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Insert 10 tasks all due exactly right now
        for (int i = 0; i < 10; i++) {
            taskRepository.save(createTask(TaskStatus.NEW, now.minusSeconds(10)));
        }

        // We will store the results fetched by two separate concurrent threads
        List<Task> thread1Tasks = new ArrayList<>();
        List<Task> thread2Tasks = new ArrayList<>();

        // Latches to perfectly synchronize the two threads
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch testFinishedLatch = new CountDownLatch(2);

        // THE FIX: Java 21 try-with-resources automatically safely shuts down the ExecutorService
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            // THREAD 1
            executor.submit(() -> {
                transactionTemplate.execute(status -> {
                    // Thread 1 pulls 5 tasks and holds the row locks
                    thread1Tasks.addAll(taskRepository.findDueTasks(now, 5));
                    lockAcquiredLatch.countDown(); // Signal Thread 2 to proceed

                    // Sleep to simulate processing time, holding the lock on these 5 tasks
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    return null;
                });
                testFinishedLatch.countDown();
            });

            // THREAD 2
            executor.submit(() -> {
                try {
                    // Wait until Thread 1 has successfully locked the first 5 tasks
                    lockAcquiredLatch.await();

                    transactionTemplate.execute(status -> {
                        // Thread 2 tries to pull 5 tasks. Thanks to SKIP LOCKED,
                        // it will skip Thread 1's tasks and grab the remaining 5 instantly.
                        thread2Tasks.addAll(taskRepository.findDueTasks(now, 5));
                        return null;
                    });
                } catch (Exception ignored) {}
                testFinishedLatch.countDown();
            });

            // Wait for both transactions to fully commit and release
            testFinishedLatch.await();
        }

        // ------------------
        // ASSERTIONS
        // ------------------
        assertEquals(5, thread1Tasks.size(), "Thread 1 should have grabbed exactly 5 tasks");
        assertEquals(5, thread2Tasks.size(), "Thread 2 should have grabbed exactly 5 tasks without blocking");

        // Combine the results. If SKIP LOCKED failed, there would be duplicates.
        long uniqueTaskIds = java.util.stream.Stream.concat(thread1Tasks.stream(), thread2Tasks.stream())
                .map(Task::getId)
                .distinct()
                .count();

        assertEquals(10, uniqueTaskIds, "CRITICAL: The union of both threads should be exactly 10 unique tasks. No overlap!");
    }
}