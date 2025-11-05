package com.readingtracker.dbms.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.PurchaseType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "User_Books")
@EntityListeners(AuditingEntityListener.class)
public class UserShelfBook {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private BookCategory category;
    
    // 읽고 싶은 책: 기대평 (선택)
    @Column(name = "expectation", length = 500)
    private String expectation;
    
    // 메모 (Memo table과 구분)
    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;
    
    // 읽는 중인 책 / 거의 다 읽은 책 / 완독한 책: 독서 시작일
    @Column(name = "reading_start_date")
    private LocalDate readingStartDate;
    
    // 읽는 중인 책 / 거의 다 읽은 책: 독서량 (읽은 페이지 수)
    @Column(name = "reading_progress")
    private Integer readingProgress;
    
    // 읽는 중인 책: 구매/대여 여부 (선택)
    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", length = 20)
    private PurchaseType purchaseType;
    
    // 완독한 책: 독서 종료일
    @Column(name = "reading_finished_date")
    private LocalDate readingFinishedDate;
    
    // 완독한 책: 평점 (예: 1~5)
    @Column(name = "rating")
    private Integer rating;
    
    // 완독한 책: 후기 (선택)
    @Column(name = "review", columnDefinition = "TEXT")
    private String review;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public UserShelfBook() {}
    
    public UserShelfBook(User user, Book book, BookCategory category) {
        this.user = user;
        this.book = book;
        this.category = category;
    }
    
    // Getters
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Book getBook() { return book; }
    public BookCategory getCategory() { return category; }
    public String getExpectation() { return expectation; }
    public String getMemo() { return memo; }
    public LocalDate getReadingStartDate() { return readingStartDate; }
    public Integer getReadingProgress() { return readingProgress; }
    public PurchaseType getPurchaseType() { return purchaseType; }
    public LocalDate getReadingFinishedDate() { return readingFinishedDate; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getUserId() { return user != null ? user.getId() : null; }
    public Long getBookId() { return book != null ? book.getId() : null; }
    public Integer getLastReadPage() { return readingProgress; }
    public LocalDate getLastReadAt() { return readingStartDate; }
    public LocalDateTime getAddedAt() { return createdAt; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setBook(Book book) { this.book = book; }
    public void setCategory(BookCategory category) { this.category = category; }
    public void setExpectation(String expectation) { this.expectation = expectation; }
    public void setMemo(String memo) { this.memo = memo; }
    public void setReadingStartDate(LocalDate readingStartDate) { this.readingStartDate = readingStartDate; }
    public void setReadingProgress(Integer readingProgress) { this.readingProgress = readingProgress; }
    public void setPurchaseType(PurchaseType purchaseType) { this.purchaseType = purchaseType; }
    public void setReadingFinishedDate(LocalDate readingFinishedDate) { this.readingFinishedDate = readingFinishedDate; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setReview(String review) { this.review = review; }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
