package com.system_design.ATF;

import com.system_design.ATF.services.worker.ATFServiceClient;
import com.system_design.ATF.services.worker.HeartbeatLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HeartbeatLoopTest {

    @Mock
    private ATFServiceClient atfClient;

    private HeartbeatLoop heartbeatLoop;

    @BeforeEach
    void setup() {
        heartbeatLoop = new HeartbeatLoop(atfClient);
    }

    @AfterEach
    void teardown() {
        heartbeatLoop.shutdown();
    }

    @Test
    void testHeartbeatAlwaysSucceeds() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        AtomicBoolean abortFlag = new AtomicBoolean(false);

        ScheduledFuture<?> future = heartbeatLoop.startInternal(taskId, "worker-1", 100L, abortFlag);

        // Sleep for 450ms. With a 100ms interval (and an initial delay of 0),
        // it should comfortably execute at 0ms, 100ms, 200ms, 300ms, and 400ms.
        Thread.sleep(450);
        future.cancel(true);

        assertFalse(abortFlag.get());
        verify(atfClient, atLeast(4)).heartbeat(taskId, "worker-1");
    }

    @Test
    void testHeartbeatFailsThreeTimesSetsAbortFlag() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        AtomicBoolean abortFlag = new AtomicBoolean(false);

        // Mock the client to ALWAYS throw an exception
        doThrow(new RuntimeException("Network Error")).when(atfClient).heartbeat(taskId, "worker-1");

        ScheduledFuture<?> future = heartbeatLoop.startInternal(taskId, "worker-1", 100L, abortFlag);

        // 0ms (fail 1), 100ms (fail 2), 200ms (fail 3 -> trigger abort flag)
        Thread.sleep(300);

        assertTrue(abortFlag.get());

        // Clean up the future so it's formally used
        future.cancel(true);

        // Even if we wait longer, it should have stopped itself after the 3rd execution
        verify(atfClient, atMost(4)).heartbeat(taskId, "worker-1");
    }

    @Test
    void testHeartbeatFailsTwiceThenSucceedsResetsCount() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        AtomicBoolean abortFlag = new AtomicBoolean(false);

        // Fail 1st and 2nd time, succeed 3rd time, fail 4th and 5th...
        doThrow(new RuntimeException("Error 1"))
                .doThrow(new RuntimeException("Error 2"))
                .doNothing() // Success! Resets count back to 0
                .doThrow(new RuntimeException("Error 4"))
                .doThrow(new RuntimeException("Error 5"))
                .doNothing() // FIX: Add this so executions 6+ succeed instead of repeating Error 5!
                .when(atfClient).heartbeat(taskId, "worker-1");

        ScheduledFuture<?> future = heartbeatLoop.startInternal(taskId, "worker-1", 100L, abortFlag);

        // Give it enough time to run ~6 times
        Thread.sleep(600);
        future.cancel(true);

        // Because the 3rd attempt succeeded, the failure count never hit 3 consecutively!
        assertFalse(abortFlag.get(), "Abort flag should remain false because failures were reset");
        verify(atfClient, atLeast(5)).heartbeat(taskId, "worker-1");
    }

    @Test
    void testCancelStopsHeartbeats() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        AtomicBoolean abortFlag = new AtomicBoolean(false);

        ScheduledFuture<?> future = heartbeatLoop.startInternal(taskId, "worker-1", 100L, abortFlag);

        // Let it fire at 0 and 100
        Thread.sleep(150);
        future.cancel(false); // Manually cancel the scheduled loop

        // Record exactly how many times it was called before we canceled
        int invocationsAtCancel = mockingDetails(atfClient).getInvocations().size();

        // Wait a long time...
        Thread.sleep(300);

        // Verify it didn't fire any more times after being canceled
        verify(atfClient, atMost(invocationsAtCancel + 1)).heartbeat(taskId, "worker-1");
    }
}