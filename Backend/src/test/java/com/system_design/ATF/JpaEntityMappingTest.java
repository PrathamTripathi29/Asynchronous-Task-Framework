package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class JpaEntityMappingTest {

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
    private LambdaRepository lambdaRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper mapper = new ObjectMapper();

    // ==========================================
    // LAMBDA TESTS
    // ==========================================

    @Test
    void testLambdaRoundTripAndEnums() {
        Lambda lambda = Lambda.builder()
                .name("TestLambda")
                .callbackUrl("https://api.example.com/webhook")
                .description("Processes test data")
                .ownerTeam("CoreTech")
                .status(LambdaStatus.PAUSED)
                .gateAction(GateAction.DROP)
                .enqueueTimeoutSeconds(120)
                .build();

        Lambda saved = lambdaRepository.saveAndFlush(lambda);
        entityManager.clear(); // Force detach to ensure DB round-trip

        Lambda loaded = lambdaRepository.findById(saved.getId()).orElseThrow();

        // Verify all fields and Enums mapped correctly
        assertEquals("TestLambda", loaded.getName());
        assertEquals("https://api.example.com/webhook", loaded.getCallbackUrl());
        assertEquals(LambdaStatus.PAUSED, loaded.getStatus());
        assertEquals(GateAction.DROP, loaded.getGateAction());
    }

    @Test
    void testLambdaWithoutNameThrowsViolation() {
        Lambda invalidLambda = Lambda.builder()
                .callbackUrl("https://api.example.com")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            lambdaRepository.saveAndFlush(invalidLambda);
        });
    }

    // ==========================================
    // TASK TESTS
    // ==========================================

    @Test
    void testTaskRoundTripAndJsonb() {
        // Create prerequisite Lambda
        Lambda lambda = Lambda.builder()
                .name("VideoLambda")
                .callbackUrl("http://localhost")
                .build();
        lambdaRepository.saveAndFlush(lambda);

        // Build Nested JSON Payload
        ObjectNode payload = mapper.createObjectNode();
        payload.put("videoId", "vid_123");
        payload.putArray("tags").add("hd").add("premium");

        // Specific timezone offset (+03:00) to test persistence
        OffsetDateTime triggerTime = OffsetDateTime.now(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.MICROS);

        Task task = Task.builder()
                .lambdaName("VideoLambda")
                .collectionName(null) // explicitly null
                .priority(Priority.CRITICAL)
                .status(TaskStatus.ENQUEUED)
                .payload(payload)
                .nextTriggerAt(triggerTime)
                .scheduledAt(OffsetDateTime.now())
                .build();

        Task saved = taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task loaded = taskRepository.findById(saved.getId()).orElseThrow();

        // 1. JSONB surviving round-trip
        assertNotNull(loaded.getPayload());
        assertEquals("vid_123", loaded.getPayload().get("videoId").asText());
        assertEquals("premium", loaded.getPayload().get("tags").get(1).asText());

        // 2. Result is null by default
        assertNull(loaded.getResult());

        // 3. Enum strings surviving
        assertEquals(Priority.CRITICAL, loaded.getPriority());

        // 4. Null collections stay null
        assertNull(loaded.getCollectionName());

        // 5. Timezone offset preservation
        assertEquals(triggerTime.toInstant(), loaded.getNextTriggerAt().toInstant());
    }
}