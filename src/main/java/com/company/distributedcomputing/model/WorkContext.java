package com.company.distributedcomputing.model;

import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

@Data
public class WorkContext implements Serializable {
    private final String contextId;
    private final String rootId;
    private final int totalWork;
    private final int depth;

    public WorkContext(String rootId, int totalWork) {
        this.contextId = UUID.randomUUID().toString();
        this.rootId = rootId;
        this.totalWork = totalWork;
        this.depth = 0;
    }

    public WorkContext(WorkContext parent, int depth) {
        this.contextId = parent.contextId;
        this.rootId = parent.rootId;
        this.totalWork = parent.totalWork;
        this.depth = depth;
    }
}