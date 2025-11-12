package com.readingtracker.server.dto.requestDTO;

import java.time.LocalDate;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.PurchaseType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 내 서재에 책 추가 요청 DTO
 */
public class BookAdditionRequest {
    
    @NotBlank(message = "ISBN은 필수 입력 항목입니다.")
    private String isbn;
    
    @NotBlank(message = "도서명은 필수 입력 항목입니다.")
    private String title;
    
    @NotBlank(message = "저자명은 필수 입력 항목입니다.")
    private String author;
    
    private String publisher;
    private String description;
    private String coverUrl;
    private Integer totalPages;
    private String mainGenre;
    private LocalDate pubDate;
    
    @NotNull(message = "카테고리는 필수 입력 항목입니다.")
    private BookCategory category;
    
    // 카테고리별 입력값 필드
    
    // ToRead: 기대평 (선택사항, 500자 이하)
    @Size(max = 500, message = "기대평은 500자 이하여야 합니다.")
    private String expectation;
    
    // Reading, AlmostFinished, Finished: 독서 시작일 (필수)
    private LocalDate readingStartDate;
    
    // Reading, AlmostFinished: 현재 읽은 페이지 수 (필수)
    @Min(value = 0, message = "읽은 페이지 수는 0 이상이어야 합니다.")
    private Integer readingProgress;
    
    // Reading: 구매/대여 여부 (선택사항)
    private PurchaseType purchaseType;
    
    // Finished: 독서 종료일 (필수)
    private LocalDate readingFinishedDate;
    
    // Finished: 평점 (필수, 1~5)
    @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5 이하여야 합니다.")
    private Integer rating;
    
    // Finished: 후기 (선택사항)
    private String review;
    
    // 기본 생성자
    public BookAdditionRequest() {}
    
    // 생성자
    public BookAdditionRequest(String isbn, String title, String author, String publisher, BookCategory category) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
    }
    
    // Getters
    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public String getDescription() { return description; }
    public String getCoverUrl() { return coverUrl; }
    public Integer getTotalPages() { return totalPages; }
    public String getMainGenre() { return mainGenre; }
    public LocalDate getPubDate() { return pubDate; }
    public BookCategory getCategory() { return category; }
    public String getExpectation() { return expectation; }
    public LocalDate getReadingStartDate() { return readingStartDate; }
    public Integer getReadingProgress() { return readingProgress; }
    public PurchaseType getPurchaseType() { return purchaseType; }
    public LocalDate getReadingFinishedDate() { return readingFinishedDate; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    
    // Setters
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public void setMainGenre(String mainGenre) { this.mainGenre = mainGenre; }
    public void setPubDate(LocalDate pubDate) { this.pubDate = pubDate; }
    public void setCategory(BookCategory category) { this.category = category; }
    public void setExpectation(String expectation) { this.expectation = expectation; }
    public void setReadingStartDate(LocalDate readingStartDate) { this.readingStartDate = readingStartDate; }
    public void setReadingProgress(Integer readingProgress) { this.readingProgress = readingProgress; }
    public void setPurchaseType(PurchaseType purchaseType) { this.purchaseType = purchaseType; }
    public void setReadingFinishedDate(LocalDate readingFinishedDate) { this.readingFinishedDate = readingFinishedDate; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setReview(String review) { this.review = review; }
}
