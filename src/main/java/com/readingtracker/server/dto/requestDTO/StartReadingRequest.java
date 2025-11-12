package com.readingtracker.server.dto.requestDTO;

import java.time.LocalDate;

import com.readingtracker.server.common.constant.PurchaseType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 독서 시작 요청 DTO
 */
public class StartReadingRequest {

    /**
     * 독서 시작일 (필수)
     */
    @NotNull(message = "독서 시작일은 필수 입력 항목입니다.")
    private LocalDate readingStartDate;

    /**
     * 현재 읽은 페이지 수 (필수, 0 이상)
     */
    @NotNull(message = "현재 읽은 페이지 수는 필수 입력 항목입니다.")
    @Min(value = 0, message = "읽은 페이지 수는 0 이상이어야 합니다.")
    private Integer readingProgress;

    /**
     * 구매/대여 여부 (선택)
     */
    private PurchaseType purchaseType;

    public StartReadingRequest() {
    }

    public LocalDate getReadingStartDate() {
        return readingStartDate;
    }

    public void setReadingStartDate(LocalDate readingStartDate) {
        this.readingStartDate = readingStartDate;
    }

    public Integer getReadingProgress() {
        return readingProgress;
    }

    public void setReadingProgress(Integer readingProgress) {
        this.readingProgress = readingProgress;
    }

    public PurchaseType getPurchaseType() {
        return purchaseType;
    }

    public void setPurchaseType(PurchaseType purchaseType) {
        this.purchaseType = purchaseType;
    }
}






