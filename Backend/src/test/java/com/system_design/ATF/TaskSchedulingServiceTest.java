package com.system_design.ATF;

import com.fasterxml.jackson.databind.JsonNode;
import com.system_design.ATF.dtos.ScheduleTaskRequest;
import com.system_design.ATF.entity.*;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.repository.CollectionRepository;
import com.system_design.ATF.repository.LambdaRepository;
import com.system_design.ATF.repository.TaskRepository;
import com.system_design.ATF.services.TaskSchedulingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskSchedulingServiceTest {

    @Mock
    private LambdaRepository lambdaRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private JsonNode mockPayload;

    @InjectMocks
    private TaskSchedulingService schedulingService;

    @Test
    void testScheduleValidActiveLambda() {
        OffsetDateTime scheduledTime = OffsetDateTime.now().plusSeconds(60);
        ScheduleTaskRequest request = ScheduleTaskRequest.builder()
                .lambdaName("EmailLambda")
                .payload(mockPayload)
                .scheduledAt(scheduledTime)
                .build();

        Lambda activeLambda = Lambda.builder().name("EmailLambda").status(LambdaStatus.ACTIVE).build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(activeLambda));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task result = schedulingService.schedule(request);

        assertEquals(TaskStatus.NEW, result.getStatus());
        assertEquals(scheduledTime, result.getNextTriggerAt());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void testScheduleWithNoScheduledAtDefaultsToNow() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().lambdaName("EmailLambda").build();
        Lambda activeLambda = Lambda.builder().name("EmailLambda").status(LambdaStatus.ACTIVE).build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(activeLambda));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task result = schedulingService.schedule(request);

        assertNotNull(result.getNextTriggerAt());

        // Refactored to provide explicit error messages with the exact numerical difference
        long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(OffsetDateTime.now(), result.getNextTriggerAt()));
        assertTrue(diffSeconds <= 5,
                "nextTriggerAt should be approx now, but difference was " + diffSeconds + " seconds.");
    }

    @Test
    void testScheduleGatedDropLambda() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().lambdaName("EmailLambda").build();
        Lambda droppedLambda = Lambda.builder().name("EmailLambda").gateAction(GateAction.DROP).build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(droppedLambda));

        Task result = schedulingService.schedule(request);

        assertEquals(TaskStatus.DROPPED, result.getStatus());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void testScheduleGatedPauseLambda() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().lambdaName("EmailLambda").build();
        Lambda pausedLambda = Lambda.builder().name("EmailLambda").gateAction(GateAction.PAUSE).build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(pausedLambda));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task result = schedulingService.schedule(request);

        assertEquals(TaskStatus.NEW, result.getStatus());

        // Refactored to calculate raw days. This bypasses OffsetDateTime drift issues.
        long daysInFuture = ChronoUnit.DAYS.between(OffsetDateTime.now(), result.getNextTriggerAt());

        assertTrue(daysInFuture >= 36490 && daysInFuture <= 36510,
                "Task should be paused ~36500 days in future, but was exactly " + daysInFuture + " days in future.");

        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void testScheduleNonExistentLambda() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder().lambdaName("GhostLambda").build();
        when(lambdaRepository.findByName("GhostLambda")).thenReturn(Optional.empty());

        assertThrows(LambdaNotFoundException.class, () -> schedulingService.schedule(request));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testScheduleWithCriticalPriority() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder()
                .lambdaName("EmailLambda")
                .priority(Priority.CRITICAL)
                .build();
        Lambda activeLambda = Lambda.builder().name("EmailLambda").build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(activeLambda));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task result = schedulingService.schedule(request);

        assertEquals(Priority.CRITICAL, result.getPriority());
    }

    @Test
    void testScheduleCollectionGatedDrop() {
        ScheduleTaskRequest request = ScheduleTaskRequest.builder()
                .lambdaName("EmailLambda")
                .collectionName("SpamList")
                .build();

        Lambda activeLambda = Lambda.builder().name("EmailLambda").build();

        Collection dropCollection = Collection.builder()
                .lambdaName("EmailLambda")
                .name("SpamList")
                .gateAction(GateAction.DROP)
                .build();

        when(lambdaRepository.findByName("EmailLambda")).thenReturn(Optional.of(activeLambda));
        when(collectionRepository.findByLambdaNameAndName("EmailLambda", "SpamList")).thenReturn(Optional.of(dropCollection));

        Task result = schedulingService.schedule(request);

        assertEquals(TaskStatus.DROPPED, result.getStatus());
        verify(taskRepository, never()).save(any(Task.class));
    }
}