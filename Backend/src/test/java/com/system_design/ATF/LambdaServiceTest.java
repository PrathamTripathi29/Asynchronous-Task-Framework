package com.system_design.ATF;

import com.system_design.ATF.dtos.RegisterLambdaRequest;
import com.system_design.ATF.entity.GateAction;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.LambdaStatus;
import com.system_design.ATF.exception.DuplicateLambdaException;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.services.ConsumerGroupInitializer;
import com.system_design.ATF.services.LambdaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LambdaServiceTest {

    @Mock
    private LambdaRepository lambdaRepository;

    @Mock
    private ConsumerGroupInitializer consumerGroupInitializer;

    @InjectMocks
    private LambdaService lambdaService;

    @Test
    void testRegisterNewLambda() {
        RegisterLambdaRequest request = new RegisterLambdaRequest(
                "VideoEncoder", "Encodes video", "http://api", "CoreTeam"
        );

        when(lambdaRepository.existsByName("VideoEncoder")).thenReturn(false);

        // Mock the save method to just return whatever was passed into it
        when(lambdaRepository.save(any(Lambda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lambda result = lambdaService.register(request);

        assertNotNull(result);
        assertEquals("VideoEncoder", result.getName());
        assertEquals(LambdaStatus.ACTIVE, result.getStatus());

        // Verify defaults were set by the Builder
        assertEquals(3, result.getMaxAttempts());
        assertEquals(10, result.getHeartbeatIntervalSeconds());
        assertEquals(60, result.getHeartbeatTimeoutSeconds());

        verify(lambdaRepository, times(1)).save(any(Lambda.class));
        verify(consumerGroupInitializer, times(1)).initializeGroup("VideoEncoder");
    }

    @Test
    void testRegisterDuplicateNameThrowsException() {
        RegisterLambdaRequest request = new RegisterLambdaRequest(
                "DuplicateName", "", "", ""
        );

        when(lambdaRepository.existsByName("DuplicateName")).thenReturn(true);

        assertThrows(DuplicateLambdaException.class, () -> lambdaService.register(request));

        // Prove that the database was never touched
        verify(lambdaRepository, never()).save(any());
    }

    @Test
    void testGateLambdaAppliesDropAction() {
        Lambda existing = Lambda.builder().name("send_email").status(LambdaStatus.ACTIVE).build();

        when(lambdaRepository.findByName("send_email")).thenReturn(Optional.of(existing));
        when(lambdaRepository.save(any(Lambda.class))).thenAnswer(i -> i.getArgument(0));

        Lambda result = lambdaService.gate("send_email", GateAction.DROP);

        assertEquals(LambdaStatus.PAUSED, result.getStatus());
        assertEquals(GateAction.DROP, result.getGateAction());
        verify(lambdaRepository).save(existing);
    }

    @Test
    void testGateNonExistentLambdaThrowsException() {
        when(lambdaRepository.findByName("ghost_lambda")).thenReturn(Optional.empty());

        assertThrows(LambdaNotFoundException.class, () -> lambdaService.gate("ghost_lambda", GateAction.DROP));
    }

    @Test
    void testUngateLambdaRemovesGateAction() {
        Lambda gated = Lambda.builder()
                .name("send_email")
                .status(LambdaStatus.PAUSED)
                .gateAction(GateAction.DROP)
                .build();

        when(lambdaRepository.findByName("send_email")).thenReturn(Optional.of(gated));
        when(lambdaRepository.save(any(Lambda.class))).thenAnswer(i -> i.getArgument(0));

        Lambda result = lambdaService.ungate("send_email");

        assertEquals(LambdaStatus.ACTIVE, result.getStatus());
        assertNull(result.getGateAction());
        verify(lambdaRepository).save(gated);
    }

    @Test
    void testUngateNonExistentLambdaThrowsException() {
        when(lambdaRepository.findByName("ghost_lambda")).thenReturn(Optional.empty());

        assertThrows(LambdaNotFoundException.class, () -> lambdaService.ungate("ghost_lambda"));
    }
}