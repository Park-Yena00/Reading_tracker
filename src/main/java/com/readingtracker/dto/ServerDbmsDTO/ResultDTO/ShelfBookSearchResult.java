package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

import com.readingtracker.common.BookCategory;

import java.time.LocalDateTime;

/**
 * 서재 책 조회 결과 데이터 전송 객체 (Service Layer)
 */
public class ShelfBookSearchResult {
    
    private Long userBookId;
    private Long bookId;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String description;
    private String coverUrl;
    private Integer totalPages;
    private String mainGenre;
    private BookCategory category;
    private Integer lastReadPage;
    private LocalDateTime lastReadAt;
    private LocalDateTime addedAt;
    
    // Constructors
    public ShelfBookSearchResult() {
    }
    
    public ShelfBookSearchResult(Long userBookId, Long bookId, String isbn, String title, String author, String publisher, BookCategory category, Integer lastReadPage) {
        this.userBookId = userBookId;
        this.bookId = bookId;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
        this.lastReadPage = lastReadPage;
    }
    
    // Getter methods
    public Long getUserBookId() {
        return userBookId;
    }
    
    public Long getBookId() {
        return bookId;
    }
    
    public String getIsbn() {
        return isbn;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public String getPublisher() {
        return publisher;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getCoverUrl() {
        return coverUrl;
    }
    
    public Integer getTotalPages() {
        return totalPages;
    }
    
    public String getMainGenre() {
        return mainGenre;
    }
    
    public BookCategory getCategory() {
        return category;
    }
    
    public Integer getLastReadPage() {
        return lastReadPage;
    }
    
    public LocalDateTime getLastReadAt() {
        return lastReadAt;
    }
    
    public LocalDateTime getAddedAt() {
        return addedAt;
    }
    
    // Setter methods
    public void setUserBookId(Long userBookId) {
        this.userBookId = userBookId;
    }
    
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    
    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    
    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
    
    public void setMainGenre(String mainGenre) {
        this.mainGenre = mainGenre;
    }
    
    public void setCategory(BookCategory category) {
        this.category = category;
    }
    
    public void setLastReadPage(Integer lastReadPage) {
        this.lastReadPage = lastReadPage;
    }
    
    public void setLastReadAt(LocalDateTime lastReadAt) {
        this.lastReadAt = lastReadAt;
    }
    
    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
