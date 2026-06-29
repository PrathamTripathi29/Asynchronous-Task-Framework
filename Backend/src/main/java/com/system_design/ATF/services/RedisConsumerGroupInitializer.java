package com.system_design.ATF.services;

import com.system_design.ATF.components.StreamKeyResolver;
import com.system_design.ATF.entity.Lambda;
import com.system_design.ATF.entity.Priority;
import com.system_design.ATF.repository.LambdaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class RedisConsumerGroupInitializer implements ConsumerGroupInitializer{
    private final StringRedisTemplate redisTemplate;
    private final LambdaRepository lambdaRepository;

    private static final String CONSUMER_GROUP_NAME = "atf-workers";

    private void createGroup(String streamKey){
        redisTemplate.execute((RedisConnection connection) -> {
            try {
                connection.streamCommands().xGroupCreate(
                        streamKey.getBytes(),
                        CONSUMER_GROUP_NAME,
                        ReadOffset.from("0-0"),
                        true
                );
                log.info("Created stream and consumer groups for: {}", streamKey);
            } catch (RedisSystemException ex) {
                boolean isBusyGroup = (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) ||
                        (ex.getCause() != null && ex.getCause().getMessage() != null && ex.getCause().getMessage().contains("BUSYGROUP"));
                if(isBusyGroup){
                    log.debug("Consumer group already exists for {}", CONSUMER_GROUP_NAME, streamKey);
                } else {
                    throw ex;
                }
            }
            return  null;
        });
    }

    @PostConstruct
    public void initializeAllGroups(){
        log.info("Bootstrapping Redis Consumer Groups for all active lambdas:");
        for(Lambda lambda : lambdaRepository.findAll()){
            initializeGroup(lambda.getName());
        }
        log.info("Redis Bootstrapping complete.");
    }

    @Override
    public void initializeGroup(String lambdaName){
        for(Priority priority : Priority.values()){
            String streamKey = StreamKeyResolver.getStreamKey(lambdaName, priority);
            createGroup(streamKey);
        }
    }


}
