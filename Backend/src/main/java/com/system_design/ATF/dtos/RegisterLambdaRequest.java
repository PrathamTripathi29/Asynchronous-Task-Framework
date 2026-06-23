package com.system_design.ATF.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterLambdaRequest {

    @NotBlank(message = "Lambda name is required")
    private String name;
    private String description;
    private String callbackUrl;
    private String ownerTeam;
}
