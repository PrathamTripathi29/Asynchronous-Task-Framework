package com.system_design.ATF.services.worker;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatLoop {
    private final ATFServiceClient atfServiceClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public ScheduledFuture<?> startInternal(UUID taskId, String workerId, long intervalMillis, AtomicBoolean abortFlag){
        int[] consecutiveFailures = {0};
        return scheduler.scheduleAtFixedRate(() -> {
            if(abortFlag.get()){
                throw new RuntimeException("Heartbeat loop stopped internally.");
            }

            try {
                atfServiceClient.heartbeat(taskId, workerId);
                consecutiveFailures[0] = 0;
                log.debug("Heartbeat successful for task {}", taskId);
            } catch (Exception ex){
                consecutiveFailures[0]++;
                log.warn("Heartbeat failed for task {} (failure {}/3): {}", taskId, consecutiveFailures[0], ex.getMessage());
                if(consecutiveFailures[0] >= 3){
                    log.error("Task {} failed 3 consecutive heartbeats. Setting abort flag.", taskId);
                    abortFlag.set(true);

                    throw new RuntimeException("Max heartbeat failures reached.");
                }
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> start(UUID taskId, String workerId, int intervalSeconds, AtomicBoolean abortFlag){
        return startInternal(taskId, workerId, intervalSeconds*1000L, abortFlag);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

}
