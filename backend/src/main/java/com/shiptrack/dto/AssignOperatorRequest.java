package com.shiptrack.dto;

public class AssignOperatorRequest {

    private Long operatorId;
    private Long assignedOperatorId;

    public AssignOperatorRequest() {}

    public AssignOperatorRequest(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getOperatorId() {
        return operatorId != null ? operatorId : assignedOperatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getAssignedOperatorId() {
        return assignedOperatorId != null ? assignedOperatorId : operatorId;
    }

    public void setAssignedOperatorId(Long assignedOperatorId) {
        this.assignedOperatorId = assignedOperatorId;
    }
}
