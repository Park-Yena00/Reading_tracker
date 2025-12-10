package com.readingtracker.server.dto.responseDTO;

import com.readingtracker.server.common.constant.BookCategory;

/**
 * 내 서재에 책 추가 응답 DTO
 */
public class BookAdditionResponse {
    
    private String message;
    private Long userBookId;  // UserShelfBook의 ID (내 서재에 추가된 책의 고유 ID)
    private Long bookId;      // Book의 ID (도서 정보의 고유 ID)
    private String title;
    private BookCategory category;
    
    // 기본 생성자
    public BookAdditionResponse() {}
    
    // 생성자
    public BookAdditionResponse(String message, Long userBookId, Long bookId, String title, BookCategory category) {
        this.message = message;
        this.userBookId = userBookId;
        this.bookId = bookId;
        this.title = title;
        this.category = category;
    }
    
    // Getters
    public String getMessage() { return message; }
    public Long getUserBookId() { return userBookId; }
    public Long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public BookCategory getCategory() { return category; }
    
    // Setters
    public void setMessage(String message) { this.message = message; }
    public void setUserBookId(Long userBookId) { this.userBookId = userBookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setTitle(String title) { this.title = title; }
    public void setCategory(BookCategory category) { this.category = category; }
}
