package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 책 검색 명령 DTO
 */
public class BookSearchCommand {
    
    private String query;
    private Integer start;
    private Integer maxResults;
    
    // Constructors
    public BookSearchCommand() {
    }
    
    public BookSearchCommand(String query, Integer start, Integer maxResults) {
        this.query = query;
        this.start = start;
        this.maxResults = maxResults;
    }
    
    // Getters
    public String getQuery() {
        return query;
    }
    
    public Integer getStart() {
        return start;
    }
    
    public Integer getMaxResults() {
        return maxResults;
    }
    
    // Setters
    public void setQuery(String query) {
        this.query = query;
    }
    
    public void setStart(Integer start) {
        this.start = start;
    }
    
    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
