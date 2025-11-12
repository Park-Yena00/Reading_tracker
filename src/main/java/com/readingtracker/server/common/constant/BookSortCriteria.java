package com.readingtracker.server.common.constant;

/**
 * 책 정렬 기준 Enum
 * 내 서재에서 책을 정렬할 때 사용하는 기준
 */
public enum BookSortCriteria {
    TITLE("도서명"),
    AUTHOR("저자명"),
    PUBLISHER("출판사명"),
    GENRE("태그(메인 장르)");
    
    private final String description;
    
    BookSortCriteria(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}




