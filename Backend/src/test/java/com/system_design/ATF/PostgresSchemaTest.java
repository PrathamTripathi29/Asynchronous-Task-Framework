package com.system_design.ATF;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class PostgresSchemaTest {

    // THE FIX: Removed the <?> and <> because Testcontainers 2.0 dropped generics
    @Container
    public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("atf_db")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testTablesExist() throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String[] tablesToCheck = {"lambdas", "collections", "tasks"};

        for (String tableName : tablesToCheck) {
            ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"});
            assertTrue(tables.next(), "Table '" + tableName + "' should exist in the database schema.");
        }
    }

    @Test
    void testTasksIdIsUuidWithDefault() throws SQLException {
        connection.createStatement().execute(
                "INSERT INTO lambdas (name, callback_url) VALUES ('VideoProcessor', 'http://localhost/callback')"
        );

        PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO tasks (lambda_name, status, next_trigger_at) VALUES ('VideoProcessor', 'NEW', NOW()) RETURNING id"
        );
        ResultSet rs = stmt.executeQuery();

        assertTrue(rs.next());
        Object idVal = rs.getObject("id");
        assertNotNull(idVal);

        // THE FIX: Updated to JUnit 5's modern assertInstanceOf
        assertInstanceOf(UUID.class, idVal, "The generated ID must be of type java.util.UUID");
    }

    @Test
    void testTasksStatusNotNullConstraint() throws SQLException {
        connection.createStatement().execute(
                "INSERT INTO lambdas (name, callback_url) VALUES ('EmailSender', 'http://localhost/callback')"
        );

        // THE FIX: Converted block lambda {} into a clean expression lambda
        SQLException exception = assertThrows(SQLException.class, () ->
                connection.createStatement().execute(
                        "INSERT INTO tasks (lambda_name, status, next_trigger_at) VALUES ('EmailSender', NULL, NOW())"
                )
        );

        assertTrue(exception.getMessage().contains("null value in column \"status\""));
    }

    @Test
    void testTasksLambdaNameForeignKeyViolation() {
        SQLException exception = assertThrows(SQLException.class, () ->
                connection.createStatement().execute(
                        "INSERT INTO tasks (lambda_name, status, next_trigger_at) VALUES ('FakeLambda', 'NEW', NOW())"
                )
        );

        assertTrue(exception.getMessage().contains("violates foreign key constraint"));
    }

    @Test
    void testTasksNextTriggerAtIndexExists() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'tasks' AND indexname = 'idx_tasks_store_consumer'"
        );
        assertTrue(rs.next(), "The index 'idx_tasks_store_consumer' must be explicitly defined on table 'tasks'.");
    }

    @Test
    void testLambdasUniqueNameConstraint() throws SQLException {
        connection.createStatement().execute(
                "INSERT INTO lambdas (name, callback_url) VALUES ('DuplicateMe', 'http://localhost/1')"
        );

        SQLException exception = assertThrows(SQLException.class, () ->
                connection.createStatement().execute(
                        "INSERT INTO lambdas (name, callback_url) VALUES ('DuplicateMe', 'http://localhost/2')"
                )
        );

        assertTrue(exception.getMessage().contains("duplicate key value violates unique constraint"));
    }

    @Test
    void testCollectionsUniqueLambdaAndNameConstraint() throws SQLException {
        connection.createStatement().execute(
                "INSERT INTO lambdas (name, callback_url) VALUES ('BatchLambda', 'http://localhost/callback')"
        );

        connection.createStatement().execute(
                "INSERT INTO collections (lambda_name, name) VALUES ('BatchLambda', 'DailyReportBatch')"
        );

        SQLException exception = assertThrows(SQLException.class, () ->
                connection.createStatement().execute(
                        "INSERT INTO collections (lambda_name, name) VALUES ('BatchLambda', 'DailyReportBatch')"
                )
        );

        assertTrue(exception.getMessage().contains("duplicate key value violates unique constraint"));
    }
}