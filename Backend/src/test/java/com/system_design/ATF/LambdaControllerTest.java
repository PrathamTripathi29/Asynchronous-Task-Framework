package com.system_design.ATF;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_design.ATF.controllers.LambdaController;
import com.system_design.ATF.dtos.GateRequest;
import com.system_design.ATF.dtos.RegisterLambdaRequest;
import com.system_design.ATF.entity.GateAction;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.exception.DuplicateLambdaException;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.services.LambdaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LambdaController.class)
public class LambdaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // This completely fakes the Service so we don't need a Database!
    @MockitoBean
    private LambdaService lambdaService;

    // TEST 1: POST /api/v1/lambdas with valid body -> 201
    @Test
    void testRegisterValidLambda() throws Exception {
        RegisterLambdaRequest request = new RegisterLambdaRequest("TestLambda", "desc", "url", "team");
        Lambda fakeResponse = Lambda.builder().id(UUID.randomUUID()).name("TestLambda").build();

        when(lambdaService.register(any())).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/lambdas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // 201
                .andExpect(jsonPath("$.name").value("TestLambda"))
                .andExpect(jsonPath("$.id").exists());
    }

    // TEST 2: POST with missing name field -> 400 (Bean Validation)
    @Test
    void testRegisterMissingName() throws Exception {
        // Name is blank, which violates @NotBlank
        RegisterLambdaRequest request = new RegisterLambdaRequest("", "desc", "url", "team");

        mockMvc.perform(post("/api/v1/lambdas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // 400
    }

    // TEST 3: POST when service throws DuplicateLambdaException -> 409
    @Test
    void testRegisterDuplicateLambda() throws Exception {
        RegisterLambdaRequest request = new RegisterLambdaRequest("ExistingLambda", "desc", "url", "team");

        when(lambdaService.register(any())).thenThrow(new DuplicateLambdaException("Exists"));

        mockMvc.perform(post("/api/v1/lambdas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()); // 409
    }

    // TEST 4: GET /nonexistent when service throws LambdaNotFoundException -> 404
    @Test
    void testGetNonExistentLambda() throws Exception {
        when(lambdaService.get("ghost")).thenThrow(new LambdaNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/lambdas/ghost"))
                .andExpect(status().isNotFound()); // 404
    }

    // TEST 5: POST /gate with body {"action": "DROP"} -> 200
    @Test
    void testGateLambdaValid() throws Exception {
        GateRequest request = new GateRequest(GateAction.DROP);
        Lambda fakeResponse = Lambda.builder().name("TestLambda").gateAction(GateAction.DROP).build();

        when(lambdaService.gate("TestLambda", GateAction.DROP)).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/lambdas/TestLambda/gate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.gateAction").value("DROP"));
    }

    // TEST 6: POST /gate with invalid action -> 400
    @Test
    void testGateLambdaInvalidAction() throws Exception {
        // Send a JSON string with an invalid Enum value
        String invalidJson = "{\"action\": \"EXPLODE\"}";

        mockMvc.perform(post("/api/v1/lambdas/TestLambda/gate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest()); // 400 because Jackson can't parse "EXPLODE" into a GateAction enum
    }
}