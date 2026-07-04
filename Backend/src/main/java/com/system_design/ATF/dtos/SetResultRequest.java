package com.system_design.ATF.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.system_design.ATF.entity.Outcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetResultRequest {

    @NotBlank(message = "Wordker ID is required")
    private String workerId;

    @NotNull(message = "Outcome is required")
    private Outcome outcome;

    private JsonNode result;

    private String errorMessage;

    private Integer httpStatusCode;
}
