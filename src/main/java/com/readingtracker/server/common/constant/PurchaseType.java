package com.readingtracker.server.common.constant;

/**
 * 구매/대여 타입 Enum
 */
public enum PurchaseType {
    PURCHASE("구매"),
    RENTAL("대여");
    
    private final String description;
    
    PurchaseType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

