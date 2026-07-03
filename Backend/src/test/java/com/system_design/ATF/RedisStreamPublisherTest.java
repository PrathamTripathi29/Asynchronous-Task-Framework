package com.system_design.ATF;

import com.system_design.ATF.components.StreamKeyResolver;
import com.system_design.ATF.entity.Priority;
import com.system_design.ATF.entity.Task;
import com.system_design.ATF.services.RedisStreamPublisher;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class RedisStreamPublisherTest {

    // 1. Add Postgres so the Spring Context doesn't crash!
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    // 2. Keep the existing Redis container
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Wire up Postgres
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Wire up Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisStreamPublisher redisStreamPublisher;

    @BeforeEach
    void setUp() {
        // Clear keys for clean test state
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void testPublishAddsMessageWithTaskId() {
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .lambdaName("test_lambda")
                .priority(Priority.HIGH)
                .build();

        redisStreamPublisher.publish(task);

        String expectedStreamKey = StreamKeyResolver.getStreamKey("test_lambda", Priority.HIGH);
        Long size = redisTemplate.opsForStream().size(expectedStreamKey);
        assertEquals(1L, size);

        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(StreamOffset.fromStart(expectedStreamKey));

        assertNotNull(messages);
        assertEquals(1, messages.size());
        // Verify it uses "task_id"
        assertEquals(task.getId().toString(), messages.get(0).getValue().get("task_id"));
    }

    @Test
    void testPublishRoutesToCorrectPriorityStream() {
        Task taskHigh = Task.builder().id(UUID.randomUUID()).lambdaName("route_lambda").priority(Priority.HIGH).build();
        Task taskLow = Task.builder().id(UUID.randomUUID()).lambdaName("route_lambda").priority(Priority.LOW).build();

        redisStreamPublisher.publish(taskHigh);
        redisStreamPublisher.publish(taskLow);

        String highKey = StreamKeyResolver.getStreamKey("route_lambda", Priority.HIGH);
        String lowKey = StreamKeyResolver.getStreamKey("route_lambda", Priority.LOW);

        assertEquals(1L, redisTemplate.opsForStream().size(highKey));
        assertEquals(1L, redisTemplate.opsForStream().size(lowKey));
    }

    @Test
    void testPublishMaintainsApproximateMaxLen() {
        String streamKey = StreamKeyResolver.getStreamKey("bulk_lambda", Priority.MEDIUM);

        // Publish 15,000 tasks, heavily exceeding the 10,000 threshold
        for (int i = 0; i < 15000; i++) {
            Task task = Task.builder()
                    .id(UUID.randomUUID())
                    .lambdaName("bulk_lambda")
                    .priority(Priority.MEDIUM)
                    .build();
            redisStreamPublisher.publish(task);
        }

        Long finalSize = redisTemplate.opsForStream().size(streamKey);

        // MAXLEN ~ 10000 means it might not strictly be 10000, but it drops macro blocks.
        // It definitely won't be 15000. It typically trims down to slightly above 10000.
        assertNotNull(finalSize);
        assertTrue(finalSize <= 10500, "Stream size should be approximately trimmed to 10000, but was " + finalSize);
    }
}