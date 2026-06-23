package com.system_design.ATF;

import com.system_design.ATF.entity.GateAction;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.LambdaStatus;
import com.system_design.ATF.repository.LambdaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class LambdaRepositoryTest {

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
        registry.add("spring.sql.init.mode", () -> "never"); // Prevent double schema execution
    }

    @Autowired
    private LambdaRepository lambdaRepository;

    private Lambda createBasicLambda(String name) {
        return Lambda.builder()
                .name(name)
                .description("Test lambda for " + name)
                .callbackUrl("https://api.example.com/" + name)
                .ownerTeam("CoreTech")
                .status(LambdaStatus.ACTIVE)
                .gateAction(GateAction.DROP)
                .build();
    }

    @BeforeEach
    void setUp() {
        // Start with a clean slate for every test
        lambdaRepository.deleteAll();
    }

    // TEST 1: Save a lambda, findByName returns it
    @Test
    void testSaveAndFindByName() {
        Lambda saved = lambdaRepository.save(createBasicLambda("VideoProcessor"));

        Optional<Lambda> found = lambdaRepository.findByName("VideoProcessor");

        assertTrue(found.isPresent(), "Lambda should be found by its name");
        assertEquals(saved.getId(), found.get().getId(), "IDs should match exactly");
        assertEquals("https://api.example.com/VideoProcessor", found.get().getCallbackUrl());
    }

    // TEST 2: findByName with non-existent name returns Optional.empty()
    @Test
    void testFindByNameNonExistent() {
        Optional<Lambda> found = lambdaRepository.findByName("GhostLambda");
        assertTrue(found.isEmpty(), "Should safely return Optional.empty() for missing records");
    }

    // TEST 3: existsByName returns true for existing, false for non-existing
    @Test
    void testExistsByName() {
        lambdaRepository.save(createBasicLambda("ImageProcessor"));

        assertTrue(lambdaRepository.existsByName("ImageProcessor"), "Should return true for existing Lambda");
        assertFalse(lambdaRepository.existsByName("AudioProcessor"), "Should return false for non-existing Lambda");
    }

    // TEST 4: Save two lambdas, findAll() returns both
    @Test
    void testFindAll() {
        lambdaRepository.save(createBasicLambda("LambdaOne"));
        lambdaRepository.save(createBasicLambda("LambdaTwo"));

        List<Lambda> allLambdas = lambdaRepository.findAll();

        assertEquals(2, allLambdas.size(), "Should return exactly the 2 saved Lambdas");

        // Quick verification they are the right ones
        boolean hasOne = allLambdas.stream().anyMatch(l -> l.getName().equals("LambdaOne"));
        boolean hasTwo = allLambdas.stream().anyMatch(l -> l.getName().equals("LambdaTwo"));
        assertTrue(hasOne && hasTwo);
    }
}