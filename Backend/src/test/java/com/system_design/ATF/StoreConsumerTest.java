package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.components.StreamKeyResolver;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.StoreConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class StoreConsumerTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private StoreConsumer storeConsumer;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LambdaRepository lambdaRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setupLambda(String name, GateAction gateAction) {
        Lambda lambda = Lambda.builder()
                .name(name)
                .gateAction(gateAction)
                .status(gateAction == GateAction.PAUSE ? LambdaStatus.PAUSED : LambdaStatus.ACTIVE)
                .callbackUrl("http://api")
                .ownerTeam("team")
                .enqueueTimeoutSeconds(300)
                .build();
        lambdaRepository.save(lambda);
    }

    private Task setupTask(String lambdaName, TaskStatus status, OffsetDateTime nextTriggerAt) {
        Task task = Task.builder()
                .lambdaName(lambdaName)
                .status(status)
                .nextTriggerAt(nextTriggerAt)
                .scheduledAt(OffsetDateTime.now())
                .priority(Priority.MEDIUM)
                .payload(objectMapper.createObjectNode().put("key", "val"))
                .attemptCount(0)
                .build();
        return taskRepository.save(task);
    }

    @SuppressWarnings({"unchecked", "varargs"})
    private boolean isTaskInStream(String lambdaName, Task task) {
        String streamKey = StreamKeyResolver.getStreamKey(lambdaName, Priority.MEDIUM);
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(StreamOffset.fromStart(streamKey));

        if (messages == null) return false;
        return messages.stream()
                .anyMatch(msg -> msg.getValue().get("taskId").equals(task.getId().toString()));
    }

    @Test
    void testPollNewTaskEnqueuesAndPublishes() {
        setupLambda("active_lambda", null);
        Task task = setupTask("active_lambda", TaskStatus.NEW, OffsetDateTime.now().minusMinutes(1));

        storeConsumer.poll();

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.ENQUEUED, updatedTask.getStatus());
        assertTrue(updatedTask.getNextTriggerAt().isAfter(OffsetDateTime.now().plusMinutes(4))); // 300s timeout
        assertTrue(isTaskInStream("active_lambda", task));
    }

    @Test
    void testPollFutureTaskIgnored() {
        setupLambda("future_lambda", null);
        Task task = setupTask("future_lambda", TaskStatus.NEW, OffsetDateTime.now().plusHours(1));

        storeConsumer.poll();

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.NEW, updatedTask.getStatus());
        assertFalse(isTaskInStream("future_lambda", task));
    }

    @Test
    void testPollPausedLambdaPushesForward60Seconds() {
        setupLambda("paused_lambda", GateAction.PAUSE);
        Task task = setupTask("paused_lambda", TaskStatus.NEW, OffsetDateTime.now().minusMinutes(1));

        storeConsumer.poll();

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.NEW, updatedTask.getStatus()); // Stays NEW

        long diffSeconds = ChronoUnit.SECONDS.between(OffsetDateTime.now(), updatedTask.getNextTriggerAt());
        assertTrue(diffSeconds >= 55 && diffSeconds <= 65); // Pushed forward by 60s

        assertFalse(isTaskInStream("paused_lambda", task)); // Nothing in Redis
    }

    @Test
    void testPollRetriableFailureEnqueuesAndPublishes() {
        setupLambda("retry_lambda", null);
        Task task = setupTask("retry_lambda", TaskStatus.RETRIABLE_FAILURE, OffsetDateTime.now().minusMinutes(1));

        storeConsumer.poll();

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.ENQUEUED, updatedTask.getStatus());
        assertTrue(isTaskInStream("retry_lambda", task));
    }

    @Test
    void testPollSuccessTaskIgnored() {
        setupLambda("success_lambda", null);
        Task task = setupTask("success_lambda", TaskStatus.SUCCESS, OffsetDateTime.now().minusMinutes(1));

        storeConsumer.poll();

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.SUCCESS, updatedTask.getStatus());
        assertFalse(isTaskInStream("success_lambda", task));
    }

    @Test
    void testBatchingLimitsTo100() {
        setupLambda("batch_lambda", null);

        // Insert 105 tasks
        for (int i = 0; i < 105; i++) {
            setupTask("batch_lambda", TaskStatus.NEW, OffsetDateTime.now().minusMinutes(1));
        }

        // First poll handles up to POLL_BATCH_SIZE (100)
        storeConsumer.poll();
        long enqueuedCount = taskRepository.findAll().stream()
                .filter(t -> t.getLambdaName().equals("batch_lambda") && t.getStatus() == TaskStatus.ENQUEUED)
                .count();
        assertEquals(100, enqueuedCount);

        // Second poll handles remaining 5
        storeConsumer.poll();
        long totalEnqueuedCount = taskRepository.findAll().stream()
                .filter(t -> t.getLambdaName().equals("batch_lambda") && t.getStatus() == TaskStatus.ENQUEUED)
                .count();
        assertEquals(105, totalEnqueuedCount);
    }
}