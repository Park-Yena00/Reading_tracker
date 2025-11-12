package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSortCriteria;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.BookAdditionRequest;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.BookDetailUpdateRequest;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.StartReadingRequest;
import com.readingtracker.server.dto.clientserverDTO.requestDTO.FinishReadingRequest;
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
        // 명시적으로 카테고리를 선택했으므로 플래그 설정
        userBook.setCategoryManuallySet(true);
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
                //다른 필드들은 자동으로 무시됨
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
                
                // 진행률 자동 설정: Finished 카테고리는 항상 100% (전체 페이지 수)
                Integer bookTotalPages = userBook.getBook().getTotalPages();
                if (bookTotalPages != null && bookTotalPages > 0) {
                    userBook.setReadingProgress(bookTotalPages);  // 전체 페이지 수 = 100%
                } else {
                    // 전체 페이지 수가 없는 경우, 사용자가 직접 입력한 진행률 값이 있으면 사용
                    if (request.getReadingProgress() != null) {
                        userBook.setReadingProgress(request.getReadingProgress());
                    } else {
                        throw new IllegalArgumentException("Finished 카테고리에는 전체 페이지 수 또는 진행률이 필요합니다.");
                    }
                }
                
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
     * 책 완독 처리 (AlmostFinished → Finished)
     */
    public UserShelfBook finishReading(String loginId, Long userBookId, FinishReadingRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. UserBook 조회 및 소유권 확인
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        BookCategory currentCategory = userBook.getCategory();
        if (currentCategory != BookCategory.Reading && currentCategory != BookCategory.AlmostFinished) {
            throw new IllegalArgumentException("현재 책 상태에서는 '완독' 처리를 할 수 없습니다.");
        }
        
        LocalDate readingFinishedDate = request.getReadingFinishedDate();
        if (readingFinishedDate == null) {
            throw new IllegalArgumentException("독서 종료일은 필수 입력 항목입니다.");
        }
        LocalDate readingStartDate = userBook.getReadingStartDate();
        if (readingStartDate != null && readingFinishedDate.isBefore(readingStartDate)) {
            throw new IllegalArgumentException("독서 종료일은 독서 시작일 이후여야 합니다.");
        }
        
        // 진행률을 전체 페이지 수로 맞추기
        Integer totalPages = userBook.getBook() != null ? userBook.getBook().getTotalPages() : null;
        if (totalPages != null && totalPages > 0) {
            userBook.setReadingProgress(totalPages);
        } else if (userBook.getReadingProgress() == null) {
            // 전체 페이지 수가 없고 진행률 정보가 없다면 0으로 설정 (완독 상태에서 최소값 보장)
            userBook.setReadingProgress(0);
        }
        
        userBook.setCategory(BookCategory.Finished);
        userBook.setCategoryManuallySet(true);
        userBook.setReadingFinishedDate(readingFinishedDate);
        userBook.setRating(request.getRating());
        userBook.setReview(request.getReview());
        userBook.setUpdatedAt(LocalDateTime.now());
        
        return userBookRepository.save(userBook);
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
     * 책 읽기 시작 (ToRead → Reading)
     */
    public UserShelfBook startReading(String loginId, Long userBookId, StartReadingRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 2. UserBook 조회 및 소유권 확인
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        if (userBook.getCategory() != BookCategory.ToRead) {
            throw new IllegalArgumentException("현재 책 상태에서는 '책 읽기 시작'을 할 수 없습니다.");
        }
        
        LocalDate readingStartDate = request.getReadingStartDate();
        Integer readingProgress = request.getReadingProgress();
        if (readingStartDate == null) {
            throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
        }
        if (readingProgress == null) {
            throw new IllegalArgumentException("현재 읽은 페이지 수는 필수 입력 항목입니다.");
        }
        if (readingProgress < 0) {
            throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
        }
        Integer totalPages = userBook.getBook() != null ? userBook.getBook().getTotalPages() : null;
        if (totalPages != null && readingProgress > totalPages) {
            throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages + ")를 초과할 수 없습니다.");
        }
        
        // ToRead → Reading 전환 처리
        userBook.setCategory(BookCategory.Reading);
        userBook.setCategoryManuallySet(true);
        userBook.setReadingStartDate(readingStartDate);
        userBook.setReadingProgress(readingProgress);
        if (request.getPurchaseType() != null) {
            userBook.setPurchaseType(request.getPurchaseType());
        }
        // 진행 중인 상태로 변경 시 완독 정보 초기화
        userBook.setReadingFinishedDate(null);
        userBook.setRating(null);
        userBook.setReview(null);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        return userBookRepository.save(userBook);
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
        
        // 3. 카테고리 변경 처리 (명시적 변경 시 플래그 설정)
        if (request.getCategory() != null && request.getCategory() != userBook.getCategory()) {
            // 명시적으로 카테고리를 변경하는 경우 (예: "독서 시작" 버튼 클릭)
            userBook.setCategory(request.getCategory());
            userBook.setCategoryManuallySet(true);
            
            // ToRead → Reading 변경 시 (독서 시작)
            if (request.getCategory() == BookCategory.Reading && 
                userBook.getReadingStartDate() == null) {
                // 독서 시작일이 없으면 오늘 날짜로 설정
                userBook.setReadingStartDate(LocalDate.now());
            }
            
            // ToRead → Reading 변경 시 진행률이 없으면 0으로 설정
            if (request.getCategory() == BookCategory.Reading && 
                userBook.getReadingProgress() == null) {
                userBook.setReadingProgress(0);
            }
        }
        
        // 4. 현재 카테고리에 따라 입력값 업데이트 (기존 값은 유지)
        updateCategorySpecificFields(userBook, request);
        
        // 5. 진행률 기반 자동 카테고리 변경 (Reading, AlmostFinished 카테고리에서 진행률 업데이트 시)
        if (request.getReadingProgress() != null && 
            (userBook.getCategory() == BookCategory.Reading || userBook.getCategory() == BookCategory.AlmostFinished)) {
            autoUpdateCategoryByProgress(userBook);
        }
        
        // 6. 업데이트 시간 갱신
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
                    // 진행률 기반 자동 카테고리 변경은 updateBookDetail에서 처리
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
                    // 진행률 기반 자동 카테고리 변경은 updateBookDetail에서 처리
                }
                
                // 구매/대여 여부 (선택사항) - 입력된 경우에만 업데이트
                if (request.getPurchaseType() != null) {
                    userBook.setPurchaseType(request.getPurchaseType());
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
    
    /**
     * 진행률 기반 자동 카테고리 변경
     * Reading 또는 AlmostFinished 카테고리에서 진행률이 업데이트될 때 자동으로 카테고리 변경
     * 명시적 카테고리 변경 플래그를 고려하여 변경 여부 결정
     * 
     * @param userBook 변경할 UserShelfBook 엔티티
     */
    private void autoUpdateCategoryByProgress(UserShelfBook userBook) {
        Integer readingProgress = userBook.getReadingProgress();
        Integer totalPages = userBook.getBook().getTotalPages();
        
        // 진행률이나 전체 페이지 수가 없으면 자동 변경하지 않음
        if (readingProgress == null || totalPages == null || totalPages == 0) {
            return;
        }
        
        // 진행률 계산
        Integer progressPercentage = calculateProgressPercentage(readingProgress, totalPages);
        
        // 진행률 기반 카테고리 결정
        BookCategory newCategory = determineCategoryByProgress(progressPercentage);
        BookCategory currentCategory = userBook.getCategory();
        
        // 명시적 카테고리 변경 플래그 확인
        if (userBook.isCategoryManuallySet() != null && userBook.isCategoryManuallySet()) {
            // 명시적으로 카테고리를 변경한 경우
            
            // 1. 진행률이 0%이고 현재 Reading 상태면 변경하지 않음
            // (독서 시작 버튼을 눌러 Reading으로 변경했지만 아직 읽지 않은 경우)
            if (progressPercentage == 0 && currentCategory == BookCategory.Reading) {
                return;  // Reading 상태 유지
            }
            
            // 2. 진행률이 70% 이상 99% 이하면 AlmostFinished로 자동 변경 허용
            if (progressPercentage >= 70 && progressPercentage < 100) {
                if (newCategory == BookCategory.AlmostFinished && 
                    currentCategory != BookCategory.AlmostFinished) {
                    userBook.setCategory(newCategory);
                    // 플래그는 유지 (명시적 변경 기록 보존)
                    return;
                }
            }
            
            // 3. 진행률이 100%면 Finished로 자동 변경 허용
            if (progressPercentage == 100) {
                if (newCategory == BookCategory.Finished && 
                    currentCategory != BookCategory.Finished) {
                    userBook.setCategory(newCategory);
                    // 플래그는 유지 (명시적 변경 기록 보존)
                    return;
                }
            }
            
            // 4. 그 외의 경우는 자동 변경하지 않음 (명시적 설정 우선)
            return;
        }
        
        // 플래그가 false이거나 null인 경우 (자동으로 설정된 카테고리)
        // 자유롭게 자동 변경 허용
        if (currentCategory != newCategory) {
            userBook.setCategory(newCategory);
            // 자동 변경이므로 플래그는 false 유지
        }
    }
    
    /**
     * 진행률 퍼센티지 계산
     * 계산식: (현재 페이지 수 / 전체 분량) × 100
     * 
     * @param readingProgress 현재 읽은 페이지 수
     * @param totalPages 전체 페이지 수
     * @return 0~100 사이의 정수 (퍼센티지)
     */
    private Integer calculateProgressPercentage(Integer readingProgress, Integer totalPages) {
        if (readingProgress == null || totalPages == null || totalPages == 0) {
            return 0;
        }
        
        // 정수 나눗셈 후 반올림
        return (int) Math.round((readingProgress * 100.0) / totalPages);
    }
    
    /**
     * 진행률 기반 카테고리 결정
     * 
     * @param progressPercentage 진행률 (0~100)
     * @return 결정된 BookCategory
     */
    private BookCategory determineCategoryByProgress(Integer progressPercentage) {
        if (progressPercentage == null) {
            return BookCategory.ToRead;
        }
        
        if (progressPercentage == 0) {
            return BookCategory.ToRead;
        } else if (progressPercentage >= 1 && progressPercentage <= 69) {
            return BookCategory.Reading;
        } else if (progressPercentage >= 70 && progressPercentage <= 99) {
            return BookCategory.AlmostFinished;
        } else if (progressPercentage == 100) {
            return BookCategory.Finished;
        }
        
        // 진행률이 100%를 초과하는 경우는 Finished로 처리 (데이터 검증은 이미 완료)
        return BookCategory.Finished;
    }
}