package com.readingtracker.common;

/**
 * 책 카테고리 Enum
 */
public enum BookCategory {
    ToRead("읽고 싶은 책"),
    Reading("읽고 있는 책"),
    AlmostFinished("거의 다 읽은 책"),
    Finished("완독한 책");
    
    private final String description;
    
    BookCategory(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

