package com.readingtracker.dto.ClientServerDTO.RequestDTO;

/**
 * 책 검색 요청 DTO
 */
public class BookSearchRequest {
    
    private String query;
    private String queryType = "Title"; // 기본값: 제목 검색
    private String searchTarget = "Book"; // 기본값: 도서
    private Integer start = 1;
    private Integer maxResults = 10;
    
    // 기본 생성자
    public BookSearchRequest() {}
    
    // 생성자
    public BookSearchRequest(String query) {
        this.query = query;
    }
    
    public BookSearchRequest(String query, String queryType, Integer start, Integer maxResults) {
        this.query = query;
        this.queryType = queryType;
        this.start = start;
        this.maxResults = maxResults;
    }
    
    // Getters
    public String getQuery() { return query; }
    public String getQueryType() { return queryType; }
    public String getSearchTarget() { return searchTarget; }
    public Integer getStart() { return start; }
    public Integer getMaxResults() { return maxResults; }
    
    // Setters
    public void setQuery(String query) { this.query = query; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public void setSearchTarget(String searchTarget) { this.searchTarget = searchTarget; }
    public void setStart(Integer start) { this.start = start; }
    public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
}

