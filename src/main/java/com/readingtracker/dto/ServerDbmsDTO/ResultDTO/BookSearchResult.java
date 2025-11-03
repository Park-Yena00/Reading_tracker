package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 책 검색 결과 데이터 전송 객체 (Service Layer)
 */
public class BookSearchResult {
    
    private List<BookInfo> books;
    private Integer totalResults;
    private Integer startIndex;
    private Integer itemsPerPage;
    private String query;
    
    // Constructors
    public BookSearchResult() {
    }
    
    public BookSearchResult(List<BookInfo> books, Integer totalResults, Integer startIndex, Integer itemsPerPage, String query) {
        this.books = books;
        this.totalResults = totalResults;
        this.startIndex = startIndex;
        this.itemsPerPage = itemsPerPage;
        this.query = query;
    }
    
    // Getter methods
    public List<BookInfo> getBooks() {
        return books;
    }
    
    public Integer getTotalResults() {
        return totalResults;
    }
    
    public Integer getStartIndex() {
        return startIndex;
    }
    
    public Integer getItemsPerPage() {
        return itemsPerPage;
    }
    
    public String getQuery() {
        return query;
    }
    
    // Setter methods
    public void setBooks(List<BookInfo> books) {
        this.books = books;
    }
    
    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }
    
    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }
    
    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    /**
     * 책 정보 데이터 전송 객체
     */
    public static class BookInfo {
        private String isbn;
        private String isbn13;
        private String title;
        private String author;
        private String publisher;
        private String description;
        private String coverUrl;
        private Integer totalPages;
        private String mainGenre;
        private LocalDate pubDate;
        private Integer priceSales;
        private Integer priceStandard;
        
        // Constructors
        public BookInfo() {
        }
        
        public BookInfo(String isbn, String title, String author, String publisher) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.publisher = publisher;
        }
        
        // Getter methods
        public String getIsbn() {
            return isbn;
        }
        
        public String getIsbn13() {
            return isbn13;
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
        
        public LocalDate getPubDate() {
            return pubDate;
        }
        
        public Integer getPriceSales() {
            return priceSales;
        }
        
        public Integer getPriceStandard() {
            return priceStandard;
        }
        
        // Setter methods
        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }
        
        public void setIsbn13(String isbn13) {
            this.isbn13 = isbn13;
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
        
        public void setPubDate(LocalDate pubDate) {
            this.pubDate = pubDate;
        }
        
        public void setPriceSales(Integer priceSales) {
            this.priceSales = priceSales;
        }
        
        public void setPriceStandard(Integer priceStandard) {
            this.priceStandard = priceStandard;
        }
    }
}
