package com.system_design.ATF.services;

import com.system_design.ATF.components.StreamKeyResolver;
import com.system_design.ATF.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    @Value("${atf.streams.max-stream-length:10000}")
    private long maxStreamLength;

    public void publish(Task task) {
        String streamKey = StreamKeyResolver.getStreamKey(task.getLambdaName(), task.getPriority());

        StringRecord record = StreamRecords.string(Map.of("task_id", task.getId().toString()))
                .withStreamKey(streamKey);

        RecordId recordId = redisTemplate.opsForStream().add(
                record,
                XAddOptions.maxlen(maxStreamLength).approximateTrimming(true)
        );

        log.debug("Published task {} to stream {}. Redis RecordId: {}", task.getId(), streamKey, recordId);
    }
}