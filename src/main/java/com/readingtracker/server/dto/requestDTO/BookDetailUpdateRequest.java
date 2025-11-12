package com.readingtracker.server.dto.requestDTO;

import java.time.LocalDate;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.PurchaseType;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 책 상세 정보 변경 요청 DTO
 */
public class BookDetailUpdateRequest {
    
    // 카테고리 변경 (선택사항, "독서 시작" 버튼 클릭 시 등)
    private BookCategory category;
    
    // 카테고리별 입력값 필드
    
    // ToRead: 기대평 (선택사항, 500자 이하)
    @Size(max = 500, message = "기대평은 500자 이하여야 합니다.")
    private String expectation;
    
    // Reading, AlmostFinished, Finished: 독서 시작일
    private LocalDate readingStartDate;
    
    // Reading, AlmostFinished: 현재 읽은 페이지 수
    @Min(value = 0, message = "읽은 페이지 수는 0 이상이어야 합니다.")
    private Integer readingProgress;
    
    // Reading: 구매/대여 여부 (선택사항)
    private PurchaseType purchaseType;
    
    // Finished: 독서 종료일
    private LocalDate readingFinishedDate;
    
    // Finished: 평점 (1~5)
    @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5 이하여야 합니다.")
    private Integer rating;
    
    // Finished: 후기 (선택사항)
    private String review;
    
    // 기본 생성자
    public BookDetailUpdateRequest() {}
    
    // Getters
    public BookCategory getCategory() { return category; }
    public String getExpectation() { return expectation; }
    public LocalDate getReadingStartDate() { return readingStartDate; }
    public Integer getReadingProgress() { return readingProgress; }
    public PurchaseType getPurchaseType() { return purchaseType; }
    public LocalDate getReadingFinishedDate() { return readingFinishedDate; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    
    // Setters
    public void setCategory(BookCategory category) { this.category = category; }
    public void setExpectation(String expectation) { this.expectation = expectation; }
    public void setReadingStartDate(LocalDate readingStartDate) { this.readingStartDate = readingStartDate; }
    public void setReadingProgress(Integer readingProgress) { this.readingProgress = readingProgress; }
    public void setPurchaseType(PurchaseType purchaseType) { this.purchaseType = purchaseType; }
    public void setReadingFinishedDate(LocalDate readingFinishedDate) { this.readingFinishedDate = readingFinishedDate; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setReview(String review) { this.review = review; }
}




