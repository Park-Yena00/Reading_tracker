package com.readingtracker.server.dto.responseDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.readingtracker.server.common.constant.BookCategory;

/**
 * 내 서재 조회 응답 DTO
 */
public class MyShelfResponse {
    
    private List<ShelfBook> books;
    private Integer totalCount;
    
    // 기본 생성자
    public MyShelfResponse() {}
    
    // 생성자
    public MyShelfResponse(List<ShelfBook> books, Integer totalCount) {
        this.books = books;
        this.totalCount = totalCount;
    }
    
    // Getters
    public List<ShelfBook> getBooks() { return books; }
    public Integer getTotalCount() { return totalCount; }
    
    // Setters
    public void setBooks(List<ShelfBook> books) { this.books = books; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    
    /**
     * 서재 책 정보 DTO
     */
    public static class ShelfBook {
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
        private LocalDate pubDate;
        private BookCategory category;
        private Integer lastReadPage;
        private LocalDate lastReadAt;
        private LocalDateTime addedAt;
        
        // 기본 생성자
        public ShelfBook() {}
        
        // Getters
        public Long getUserBookId() { return userBookId; }
        public Long getBookId() { return bookId; }
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
        public Integer getLastReadPage() { return lastReadPage; }
        public LocalDate getLastReadAt() { return lastReadAt; }
        public LocalDateTime getAddedAt() { return addedAt; }
        
        // Setters
        public void setUserBookId(Long userBookId) { this.userBookId = userBookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
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
        public void setLastReadPage(Integer lastReadPage) { this.lastReadPage = lastReadPage; }
        public void setLastReadAt(LocalDate lastReadAt) { this.lastReadAt = lastReadAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    }
}
