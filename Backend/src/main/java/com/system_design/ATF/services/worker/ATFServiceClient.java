package com.system_design.ATF.services.worker;

import com.system_design.ATF.dtos.ClaimResponse;
import com.system_design.ATF.dtos.SetResultRequest;
import com.system_design.ATF.dtos.TaskDetail;
import com.system_design.ATF.dtos.WorkerRequest;
import com.system_design.ATF.entity.Outcome;
import com.system_design.ATF.exception.TaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.restclient.RestTemplateBuilder;

import java.util.UUID;

@Slf4j
@Service
public class ATFServiceClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ATFServiceClient(RestTemplateBuilder restTemplateBuilder, @Value("{atf.service.url:http://localhost:8080}") String baseUrl){
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
    }

    public TaskDetail getTask(UUID taskId){
        try {
            return restTemplate.getForObject(baseUrl + "/internal/v1/tasks/" + taskId, TaskDetail.class);
        } catch (HttpClientErrorException.NotFound e){
            throw new TaskNotFoundException("Task " + taskId + " not found.");
        }
    }

    public ClaimResponse claimTask(UUID taskId, String workerId){
        WorkerRequest workerRequest = new WorkerRequest(workerId);
        try {
            ResponseEntity<TaskDetail> response = restTemplate.postForEntity(
                    baseUrl + "/internal/v1/tasks/" + taskId + "/claim",
                    workerRequest,
                    TaskDetail.class
            );
            return new ClaimResponse(true, response.getBody());
        } catch (HttpClientErrorException.Conflict e){
            log.debug("Task {} was already claimed by another worker.", taskId);
            return new ClaimResponse(false, null);
        } catch (HttpClientErrorException.NotFound e){
            throw new TaskNotFoundException("Task " + taskId + " not found.");
        }
    }

    public void heartbeat(UUID taskId, String workerId){
        WorkerRequest workerRequest = new WorkerRequest(workerId);
        restTemplate.postForEntity(
                baseUrl + "/internal/v1/tasks/" + taskId + "/heartbeat",
                workerRequest,
                Void.class
        );
    }

    public void setResult(UUID taskId, String workerId, Outcome outcome){
        SetResultRequest request = SetResultRequest.builder()
                .workerId(workerId)
                .outcome(outcome)
                .build();

        restTemplate.postForEntity(
                baseUrl + "/internal/v1/tasks/" + taskId + "/result",
                request,
                Void.class
        );
    }
}
