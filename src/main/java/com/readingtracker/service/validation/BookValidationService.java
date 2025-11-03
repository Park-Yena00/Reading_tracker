package com.readingtracker.service.validation;

import com.readingtracker.common.BookCategory;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.AddBookCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.BookDeletionCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.BookSearchCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.CategoryUpdateCommand;
import org.springframework.stereotype.Service;

/**
 * 책 관련 검증 서비스
 */
@Service
public class BookValidationService {
    
    /**
     * 책 추가 검증
     */
    public void validateAddBook(AddBookCommand command) {
        validateIsbn(command.getIsbn());
        validateTitle(command.getTitle());
        validateAuthor(command.getAuthor());
        validatePublisher(command.getPublisher());
        validateCategory(command.getCategory());
    }
    
    /**
     * ISBN 검증
     */
    public void validateIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new IllegalArgumentException("ISBN은 필수입니다");
        }
        if (isbn.length() < 10 || isbn.length() > 17) {
            throw new IllegalArgumentException("ISBN은 10-17자 사이여야 합니다");
        }
    }
    
    /**
     * 제목 검증
     */
    public void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("제목은 필수입니다");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("제목은 200자를 초과할 수 없습니다");
        }
    }
    
    /**
     * 저자 검증
     */
    public void validateAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("저자는 필수입니다");
        }
        if (author.length() > 100) {
            throw new IllegalArgumentException("저자는 100자를 초과할 수 없습니다");
        }
    }
    
    /**
     * 출판사 검증
     */
    public void validatePublisher(String publisher) {
        if (publisher == null || publisher.trim().isEmpty()) {
            throw new IllegalArgumentException("출판사는 필수입니다");
        }
        if (publisher.length() > 100) {
            throw new IllegalArgumentException("출판사는 100자를 초과할 수 없습니다");
        }
    }
    
    /**
     * 카테고리 검증
     */
    public void validateCategory(Object category) {
        if (category == null) {
            throw new IllegalArgumentException("카테고리는 필수입니다");
        }
        // BookCategory enum 타입 검증
        if (!(category instanceof BookCategory)) {
            throw new IllegalArgumentException("유효하지 않은 카테고리입니다");
        }
    }
    
    /**
     * 책 카테고리 변경 검증
     */
    public void validateUpdateBookCategory(CategoryUpdateCommand command) {
        if (command.getUserBookId() == null) {
            throw new IllegalArgumentException("사용자 책 ID는 필수입니다");
        }
        validateCategory(command.getCategory());
    }
    
    /**
     * 책 제거 검증
     */
    public void validateRemoveBook(BookDeletionCommand command) {
        if (command.getUserBookId() == null) {
            throw new IllegalArgumentException("사용자 책 ID는 필수입니다");
        }
    }
    
    /**
     * 책 검색 검증
     */
    public void validateBookSearch(BookSearchCommand command) {
        if (command.getQuery() == null || command.getQuery().trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 필수입니다");
        }
        if (command.getQuery().length() < 2) {
            throw new IllegalArgumentException("검색어는 최소 2자 이상이어야 합니다");
        }
        if (command.getStart() != null && command.getStart() < 1) {
            throw new IllegalArgumentException("시작 페이지는 1 이상이어야 합니다");
        }
        if (command.getMaxResults() != null && (command.getMaxResults() < 1 || command.getMaxResults() > 50)) {
            throw new IllegalArgumentException("페이지당 결과 수는 1-50 사이여야 합니다");
        }
    }
}
