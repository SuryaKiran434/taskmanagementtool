package com.suryakiran.taskmanagementtool.dto;

public class TaskStatsDTO {

    private long total;
    private long toDo;
    private long inProgress;
    private long complete;
    private long overdue;

    public TaskStatsDTO(long total, long toDo, long inProgress, long complete, long overdue) {
        this.total = total;
        this.toDo = toDo;
        this.inProgress = inProgress;
        this.complete = complete;
        this.overdue = overdue;
    }

    public long getTotal() { return total; }
    public long getToDo() { return toDo; }
    public long getInProgress() { return inProgress; }
    public long getComplete() { return complete; }
    public long getOverdue() { return overdue; }
}
