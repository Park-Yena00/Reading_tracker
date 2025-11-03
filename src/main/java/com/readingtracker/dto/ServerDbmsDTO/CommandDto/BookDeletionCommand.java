package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 책 제거 명령 DTO
 */
public class BookDeletionCommand {
    
    private Long userBookId;
    
    // Constructors
    public BookDeletionCommand() {
    }
    
    public BookDeletionCommand(Long userBookId) {
        this.userBookId = userBookId;
    }
    
    // Getters
    public Long getUserBookId() {
        return userBookId;
    }
    
    // Setters
    public void setUserBookId(Long userBookId) {
        this.userBookId = userBookId;
    }
}
