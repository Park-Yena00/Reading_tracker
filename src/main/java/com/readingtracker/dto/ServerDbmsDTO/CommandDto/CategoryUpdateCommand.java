package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

import com.readingtracker.common.BookCategory;

/**
 * 책 카테고리 변경 명령 DTO
 */
public class CategoryUpdateCommand {
    
    private Long userBookId;
    private BookCategory category;
    
    // Constructors
    public CategoryUpdateCommand() {
    }
    
    public CategoryUpdateCommand(Long userBookId, BookCategory category) {
        this.userBookId = userBookId;
        this.category = category;
    }
    
    // Getters
    public Long getUserBookId() {
        return userBookId;
    }
    
    public BookCategory getCategory() {
        return category;
    }
    
    // Setters
    public void setUserBookId(Long userBookId) {
        this.userBookId = userBookId;
    }
    
    public void setCategory(BookCategory category) {
        this.category = category;
    }
}
