package com.system_design.ATF.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponse {
    private boolean success;
    private TaskDetail taskDetail;
}
