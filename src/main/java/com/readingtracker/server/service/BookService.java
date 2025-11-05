package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSortCriteria;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.BookAdditionRequest;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.BookDetailUpdateRequest;
import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.BookRepository;
import com.readingtracker.dbms.repository.UserShelfBookRepository;
import com.readingtracker.dbms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        
        // 2. ISBN으로 Book 테이블에 이미 존재하는지 확인
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            throw new IllegalArgumentException("이미 Book 테이블에 존재하는 ISBN입니다. 직접 등록할 수 없습니다.");
        }
        
        // 3. 직접 입력한 정보로 Book 생성 (알라딘 API 호출 없음)
        Book book = createBookFromRequest(request);
        
        // 4. 중복 추가 방지 체크
        Optional<UserShelfBook> existingBook = userBookRepository.findByUserIdAndBookId(user.getId(), book.getId());
        if (existingBook.isPresent()) {
            throw new IllegalArgumentException("이미 내 서재에 추가된 책입니다.");
        }
        
        // 5. UserBook 생성 및 카테고리별 입력값 설정
        UserShelfBook userBook = new UserShelfBook(user, book, request.getCategory());
        setCategorySpecificFields(userBook, request);
        
        return userBookRepository.save(userBook);
    }
    
    /**
     * 카테고리별 입력값 설정
     */
    private void setCategorySpecificFields(UserShelfBook userBook, BookAdditionRequest request) {
        BookCategory category = request.getCategory();
        
        switch (category) {
            case ToRead:
                // 기대평 (선택사항)
                if (request.getExpectation() != null && !request.getExpectation().trim().isEmpty()) {
                    if (request.getExpectation().length() > 500) {
                        throw new IllegalArgumentException("기대평은 500자 이하여야 합니다.");
                    }
                    userBook.setExpectation(request.getExpectation());
                }
                break;
                
            case Reading:
                // 독서 시작일 (필수)
                if (request.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                userBook.setReadingStartDate(request.getReadingStartDate());
                
                // 현재 읽은 페이지 수 (필수)
                if (request.getReadingProgress() == null) {
                    throw new IllegalArgumentException("현재 읽은 페이지 수는 필수 입력 항목입니다.");
                }
                // 전체 페이지 수와 비교 검증
                Integer totalPages = userBook.getBook().getTotalPages();
                if (totalPages != null && request.getReadingProgress() > totalPages) {
                    throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages + ")를 초과할 수 없습니다.");
                }
                if (request.getReadingProgress() < 0) {
                    throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                }
                userBook.setReadingProgress(request.getReadingProgress());
                
                // 구매/대여 여부 (선택사항)
                if (request.getPurchaseType() != null) {
                    userBook.setPurchaseType(request.getPurchaseType());
                }
                break;
                
            case AlmostFinished:
                // 독서 시작일 (필수)
                if (request.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                userBook.setReadingStartDate(request.getReadingStartDate());
                
                // 현재 읽은 페이지 수 (필수)
                if (request.getReadingProgress() == null) {
                    throw new IllegalArgumentException("현재 읽은 페이지 수는 필수 입력 항목입니다.");
                }
                // 전체 페이지 수와 비교 검증
                Integer totalPages2 = userBook.getBook().getTotalPages();
                if (totalPages2 != null && request.getReadingProgress() > totalPages2) {
                    throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages2 + ")를 초과할 수 없습니다.");
                }
                if (request.getReadingProgress() < 0) {
                    throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                }
                userBook.setReadingProgress(request.getReadingProgress());
                break;
                
            case Finished:
                // 독서 시작일 (필수)
                if (request.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                userBook.setReadingStartDate(request.getReadingStartDate());
                
                // 독서 종료일 (필수)
                if (request.getReadingFinishedDate() == null) {
                    throw new IllegalArgumentException("독서 종료일은 필수 입력 항목입니다.");
                }
                // 독서 종료일이 독서 시작일 이후인지 검증
                if (request.getReadingFinishedDate().isBefore(request.getReadingStartDate())) {
                    throw new IllegalArgumentException("독서 종료일은 독서 시작일 이후여야 합니다.");
                }
                userBook.setReadingFinishedDate(request.getReadingFinishedDate());
                
                // 평점 (필수, 1~5)
                if (request.getRating() == null) {
                    throw new IllegalArgumentException("평점은 필수 입력 항목입니다.");
                }
                if (request.getRating() < 1 || request.getRating() > 5) {
                    throw new IllegalArgumentException("평점은 1 이상 5 이하여야 합니다.");
                }
                userBook.setRating(request.getRating());
                
                // 후기 (선택사항)
                if (request.getReview() != null && !request.getReview().trim().isEmpty()) {
                    userBook.setReview(request.getReview());
                }
                break;
        }
    }
    
    /**
     * 내 서재 조회
     */
    @Transactional(readOnly = true)
    public List<UserShelfBook> getMyShelf(String loginId, BookCategory category, BookSortCriteria sortBy) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. 정렬 기준이 지정되지 않은 경우 기본값은 도서명 오름차순
        if (sortBy == null) {
            sortBy = BookSortCriteria.TITLE;
        }
        
        // 3. 카테고리와 정렬 기준에 따라 내 서재 조회
        if (category != null) {
            return getMyShelfByCategoryAndSort(user.getId(), category, sortBy);
        } else {
            return getMyShelfBySort(user.getId(), sortBy);
        }
    }
    
    /**
     * 카테고리별 정렬된 내 서재 조회
     */
    private List<UserShelfBook> getMyShelfByCategoryAndSort(Long userId, BookCategory category, BookSortCriteria sortBy) {
        switch (sortBy) {
            case TITLE:
                return userBookRepository.findByUserIdAndCategoryOrderByTitleAsc(userId, category);
            case AUTHOR:
                return userBookRepository.findByUserIdAndCategoryOrderByAuthorAsc(userId, category);
            case PUBLISHER:
                return userBookRepository.findByUserIdAndCategoryOrderByPublisherAsc(userId, category);
            case GENRE:
                return userBookRepository.findByUserIdAndCategoryOrderByGenreAsc(userId, category);
            default:
                return userBookRepository.findByUserIdAndCategoryOrderByTitleAsc(userId, category);
        }
    }
    
    /**
     * 정렬된 내 서재 조회 (카테고리 없음)
     */
    private List<UserShelfBook> getMyShelfBySort(Long userId, BookSortCriteria sortBy) {
        switch (sortBy) {
            case TITLE:
                return userBookRepository.findByUserIdOrderByTitleAsc(userId);
            case AUTHOR:
                return userBookRepository.findByUserIdOrderByAuthorAsc(userId);
            case PUBLISHER:
                return userBookRepository.findByUserIdOrderByPublisherAsc(userId);
            case GENRE:
                return userBookRepository.findByUserIdOrderByGenreAsc(userId);
            default:
                return userBookRepository.findByUserIdOrderByTitleAsc(userId);
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
     * 책 상세 정보 변경
     * 카테고리별 입력값에 따라 기존 값은 유지하고, 새 값만 업데이트
     */
    public UserShelfBook updateBookDetail(String loginId, Long userBookId, BookDetailUpdateRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. UserBook 조회 및 소유권 확인
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 3. 현재 카테고리에 따라 입력값 업데이트 (기존 값은 유지)
        updateCategorySpecificFields(userBook, request);
        
        // 4. 업데이트 시간 갱신
        userBook.setUpdatedAt(LocalDateTime.now());
        
        return userBookRepository.save(userBook);
    }
    
    /**
     * 카테고리별 입력값 업데이트
     * 기존 값은 유지하고, 새로 입력된 값만 업데이트
     */
    private void updateCategorySpecificFields(UserShelfBook userBook, BookDetailUpdateRequest request) {
        BookCategory category = userBook.getCategory();
        
        switch (category) {
            case ToRead:
                // 기대평 (선택사항) - 입력된 경우에만 업데이트
                if (request.getExpectation() != null) {
                    if (request.getExpectation().length() > 500) {
                        throw new IllegalArgumentException("기대평은 500자 이하여야 합니다.");
                    }
                    userBook.setExpectation(request.getExpectation());
                }
                break;
                
            case Reading:
                // 독서 시작일 (필수) - 입력된 경우에만 업데이트
                if (request.getReadingStartDate() != null) {
                    userBook.setReadingStartDate(request.getReadingStartDate());
                }
                
                // 현재 읽은 페이지 수 (필수) - 입력된 경우에만 업데이트
                if (request.getReadingProgress() != null) {
                    // 전체 페이지 수와 비교 검증
                    Integer totalPages = userBook.getBook().getTotalPages();
                    if (totalPages != null && request.getReadingProgress() > totalPages) {
                        throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages + ")를 초과할 수 없습니다.");
                    }
                    if (request.getReadingProgress() < 0) {
                        throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                    }
                    userBook.setReadingProgress(request.getReadingProgress());
                }
                
                // 구매/대여 여부 (선택사항) - 입력된 경우에만 업데이트
                if (request.getPurchaseType() != null) {
                    userBook.setPurchaseType(request.getPurchaseType());
                }
                break;
                
            case AlmostFinished:
                // 독서 시작일 (필수) - 입력된 경우에만 업데이트
                if (request.getReadingStartDate() != null) {
                    userBook.setReadingStartDate(request.getReadingStartDate());
                }
                
                // 현재 읽은 페이지 수 (필수) - 입력된 경우에만 업데이트
                if (request.getReadingProgress() != null) {
                    // 전체 페이지 수와 비교 검증
                    Integer totalPages = userBook.getBook().getTotalPages();
                    if (totalPages != null && request.getReadingProgress() > totalPages) {
                        throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages + ")를 초과할 수 없습니다.");
                    }
                    if (request.getReadingProgress() < 0) {
                        throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                    }
                    userBook.setReadingProgress(request.getReadingProgress());
                }
                break;
                
            case Finished:
                // 독서 시작일 - 입력된 경우에만 업데이트
                if (request.getReadingStartDate() != null) {
                    userBook.setReadingStartDate(request.getReadingStartDate());
                }
                
                // 독서 종료일 - 입력된 경우에만 업데이트
                if (request.getReadingFinishedDate() != null) {
                    // 독서 종료일이 독서 시작일 이후인지 검증
                    LocalDate readingStartDate = userBook.getReadingStartDate();
                    if (readingStartDate != null && request.getReadingFinishedDate().isBefore(readingStartDate)) {
                        throw new IllegalArgumentException("독서 종료일은 독서 시작일 이후여야 합니다.");
                    }
                    userBook.setReadingFinishedDate(request.getReadingFinishedDate());
                }
                
                // 평점 - 입력된 경우에만 업데이트
                if (request.getRating() != null) {
                    if (request.getRating() < 1 || request.getRating() > 5) {
                        throw new IllegalArgumentException("평점은 1 이상 5 이하여야 합니다.");
                    }
                    userBook.setRating(request.getRating());
                }
                
                // 후기 (선택사항) - 입력된 경우에만 업데이트
                if (request.getReview() != null) {
                    userBook.setReview(request.getReview());
                }
                break;
        }
    }
    
    /**
     * 직접 입력한 정보로 Book 생성
     */
    private Book createBookFromRequest(BookAdditionRequest request) {
        // 직접 입력한 정보로 새 책 생성
        Book newBook = new Book(
            request.getIsbn(),
            request.getTitle(),
            request.getAuthor(),
            request.getPublisher()
        );
        
        // 추가 정보 설정 (선택사항)
        if (request.getDescription() != null) {
            newBook.setDescription(request.getDescription());
        }
        if (request.getCoverUrl() != null) {
            newBook.setCoverUrl(request.getCoverUrl());
        }
        if (request.getTotalPages() != null) {
            newBook.setTotalPages(request.getTotalPages());
        }
        if (request.getMainGenre() != null) {
            newBook.setMainGenre(request.getMainGenre());
        }
        if (request.getPubDate() != null) {
            newBook.setPubDate(request.getPubDate());
        }
        
        newBook.setCreatedAt(LocalDateTime.now());
        newBook.setUpdatedAt(LocalDateTime.now());
        
        return bookRepository.save(newBook);
    }
}