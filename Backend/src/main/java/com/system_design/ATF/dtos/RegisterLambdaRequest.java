package com.system_design.ATF.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterLambdaRequest {
    private String name;
    private String description;
    private String callbackUrl;
    private String ownerTeam;
}
