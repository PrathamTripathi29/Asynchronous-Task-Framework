package com.system_design.ATF.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.system_design.ATF.entity.Outcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionOutcome {
    private Outcome outcome;
    private JsonNode result;
    private String errorMessage;
    private Integer httpStatusCode;
}
