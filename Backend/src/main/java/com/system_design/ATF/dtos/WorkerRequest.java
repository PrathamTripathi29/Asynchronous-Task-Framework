package com.system_design.ATF.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerRequest {
    @NotBlank(message = "Worker ID is required")
    private String workerId;
}
