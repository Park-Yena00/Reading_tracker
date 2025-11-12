package com.readingtracker.server.dto.responseDTO;

import java.time.LocalDate;

/**
 * 도서 세부 정보 응답 DTO
 */
public class BookDetailResponse {
    
    private String isbn;
    private String isbn13;
    private String title;          // 도서명
    private String author;          // 저자명
    private String publisher;       // 출판사명
    private LocalDate pubDate;      // 출판일
    private String coverUrl;        // 표지URL
    private String description;     // 책소개
    private Integer totalPages;      // 전체 페이지 수
    private String mainGenre;       // 메인 태그
    
    // 기본 생성자
    public BookDetailResponse() {}
    
    // 생성자
    public BookDetailResponse(String isbn, String isbn13, String title, String author, String publisher,
                             LocalDate pubDate, String coverUrl, String description, Integer totalPages, String mainGenre) {
        this.isbn = isbn;
        this.isbn13 = isbn13;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.pubDate = pubDate;
        this.coverUrl = coverUrl;
        this.description = description;
        this.totalPages = totalPages;
        this.mainGenre = mainGenre;
    }
    
    // Getters
    public String getIsbn() { return isbn; }
    public String getIsbn13() { return isbn13; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public LocalDate getPubDate() { return pubDate; }
    public String getCoverUrl() { return coverUrl; }
    public String getDescription() { return description; }
    public Integer getTotalPages() { return totalPages; }
    public String getMainGenre() { return mainGenre; }
    
    // Setters
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setIsbn13(String isbn13) { this.isbn13 = isbn13; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setPubDate(LocalDate pubDate) { this.pubDate = pubDate; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setDescription(String description) { this.description = description; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public void setMainGenre(String mainGenre) { this.mainGenre = mainGenre; }
}




