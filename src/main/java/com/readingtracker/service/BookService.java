package com.readingtracker.service;

import com.readingtracker.common.BookCategory;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.BookAdditionRequest;
import com.readingtracker.entity.Book;
import com.readingtracker.entity.User;
import com.readingtracker.entity.UserShelfBook;
import com.readingtracker.repository.BookRepository;
import com.readingtracker.repository.UserShelfBookRepository;
import com.readingtracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private UserShelfBookRepository userBookRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 내 서재에 책 추가
     */
    public UserShelfBook addBookToShelf(String loginId, BookAdditionRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. ISBN으로 책 조회 또는 생성
        Book book = findOrCreateBook(request);
        
        // 3. 중복 추가 방지 체크
        Optional<UserShelfBook> existingBook = userBookRepository.findByUserIdAndBookId(user.getId(), book.getId());
        if (existingBook.isPresent()) {
            throw new IllegalArgumentException("이미 내 서재에 추가된 책입니다.");
        }
        
        // 4. UserBook 생성 (도메인 로직 사용)
        UserShelfBook userBook = new UserShelfBook(user, book, request.getCategory());
        
        return userBookRepository.save(userBook);
    }
    
    /**
     * 내 서재 조회
     */
    @Transactional(readOnly = true)
    public List<UserShelfBook> getMyShelf(String loginId, BookCategory category) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. 내 서재 조회
        if (category != null) {
            return userBookRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(user.getId(), category);
        } else {
            return userBookRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
    }
    
    /**
     * 내 서재에서 책 제거
     */
    public void removeBookFromShelf(String loginId, Long userBookId) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. UserBook 조회 및 소유권 확인
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 3. 삭제
        userBookRepository.delete(userBook);
    }
    
    /**
     * 책 읽기 상태 변경
     */
    public void updateBookCategory(String loginId, Long userBookId, BookCategory category) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. UserBook 조회 및 소유권 확인
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 3. 카테고리 변경
        userBook.setCategory(category);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        userBookRepository.save(userBook);
    }
    
    /**
     * 책 조회 또는 생성
     */
    private Book findOrCreateBook(BookAdditionRequest request) {
        // 1. ISBN으로 기존 책 조회
        Optional<Book> existingBook = bookRepository.findByIsbn(request.getIsbn());
        if (existingBook.isPresent()) {
            return existingBook.get();
        }
        
        // 2. ISBN으로 알라딘 API에서 책 정보 조회
        BookInfo bookInfo = getBookInfoFromAladin(request.getIsbn());
        
        // 3. 새 책 생성
        Book newBook = new Book(
            request.getIsbn(),
            bookInfo.getTitle(),
            bookInfo.getAuthor(),
            bookInfo.getPublisher()
        );
        
        // 4. 추가 정보 설정
        newBook.setDescription(bookInfo.getDescription());
        newBook.setCoverUrl(bookInfo.getCoverUrl());
        newBook.setTotalPages(bookInfo.getTotalPages());
        newBook.setMainGenre(bookInfo.getMainGenre());
        newBook.setPubDate(bookInfo.getPubDate());
        newBook.setCreatedAt(LocalDateTime.now());
        newBook.setUpdatedAt(LocalDateTime.now());
        
        return bookRepository.save(newBook);
    }
    
    /**
     * 알라딘 API에서 ISBN으로 책 정보 조회
     */
    private BookInfo getBookInfoFromAladin(String isbn) {
        // 알라딘 API 호출 로직 (간단한 구현)
        // 실제로는 AladinApiService를 사용해야 함
        BookInfo bookInfo = new BookInfo();
        bookInfo.setIsbn(isbn);
        bookInfo.setTitle("책 제목 (알라딘 API에서 조회)");
        bookInfo.setAuthor("저자명 (알라딘 API에서 조회)");
        bookInfo.setPublisher("출판사명 (알라딘 API에서 조회)");
        bookInfo.setDescription("책 설명 (알라딘 API에서 조회)");
        bookInfo.setCoverUrl("표지 URL (알라딘 API에서 조회)");
        bookInfo.setTotalPages(300);
        bookInfo.setMainGenre("소설");
        bookInfo.setPubDate(LocalDateTime.now().toLocalDate());
        
        return bookInfo;
    }
    
    /**
     * 책 정보 DTO (내부용)
     */
    private static class BookInfo {
        private String isbn;
        private String title;
        private String author;
        private String publisher;
        private String description;
        private String coverUrl;
        private Integer totalPages;
        private String mainGenre;
        private java.time.LocalDate pubDate;
        
        // Getters and Setters
        public String getIsbn() { return isbn; }
        public void setIsbn(String isbn) { this.isbn = isbn; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public Integer getTotalPages() { return totalPages; }
        public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
        public String getMainGenre() { return mainGenre; }
        public void setMainGenre(String mainGenre) { this.mainGenre = mainGenre; }
        public java.time.LocalDate getPubDate() { return pubDate; }
        public void setPubDate(java.time.LocalDate pubDate) { this.pubDate = pubDate; }
    }
}