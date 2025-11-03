package com.readingtracker.dto.ClientServerDTO.ResponseDTO;

import com.readingtracker.common.BookCategory;

/**
 * 내 서재에 책 추가 응답 DTO
 */
public class BookAdditionResponse {
    
    private String message;
    private Long bookId;
    private String title;
    private BookCategory category;
    
    // 기본 생성자
    public BookAdditionResponse() {}
    
    // 생성자
    public BookAdditionResponse(String message, Long bookId, String title, BookCategory category) {
        this.message = message;
        this.bookId = bookId;
        this.title = title;
        this.category = category;
    }
    
    // Getters
    public String getMessage() { return message; }
    public Long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public BookCategory getCategory() { return category; }
    
    // Setters
    public void setMessage(String message) { this.message = message; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setTitle(String title) { this.title = title; }
    public void setCategory(BookCategory category) { this.category = category; }
}
