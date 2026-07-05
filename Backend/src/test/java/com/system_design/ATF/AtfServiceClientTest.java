package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.system_design.ATF.dtos.ClaimResponse;
import com.system_design.ATF.dtos.TaskDetail;
import com.system_design.ATF.entity.Outcome;
import com.system_design.ATF.exception.TaskNotFoundException;
import com.system_design.ATF.services.worker.ATFServiceClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class AtfServiceClientTest {

    private static WireMockServer wireMockServer;
    private ATFServiceClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        // Spins up a real HTTP server on a random available port
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void teardown() {
        wireMockServer.stop();
    }

    @BeforeEach
    void init() {
        wireMockServer.resetAll();
        // Point our client directly to the WireMock port!
        String baseUrl = "http://localhost:" + wireMockServer.port();

        // CRITICAL FIX: Force HTTP/1.1 using SimpleClientHttpRequestFactory.
        // Newer Spring versions + Java 25 default to HTTP/2, which causes
        // RST_STREAM connection crashes with WireMock 2.x!
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .requestFactory(() -> new SimpleClientHttpRequestFactory());

        client = new ATFServiceClient(builder, baseUrl);
    }

    @Test
    void testGetTaskSuccess() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskDetail mockDetail = TaskDetail.builder()
                .id(taskId)
                .lambdaName("test_lambda")
                .callbackUrl("http://hook")
                .build();

        // Stub out the 200 OK Response
        stubFor(get(urlEqualTo("/internal/v1/tasks/" + taskId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(mockDetail))));

        TaskDetail result = client.getTask(taskId);

        assertNotNull(result);
        assertEquals(taskId, result.getId());
        assertEquals("test_lambda", result.getLambdaName());
    }

    @Test
    void testGetTaskNotFoundThrowsException() {
        UUID taskId = UUID.randomUUID();

        // Stub out the 404 Response
        stubFor(get(urlEqualTo("/internal/v1/tasks/" + taskId))
                .willReturn(aResponse().withStatus(404)));

        assertThrows(TaskNotFoundException.class, () -> client.getTask(taskId));
    }

    @Test
    void testClaimTaskSuccess() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskDetail mockDetail = TaskDetail.builder().id(taskId).build();

        // Let the stub match just the URL to prevent mysterious 404 mapping errors
        stubFor(post(urlEqualTo("/internal/v1/tasks/" + taskId + "/claim"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(mockDetail))));

        ClaimResponse response = client.claimTask(taskId, "worker-1");

        assertTrue(response.isSuccess());
        assertNotNull(response.getTaskDetail());
        assertEquals(taskId, response.getTaskDetail().getId());

        // Verify the payload explicitly afterward to get better error diffs
        verify(1, postRequestedFor(urlEqualTo("/internal/v1/tasks/" + taskId + "/claim"))
                .withRequestBody(matchingJsonPath("$.workerId", equalTo("worker-1"))));
    }

    @Test
    void testClaimTaskConflict() {
        UUID taskId = UUID.randomUUID();

        // 409 Conflict translates into a ClaimResponse containing isSuccess() = false
        stubFor(post(urlEqualTo("/internal/v1/tasks/" + taskId + "/claim"))
                .willReturn(aResponse().withStatus(409)));

        ClaimResponse response = client.claimTask(taskId, "worker-1");

        assertFalse(response.isSuccess());
        assertNull(response.getTaskDetail());

        verify(1, postRequestedFor(urlEqualTo("/internal/v1/tasks/" + taskId + "/claim"))
                .withRequestBody(matchingJsonPath("$.workerId", equalTo("worker-1"))));
    }

    @Test
    void testHeartbeatSuccess() {
        UUID taskId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/internal/v1/tasks/" + taskId + "/heartbeat"))
                .willReturn(aResponse().withStatus(204)));

        assertDoesNotThrow(() -> client.heartbeat(taskId, "worker-1"));

        verify(1, postRequestedFor(urlEqualTo("/internal/v1/tasks/" + taskId + "/heartbeat"))
                .withRequestBody(matchingJsonPath("$.workerId", equalTo("worker-1"))));
    }

    @Test
    void testHeartbeatServerErrorThrowsException() {
        UUID taskId = UUID.randomUUID();

        // Server outages (503) should not be silently swallowed; the caller must handle retry logic
        stubFor(post(urlEqualTo("/internal/v1/tasks/" + taskId + "/heartbeat"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(HttpServerErrorException.class, () -> client.heartbeat(taskId, "worker-1"));
    }

    @Test
    void testSetResultSuccess() throws Exception {
        UUID taskId = UUID.randomUUID();

        // Broaden the stub to catch the route regardless of the payload
        stubFor(post(urlEqualTo("/internal/v1/tasks/" + taskId + "/result"))
                .willReturn(aResponse().withStatus(204)));

        assertDoesNotThrow(() -> client.setResult(taskId, "worker-1", Outcome.SUCCESS));

        // Verify the client explicitly transmitted the correct JSON mapping
        verify(1, postRequestedFor(urlEqualTo("/internal/v1/tasks/" + taskId + "/result"))
                .withRequestBody(matchingJsonPath("$.outcome", equalTo("SUCCESS")))
                .withRequestBody(matchingJsonPath("$.workerId", equalTo("worker-1"))));
    }
}