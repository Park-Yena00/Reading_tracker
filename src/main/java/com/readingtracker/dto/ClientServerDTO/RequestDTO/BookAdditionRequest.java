package com.readingtracker.dto.ClientServerDTO.RequestDTO;

import com.readingtracker.common.BookCategory;

import java.time.LocalDate;

/**
 * 내 서재에 책 추가 요청 DTO
 */
public class BookAdditionRequest {
    
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
}
