package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

import com.readingtracker.common.BookCategory;

/**
 * 책 추가 명령 DTO
 */
public class AddBookCommand {
    
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private BookCategory category;
    
    // Constructors
    public AddBookCommand() {
    }
    
    public AddBookCommand(String isbn, String title, String author, String publisher, BookCategory category) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
    }
    
    // Getters
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
    
    public BookCategory getCategory() {
        return category;
    }
    
    // Setters
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
    
    public void setCategory(BookCategory category) {
        this.category = category;
    }
}
