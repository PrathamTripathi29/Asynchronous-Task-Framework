package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.system_design.ATF.dtos.ExecutionOutcome;
import com.system_design.ATF.dtos.TaskDetail;
import com.system_design.ATF.entity.Outcome;
import com.system_design.ATF.services.worker.HTTPCallbackExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpCallbackExecutorTest {

    private static WireMockServer wireMockServer;
    private HTTPCallbackExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
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

        // 1. Force tight 500ms timeouts directly onto the factory to ensure they are respected
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(500);

        // 2. Build the RestTemplate using this factory
        RestTemplateBuilder builder = new RestTemplateBuilder().requestFactory(() -> factory);

        // 3. Inject it into our component
        executor = new HTTPCallbackExecutor(builder, 500, 500);
    }

    private TaskDetail createMockTask(String path) {
        ObjectNode payload = objectMapper.createObjectNode().put("data", "hello");
        return TaskDetail.builder()
                .id(UUID.randomUUID())
                .lambdaName("test_lambda")
                .attemptCount(3)
                .callbackUrl("http://localhost:" + wireMockServer.port() + path)
                .payload(payload)
                .build();
    }

    @Test
    void testExecutionSuccessReturns200WithResultAndHeaders() {
        TaskDetail task = createMockTask("/success-200");

        stubFor(post(urlEqualTo("/success-200"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"done\"}")));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.SUCCESS, outcome.getOutcome());
        assertEquals(200, outcome.getHttpStatusCode());
        assertEquals("done", outcome.getResult().get("status").asText());

        verify(1, postRequestedFor(urlEqualTo("/success-200"))
                .withHeader("X-ATF-Task-Id", equalTo(task.getId().toString()))
                .withHeader("X-ATF-Lambda", equalTo("test_lambda"))
                .withHeader("X-ATF-Attempt", equalTo("3"))
                .withRequestBody(matchingJsonPath("$.data", equalTo("hello"))));
    }

    @Test
    void testExecutionSuccessReturns201() {
        TaskDetail task = createMockTask("/success-201");

        stubFor(post(urlEqualTo("/success-201"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.SUCCESS, outcome.getOutcome());
        assertEquals(201, outcome.getHttpStatusCode());
    }

    @Test
    void testExecutionClientErrorReturnsFatalFailure400() {
        TaskDetail task = createMockTask("/error-400");

        stubFor(post(urlEqualTo("/error-400")).willReturn(aResponse().withStatus(400).withBody("Bad Data")));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.FATAL_FAILURE, outcome.getOutcome());
        assertEquals(400, outcome.getHttpStatusCode());
        assertTrue(outcome.getErrorMessage().contains("400"));
    }

    @Test
    void testExecutionClientErrorReturnsFatalFailure404() {
        TaskDetail task = createMockTask("/error-404");

        stubFor(post(urlEqualTo("/error-404")).willReturn(aResponse().withStatus(404)));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.FATAL_FAILURE, outcome.getOutcome());
        assertEquals(404, outcome.getHttpStatusCode());
        assertTrue(outcome.getErrorMessage().contains("404"));
    }

    @Test
    void testExecutionServerErrorReturnsRetriableFailure500() {
        TaskDetail task = createMockTask("/error-500");

        stubFor(post(urlEqualTo("/error-500")).willReturn(aResponse().withStatus(500)));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.RETRIABLE_FAILURE, outcome.getOutcome());
        assertEquals(500, outcome.getHttpStatusCode());
        assertTrue(outcome.getErrorMessage().contains("500"));
    }

    @Test
    void testExecutionServerErrorReturnsRetriableFailure503() {
        TaskDetail task = createMockTask("/error-503");

        stubFor(post(urlEqualTo("/error-503")).willReturn(aResponse().withStatus(503)));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.RETRIABLE_FAILURE, outcome.getOutcome());
        assertEquals(503, outcome.getHttpStatusCode());
        assertTrue(outcome.getErrorMessage().contains("503"));
    }

    @Test
    void testExecutionTimeoutReturnsRetriableFailure() {
        TaskDetail task = createMockTask("/timeout");

        // Delay the WireMock response by 1.5 seconds (exceeding our aggressive 500ms config timeout)
        stubFor(post(urlEqualTo("/timeout"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.RETRIABLE_FAILURE, outcome.getOutcome());
        assertTrue(outcome.getErrorMessage().toLowerCase().contains("timeout") || outcome.getErrorMessage().toLowerCase().contains("read timed out"));
    }

    @Test
    void testExecutionConnectionRefusedReturnsRetriableFailure() {
        TaskDetail task = createMockTask("");

        // Point to a completely dead/unused port to trigger a connection refused
        task.setCallbackUrl("http://localhost:1");

        ExecutionOutcome outcome = executor.execute(task);

        assertEquals(Outcome.RETRIABLE_FAILURE, outcome.getOutcome());
        assertTrue(outcome.getErrorMessage().toLowerCase().contains("connection refused"));
    }
}