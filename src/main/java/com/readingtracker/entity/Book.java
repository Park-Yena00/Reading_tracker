package com.readingtracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Books")
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "isbn", unique = true, nullable = false, length = 13)
    private String isbn;
    
    @Column(name = "title", nullable = false, length = 255)
    private String title;
    
    @Column(name = "author", nullable = false, length = 255)
    private String author;
    
    @Column(name = "publisher", nullable = false, length = 255)
    private String publisher;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "cover_url", length = 255)
    private String coverUrl;
    
    @Column(name = "total_pages")
    private Integer totalPages;
    
    @Column(name = "main_genre", length = 50)
    private String mainGenre;
    
    @Column(name = "pub_date")
    private LocalDate pubDate;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // 1:N 관계 설정 (UserBooks)
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserShelfBook> userBooks = new ArrayList<>();
    
    // 기본 생성자
    public Book() {}
    
    // 생성자
    public Book(String isbn, String title, String author, String publisher) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
    }
    
    // Getters
    public Long getId() { return id; }
    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public String getDescription() { return description; }
    public String getCoverUrl() { return coverUrl; }
    public Integer getTotalPages() { return totalPages; }
    public String getMainGenre() { return mainGenre; }
    public LocalDate getPubDate() { return pubDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<UserShelfBook> getUserBooks() { return userBooks; }
    
    // Setters
    public void setId(Long id) { this.id = id; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public void setMainGenre(String mainGenre) { this.mainGenre = mainGenre; }
    public void setPubDate(LocalDate pubDate) { this.pubDate = pubDate; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setUserBooks(List<UserShelfBook> userBooks) { this.userBooks = userBooks; }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

