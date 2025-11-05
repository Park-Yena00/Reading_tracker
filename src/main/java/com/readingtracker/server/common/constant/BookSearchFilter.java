package com.readingtracker.server.common.constant;

/**
 * 도서 검색 필터 Enum
 * 알라딘 API의 QueryType과 매핑
 */
public enum BookSearchFilter {
    TITLE("Title"),      // 도서명
    AUTHOR("Author"),    // 저자명
    PUBLISHER("Publisher"); // 출판사명
    
    private final String apiValue;
    
    BookSearchFilter(String apiValue) {
        this.apiValue = apiValue;
    }
    
    /**
     * 알라딘 API QueryType 값 반환
     */
    public String getApiValue() {
        return apiValue;
    }
}

