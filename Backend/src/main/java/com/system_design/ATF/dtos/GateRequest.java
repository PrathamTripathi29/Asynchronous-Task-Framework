package com.system_design.ATF.dtos;

import com.system_design.ATF.entity.GateAction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GateRequest {
    @NotNull(message = "Gate action is required")
    private GateAction gateAction;
}
