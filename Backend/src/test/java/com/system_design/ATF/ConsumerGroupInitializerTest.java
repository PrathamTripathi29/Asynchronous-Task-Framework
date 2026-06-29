package com.system_design.ATF;

import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Priority;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.services.RedisConsumerGroupInitializer;
import com.system_design.ATF.components.StreamKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Testcontainers // Triggers Testcontainers to boot up real instances via Docker
public class ConsumerGroupInitializerTest {

    // 1. We must spin up Postgres so the Spring Context (JPA) can boot successfully!
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // 2. Spin up Redis
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Wire up the Postgres credentials
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
    private LambdaRepository lambdaRepository;

    private RedisConsumerGroupInitializer initializer;

    @BeforeEach
    void setUp() {
        // We removed StreamKeyResolver from here since you are using static methods!
        initializer = new RedisConsumerGroupInitializer(redisTemplate, lambdaRepository);
    }

    @Test
    void testCreateGroupsForLambdaCreatesFourStreams() {
        // 1. Initialize groups for a new lambda
        initializer.initializeGroup("VideoProcessor");

        // 2. Verify all 4 priority streams were created with the "atf-workers" group
        for (Priority priority : Priority.values()) {
            // Using your static method!
            String streamKey = StreamKeyResolver.getStreamKey("VideoProcessor", priority);

            // Fetch the groups for this specific stream from Redis
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);

            // Assert that the group exists and is named correctly
            assertThat(groups.stream()).anyMatch(g -> g.groupName().equals("atf-workers"));
        }
    }

    @Test
    void testCallingInitializeTwiceIsIdempotent() {
        // Call it the first time (creates streams and groups)
        initializer.initializeGroup("ImageResizer");

        // Call it the second time (should catch BUSYGROUP and NOT throw an exception)
        assertDoesNotThrow(() -> {
            initializer.initializeGroup("ImageResizer");
        });

        // Verify it still exists properly
        String streamKey = StreamKeyResolver.getStreamKey("ImageResizer", Priority.CRITICAL);
        StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
        assertThat(groups.stream()).anyMatch(g -> g.groupName().equals("atf-workers"));
    }
}