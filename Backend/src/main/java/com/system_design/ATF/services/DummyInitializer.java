package com.system_design.ATF.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DummyInitializer implements ConsumerGroupInitializer{
    @Override
    public void initializeGroup(String lambdaName){
        log.info("dummy initialization: {}", lambdaName);
    }
}
