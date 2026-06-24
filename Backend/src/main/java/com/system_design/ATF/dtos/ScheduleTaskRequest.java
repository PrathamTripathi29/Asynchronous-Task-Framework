package com.system_design.ATF.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.system_design.ATF.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTaskRequest {
    @NotBlank(message = "Lambda name is required")
    private String lambdaName;
    private String collectionName;
    private Priority priority;
    private JsonNode payload;
    private OffsetDateTime scheduledAt;
}
