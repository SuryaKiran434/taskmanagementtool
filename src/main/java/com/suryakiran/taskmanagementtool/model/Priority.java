package com.suryakiran.taskmanagementtool.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String displayName;

    Priority(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static Priority fromValue(String value) {
        for (Priority priority : Priority.values()) {
            if (priority.displayName.equalsIgnoreCase(value)) {
                return priority;
            }
        }
        // Throw an exception or return a default value if needed
        throw new IllegalArgumentException("Invalid Priority: " + value);
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
