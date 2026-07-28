package com.system_design.ATF.services.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.dtos.ExecutionOutcome;
import com.system_design.ATF.dtos.TaskDetail;
import com.system_design.ATF.entity.Outcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Component
public class HTTPCallbackExecutor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HTTPCallbackExecutor(
            RestTemplateBuilder builder,
            @Value("${atf.callback.connect-timeout:2000}") int connectTimeout,
            @Value("${atf.callback.read-timeout:30000}") int readTimeout) {

        this.restTemplate = builder
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .readTimeout(Duration.ofMillis(readTimeout))
                .build();
    }

    public ExecutionOutcome execute(TaskDetail task) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-ATF-Task-Id", task.getId().toString());
        headers.set("X-ATF-Lambda", task.getLambdaName());
        headers.set("X-ATF-Attempt", String.valueOf(task.getAttemptCount()));

        try {
            // 1. Manually serialize payload to String to avoid Jackson 2 vs 3 conflicts
            String payloadStr = task.getPayload() != null ? objectMapper.writeValueAsString(task.getPayload()) : "{}";
            HttpEntity<String> request = new HttpEntity<>(payloadStr, headers);

            // 2. Ask for a plain String response
            ResponseEntity<String> response = restTemplate.postForEntity(
                    task.getCallbackUrl(),
                    request,
                    String.class
            );

            // 3. Manually parse the JSON string back into JsonNode
            JsonNode resultNode = null;
            if (response.getBody() != null && !response.getBody().trim().isEmpty()) {
                resultNode = objectMapper.readTree(response.getBody());
            }

            return ExecutionOutcome.builder()
                    .outcome(Outcome.SUCCESS)
                    .result(resultNode)
                    .httpStatusCode(response.getStatusCode().value())
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("Client error executing task {}: {}", task.getId(), e.getStatusCode());
            return ExecutionOutcome.builder()
                    .outcome(Outcome.FATAL_FAILURE)
                    .errorMessage("Client error: " + e.getStatusCode())
                    .httpStatusCode(e.getStatusCode().value())
                    .build();

        } catch (HttpServerErrorException e) {
            log.error("Server error executing task {}: {}", task.getId(), e.getStatusCode());
            return ExecutionOutcome.builder()
                    .outcome(Outcome.RETRIABLE_FAILURE)
                    .errorMessage("Server error: " + e.getStatusCode())
                    .httpStatusCode(e.getStatusCode().value())
                    .build();

        } catch (ResourceAccessException e) {
            log.error("Network timeout or connection refused executing task {}: {}", task.getId(), e.getMessage());
            return ExecutionOutcome.builder()
                    .outcome(Outcome.RETRIABLE_FAILURE)
                    .errorMessage("Network error or timeout: " + e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error executing task {}: {}", task.getId(), e.getMessage(), e);
            return ExecutionOutcome.builder()
                    .outcome(Outcome.FATAL_FAILURE)
                    .errorMessage("Unexpected internal error: " + e.getMessage())
                    .build();
        }
    }
}