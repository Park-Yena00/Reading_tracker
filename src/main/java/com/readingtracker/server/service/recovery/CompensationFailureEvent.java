package com.readingtracker.server.service.recovery;

import java.time.Instant;

/**
 * 보상 트랜잭션 실패 이벤트
 */
public class CompensationFailureEvent {
    
    private String action;
    private Long entityId;
    private String entityType;
    private String targetDB;
    private Instant failureTime;
    private String errorMessage;
    private int retryCount;
    
    public CompensationFailureEvent() {
        this.retryCount = 0;
    }
    
    public CompensationFailureEvent(String action, Long entityId, String entityType, 
                                   String targetDB, Instant failureTime, String errorMessage) {
        this.action = action;
        this.entityId = entityId;
        this.entityType = entityType;
        this.targetDB = targetDB;
        this.failureTime = failureTime;
        this.errorMessage = errorMessage;
        this.retryCount = 0;
    }
    
    public int incrementRetryCount() {
        return ++retryCount;
    }
    
    // Getters and Setters
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public Long getEntityId() {
        return entityId;
    }
    
    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public String getTargetDB() {
        return targetDB;
    }
    
    public void setTargetDB(String targetDB) {
        this.targetDB = targetDB;
    }
    
    public Instant getFailureTime() {
        return failureTime;
    }
    
    public void setFailureTime(Instant failureTime) {
        this.failureTime = failureTime;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public String getCompensationAction() {
        return action;
    }
    
    @Override
    public String toString() {
        return "CompensationFailureEvent{" +
                "action='" + action + '\'' +
                ", entityId=" + entityId +
                ", entityType='" + entityType + '\'' +
                ", targetDB='" + targetDB + '\'' +
                ", failureTime=" + failureTime +
                ", retryCount=" + retryCount +
                '}';
    }
}

