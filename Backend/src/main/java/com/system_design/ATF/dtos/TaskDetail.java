package com.system_design.ATF.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetail {
    private UUID id;
    private String lambdaName;
    private String collectionName;
    private JsonNode payload;
    private int attemptCount;
    private String callbackUrl;
    private int heartbeatIntervalSeconds;
    private int heartbeatTimeoutSeconds;
}
