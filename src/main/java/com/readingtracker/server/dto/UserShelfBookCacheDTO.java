package com.readingtracker.server.dto;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.PurchaseType;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Redis 캐싱을 위한 UserShelfBook DTO
 * 순환 참조를 방지하고 필요한 데이터만 포함
 */
public class UserShelfBookCacheDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // UserShelfBook 필드
    private Long id;
    private Long userId;
    private Long bookId;
    private BookCategory category;
    private Boolean categoryManuallySet;
    private String expectation;
    private LocalDate readingStartDate;
    private Integer readingProgress;
    private PurchaseType purchaseType;
    private LocalDate readingFinishedDate;
    private Integer rating;
    private String review;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Book 필드 (순환 참조 방지를 위해 엔티티 대신 필드만 포함)
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String description;
    private String coverUrl;
    private Integer totalPages;
    private String mainGenre;
    private LocalDate pubDate;
    
    // 기본 생성자
    public UserShelfBookCacheDTO() {}
    
    // Getters
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getBookId() { return bookId; }
    public BookCategory getCategory() { return category; }
    public Boolean getCategoryManuallySet() { return categoryManuallySet; }
    public String getExpectation() { return expectation; }
    public LocalDate getReadingStartDate() { return readingStartDate; }
    public Integer getReadingProgress() { return readingProgress; }
    public PurchaseType getPurchaseType() { return purchaseType; }
    public LocalDate getReadingFinishedDate() { return readingFinishedDate; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public String getDescription() { return description; }
    public String getCoverUrl() { return coverUrl; }
    public Integer getTotalPages() { return totalPages; }
    public String getMainGenre() { return mainGenre; }
    public LocalDate getPubDate() { return pubDate; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setCategory(BookCategory category) { this.category = category; }
    public void setCategoryManuallySet(Boolean categoryManuallySet) { this.categoryManuallySet = categoryManuallySet; }
    public void setExpectation(String expectation) { this.expectation = expectation; }
    public void setReadingStartDate(LocalDate readingStartDate) { this.readingStartDate = readingStartDate; }
    public void setReadingProgress(Integer readingProgress) { this.readingProgress = readingProgress; }
    public void setPurchaseType(PurchaseType purchaseType) { this.purchaseType = purchaseType; }
    public void setReadingFinishedDate(LocalDate readingFinishedDate) { this.readingFinishedDate = readingFinishedDate; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setReview(String review) { this.review = review; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public void setMainGenre(String mainGenre) { this.mainGenre = mainGenre; }
    public void setPubDate(LocalDate pubDate) { this.pubDate = pubDate; }
}


