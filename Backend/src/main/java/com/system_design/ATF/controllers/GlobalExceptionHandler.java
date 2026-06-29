package com.system_design.ATF.controllers;

import com.system_design.ATF.exception.DuplicateLambdaException;
import com.system_design.ATF.exception.LambdaNotFoundException;
import com.system_design.ATF.exception.TaskDroppedException;
import com.system_design.ATF.exception.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LambdaNotFoundException.class)
    public ResponseEntity<String> handleLambdaNotFound(LambdaNotFoundException ex){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateLambdaException.class)
    public ResponseEntity<String> handleDuplicateLambda(DuplicateLambdaException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<String> handleTaskNotFound(TaskNotFoundException ex){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(TaskDroppedException.class)
    public ResponseEntity<String> handleTaskDropped(TaskDroppedException ex){
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ex.getMessage());
    }
}
