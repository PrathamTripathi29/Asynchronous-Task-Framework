package com.system_design.ATF.entity;

public enum TaskStatus {
    NEW(false),
    ENQUEUED(false),
    CLAIMED(false),
    PROCESSING(false),
    RETRIABLE_FAILURE(false),
    SUCCESS(true),
    FATAL_FAILURE(true);

    private final boolean terminal;

    TaskStatus(boolean terminal){
        this.terminal = terminal;
    }

    public boolean isTerminal(){
        return this.terminal;
    }
}
