package com.readingtracker.server.dto.requestDTO;

import com.readingtracker.server.common.constant.BookSearchFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 책 검색 요청 DTO
 */
public class BookSearchRequest {
    
    @NotBlank(message = "검색어는 필수입니다.")
    @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
    private String query;
    
    private BookSearchFilter queryType = BookSearchFilter.TITLE; // 기본값: 제목 검색
    
    private String searchTarget = "Book"; // 기본값: 도서
    
    @Min(value = 1, message = "시작 페이지는 1 이상이어야 합니다.")
    private Integer start = 1;
    
    @Min(value = 1, message = "페이지당 결과 수는 1 이상이어야 합니다.")
    @Max(value = 50, message = "페이지당 결과 수는 50 이하여야 합니다.")
    private Integer maxResults = 10;
    
    // 기본 생성자
    public BookSearchRequest() {}
    
    // 생성자
    public BookSearchRequest(String query) {
        this.query = query;
    }
    
    public BookSearchRequest(String query, BookSearchFilter queryType, Integer start, Integer maxResults) {
        this.query = query;
        this.queryType = queryType;
        this.start = start;
        this.maxResults = maxResults;
    }
    
    // Getters
    public String getQuery() { return query; }
    public BookSearchFilter getQueryType() { return queryType; }
    public String getSearchTarget() { return searchTarget; }
    public Integer getStart() { return start; }
    public Integer getMaxResults() { return maxResults; }
    
    // Setters
    public void setQuery(String query) { this.query = query; }
    public void setQueryType(BookSearchFilter queryType) { this.queryType = queryType; }
    public void setSearchTarget(String searchTarget) { this.searchTarget = searchTarget; }
    public void setStart(Integer start) { this.start = start; }
    public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
}

