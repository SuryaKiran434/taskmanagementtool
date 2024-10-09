package com.suryakiran.taskmanagementtool.model;

public enum Status {
    TO_DO("To-Do"),
    IN_PROGRESS("In Progress"),
    COMPLETE("Complete");

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}