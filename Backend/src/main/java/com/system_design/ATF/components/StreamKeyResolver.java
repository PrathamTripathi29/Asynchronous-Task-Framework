package com.system_design.ATF.components;

import com.system_design.ATF.entity.Priority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StreamKeyResolver {
    private static final String ROOT_PREFIX = "atf";
    private static final String STREAM_PREFIX = "stream";
    private static final String DLQ_PREFIX = "deadletter";
    private static final String SEPARATOR = ":";

    // wanna generate: atf:stream:{lambda}:{priority}
    public static String getStreamKey(String lambdaName, Priority priority){
        return String.join(SEPARATOR, ROOT_PREFIX, STREAM_PREFIX, lambdaName, priority.name());
    }

    //atf:deadletter:{lambda}
    public static String getDeadLetterKey(String lambdaName){
        return String.join(SEPARATOR, ROOT_PREFIX, DLQ_PREFIX, lambdaName);
    }

    public static List<String> getAllStreamKeys(String lambdaName){
        return Arrays
                .stream(Priority.values())
                .map(priority -> getStreamKey(lambdaName, priority))
                .collect(Collectors.toList());
    }
}
