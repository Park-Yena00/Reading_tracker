package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSortCriteria;
import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.BookRepository;
import com.readingtracker.dbms.repository.UserShelfBookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private UserShelfBookRepository userBookRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String CACHE_KEY_PREFIX = "my_shelf:user:";
    private static final long TTL_MINUTES = 5; // 5분 (안전망 역할)
    
    /**
     * 내 서재에 책 추가
     * 문서: MAPSTRUCT_ARCHITECTURE_DESIGN.md 준수 - Entity만 받음
     */
    public UserShelfBook addBookToShelf(UserShelfBook userShelfBook) {
        // 1. ISBN으로 Book 테이블에 이미 존재하는지 확인
        // books 테이블에 ISBN이 존재하면 해당 Book을 재사용하고, 없으면 새로 생성
        Book book = userShelfBook.getBook();
        if (book == null) {
            throw new IllegalArgumentException("Book 정보가 없습니다.");
        }
        
        Book savedBook;
        if (book.getId() != null) {
            // 이미 ID가 있는 경우 (기존 Book)
            savedBook = book;
        } else {
            // ISBN으로 기존 Book 조회
            Optional<Book> existingBook = bookRepository.findByIsbn(book.getIsbn());
            if (existingBook.isPresent()) {
                // 기존 Book이 있으면 재사용
                savedBook = existingBook.get();
            } else {
                // 기존 Book이 없으면 새로 생성
                savedBook = bookRepository.save(book);
            }
        }
        
        // 2. UserShelfBook에 Book 설정
        userShelfBook.setBook(savedBook);
        
        // 3. 중복 추가 방지 체크 (user_books 테이블에서 해당 사용자의 중복 확인)
        Optional<UserShelfBook> existingUserBook = userBookRepository.findByUserIdAndBookId(
            userShelfBook.getUserId(), savedBook.getId());
        if (existingUserBook.isPresent()) {
            throw new IllegalArgumentException("이미 내 서재에 추가된 책입니다.");
        }
        
        // 4. 카테고리별 입력값 검증
        validateCategorySpecificFields(userShelfBook);
        
        // 5. UserShelfBook 저장
        UserShelfBook savedUserShelfBook = userBookRepository.save(userShelfBook);
        
        // 6. 내 서재 캐시 무효화 (Write-Through 패턴)
        invalidateMyShelfCache(userShelfBook.getUserId());
        
        return savedUserShelfBook;
    }
    
    /**
     * 카테고리별 입력값 검증
     */
    private void validateCategorySpecificFields(UserShelfBook userBook) {
        BookCategory category = userBook.getCategory();
        
        switch (category) {
            case ToRead:
                // 기대평 (선택사항) - 길이 검증만
                if (userBook.getExpectation() != null && userBook.getExpectation().length() > 500) {
                    throw new IllegalArgumentException("기대평은 500자 이하여야 합니다.");
                }
                break;
                
            case Reading:
                // 독서 시작일 (필수)
                if (userBook.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                
                // 현재 읽은 페이지 수 (필수)
                if (userBook.getReadingProgress() == null) {
                    throw new IllegalArgumentException("현재 읽은 페이지 수는 필수 입력 항목입니다.");
                }
                // 전체 페이지 수와 비교 검증
                Integer totalPages = userBook.getBook() != null ? userBook.getBook().getTotalPages() : null;
                if (totalPages != null && userBook.getReadingProgress() > totalPages) {
                    throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages + ")를 초과할 수 없습니다.");
                }
                if (userBook.getReadingProgress() < 0) {
                    throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                }
                break;
                
            case AlmostFinished:
                // 독서 시작일 (필수)
                if (userBook.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                
                // 현재 읽은 페이지 수 (필수)
                if (userBook.getReadingProgress() == null) {
                    throw new IllegalArgumentException("현재 읽은 페이지 수는 필수 입력 항목입니다.");
                }
                // 전체 페이지 수와 비교 검증
                Integer totalPages2 = userBook.getBook() != null ? userBook.getBook().getTotalPages() : null;
                if (totalPages2 != null && userBook.getReadingProgress() > totalPages2) {
                    throw new IllegalArgumentException("읽은 페이지 수는 전체 페이지 수(" + totalPages2 + ")를 초과할 수 없습니다.");
                }
                if (userBook.getReadingProgress() < 0) {
                    throw new IllegalArgumentException("읽은 페이지 수는 0 이상이어야 합니다.");
                }
                break;
                
            case Finished:
                // 독서 시작일 (필수)
                if (userBook.getReadingStartDate() == null) {
                    throw new IllegalArgumentException("독서 시작일은 필수 입력 항목입니다.");
                }
                
                // 독서 종료일 (필수)
                if (userBook.getReadingFinishedDate() == null) {
                    throw new IllegalArgumentException("독서 종료일은 필수 입력 항목입니다.");
                }
                // 독서 종료일이 독서 시작일 이후인지 검증
                if (userBook.getReadingFinishedDate().isBefore(userBook.getReadingStartDate())) {
                    throw new IllegalArgumentException("독서 종료일은 독서 시작일 이후여야 합니다.");
                }
                
                // 진행률 자동 설정: Finished 카테고리는 항상 100% (전체 페이지 수)
                Integer bookTotalPages = userBook.getBook() != null ? userBook.getBook().getTotalPages() : null;
                if (bookTotalPages != null && bookTotalPages > 0) {
                    userBook.setReadingProgress(bookTotalPages);  // 전체 페이지 수 = 100%
                } else if (userBook.getReadingProgress() == null) {
                    throw new IllegalArgumentException("Finished 카테고리에는 전체 페이지 수 또는 진행률이 필요합니다.");
                }
                
                // 평점 (필수, 1~5)
                if (userBook.getRating() == null) {
                    throw new IllegalArgumentException("평점은 필수 입력 항목입니다.");
                }
                if (userBook.getRating() < 1 || userBook.getRating() > 5) {
                    throw new IllegalArgumentException("평점은 1 이상 5 이하여야 합니다.");
                }
                break;
        }
    }
    
    /**
     * 책 완독 처리 (AlmostFinished → Finished)
     * UserShelfBook Entity를 받아서 처리
     */
    public UserShelfBook finishReading(UserShelfBook userBook) {
        // 1. UserBook 조회 및 소유권 확인 (이미 조회된 Entity 사용)
        BookCategory currentCategory = userBook.getCategory();
        if (currentCategory != BookCategory.Reading && currentCategory != BookCategory.AlmostFinished) {
            throw new IllegalArgumentException("현재 책 상태에서는 '완독' 처리를 할 수 없습니다.");
        }
        
        LocalDate readingFinishedDate = userBook.getReadingFinishedDate();
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
        
        // 평점 검증
        if (userBook.getRating() == null) {
            throw new IllegalArgumentException("평점은 필수 입력 항목입니다.");
        }
        if (userBook.getRating() < 1 || userBook.getRating() > 5) {
            throw new IllegalArgumentException("평점은 1 이상 5 이하여야 합니다.");
        }
        
        userBook.setCategory(BookCategory.Finished);
        userBook.setCategoryManuallySet(true);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        UserShelfBook savedBook = userBookRepository.save(userBook);
        
        // 내 서재 캐시 무효화 (Write-Through 패턴)
        invalidateMyShelfCache(userBook.getUserId());
        
        return savedBook;
    }
    
    /**
     * 내 서재 조회
     * userId를 받아서 처리
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<UserShelfBook> getMyShelf(Long userId, BookCategory category, BookSortCriteria sortBy) {
        // 1. 정렬 기준이 지정되지 않은 경우 기본값은 도서명 오름차순
        if (sortBy == null) {
            sortBy = BookSortCriteria.TITLE;
        }
        
        // 2. 캐시 키 생성
        String cacheKey = buildCacheKey(userId, category, sortBy);
        
        // 3. Redis 캐시 확인
        List<UserShelfBook> cachedBooks = (List<UserShelfBook>) redisTemplate.opsForValue().get(cacheKey);
        if (cachedBooks != null) {
            return cachedBooks;
        }
        
        // 4. DB 조회
        List<UserShelfBook> books;
        if (category != null) {
            books = getMyShelfByCategoryAndSort(userId, category, sortBy);
        } else {
            books = getMyShelfBySort(userId, sortBy);
        }
        
        // 5. Redis에 저장 (TTL: 5분)
        redisTemplate.opsForValue().set(cacheKey, books, TTL_MINUTES, TimeUnit.MINUTES);
        
        return books;
    }
    
    /**
     * 캐시 키 생성
     */
    private String buildCacheKey(Long userId, BookCategory category, BookSortCriteria sortBy) {
        if (category != null) {
            return CACHE_KEY_PREFIX + userId + ":category:" + category.name() + ":sort:" + sortBy.name();
        } else {
            return CACHE_KEY_PREFIX + userId + ":sort:" + sortBy.name();
        }
    }
    
    /**
     * 사용자의 모든 내 서재 캐시 무효화 (Write-Through 패턴)
     */
    private void invalidateMyShelfCache(Long userId) {
        String pattern = CACHE_KEY_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
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
     * UserShelfBook Entity를 받아서 처리
     */
    public void removeBookFromShelf(UserShelfBook userBook) {
        // 소유권 확인은 Controller에서 이미 완료된 것으로 가정
        Long userId = userBook.getUserId();
        
        // Entity만 받아서 삭제 처리
        userBookRepository.delete(userBook);
        
        // 내 서재 캐시 무효화 (Write-Through 패턴)
        invalidateMyShelfCache(userId);
    }
    
    /**
     * 책 읽기 상태 변경
     * UserShelfBook Entity를 받아서 처리
     */
    public void updateBookCategory(UserShelfBook userBook, BookCategory category) {
        // 소유권 확인은 Controller에서 이미 완료된 것으로 가정
        Long userId = userBook.getUserId();
        
        // Entity만 받아서 카테고리 변경 처리
        userBook.setCategory(category);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        userBookRepository.save(userBook);
        
        // 내 서재 캐시 무효화 (Write-Through 패턴)
        invalidateMyShelfCache(userId);
    }
    
    /**
     * 책 읽기 시작 (ToRead → Reading)
     * UserShelfBook Entity를 받아서 처리
     */
    public UserShelfBook startReading(UserShelfBook userBook) {
        // 1. 카테고리 확인
        if (userBook.getCategory() != BookCategory.ToRead) {
            throw new IllegalArgumentException("현재 책 상태에서는 '책 읽기 시작'을 할 수 없습니다.");
        }
        
        // 2. 필수 필드 검증
        LocalDate readingStartDate = userBook.getReadingStartDate();
        Integer readingProgress = userBook.getReadingProgress();
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
        
        // 3. ToRead → Reading 전환 처리
        userBook.setCategory(BookCategory.Reading);
        userBook.setCategoryManuallySet(true);
        // 진행 중인 상태로 변경 시 완독 정보 초기화
        userBook.setReadingFinishedDate(null);
        userBook.setRating(null);
        userBook.setReview(null);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        return userBookRepository.save(userBook);
    }
    
    /**
     * 책 상세 정보 변경
     * UserShelfBook Entity를 받아서 처리
     * 카테고리별 입력값에 따라 기존 값은 유지하고, 새 값만 업데이트
     */
    public UserShelfBook updateBookDetail(UserShelfBook userBook) {
        // 1. 카테고리별 입력값 검증
        validateCategorySpecificFields(userBook);
        
        // 2. 진행률 기반 자동 카테고리 변경 (Reading, AlmostFinished 카테고리에서 진행률 업데이트 시)
        if (userBook.getReadingProgress() != null && 
            (userBook.getCategory() == BookCategory.Reading || userBook.getCategory() == BookCategory.AlmostFinished)) {
            autoUpdateCategoryByProgress(userBook);
        }
        
        // 3. 업데이트 시간 갱신
        userBook.setUpdatedAt(LocalDateTime.now());
        
        UserShelfBook savedBook = userBookRepository.save(userBook);
        
        // 내 서재 캐시 무효화 (Write-Through 패턴)
        invalidateMyShelfCache(userBook.getUserId());
        
        return savedBook;
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
        
        // Finished 카테고리에서는 다른 카테고리로 자동 변경하지 않음
        BookCategory currentCategory = userBook.getCategory();
        if (currentCategory == BookCategory.Finished) {
            return;
        }
        
        // 진행률 계산
        Integer progressPercentage = calculateProgressPercentage(readingProgress, totalPages);
        
        // 진행률 기반 카테고리 결정
        BookCategory newCategory = determineCategoryByProgress(progressPercentage);
        
        // 명시적 카테고리 변경 플래그 확인
        if (userBook.isCategoryManuallySet() != null && userBook.isCategoryManuallySet()) {
            // 명시적으로 카테고리를 변경한 경우
            
            // 1. 진행률이 0%이고 현재 Reading 상태면 변경하지 않음
            // (독서 시작 버튼을 눌러 Reading으로 변경했지만 아직 읽지 않은 경우)
            if (progressPercentage == 0 && currentCategory == BookCategory.Reading) {
                return;  // Reading 상태 유지
            }
            
            // 2. 진행률이 1% 이상 69% 이하면 Reading으로 자동 변경 허용
            // (AlmostFinished에서 진행률이 낮아진 경우 Reading으로 변경)
            if (progressPercentage >= 1 && progressPercentage <= 69) {
                if (newCategory == BookCategory.Reading) {
                    // AlmostFinished나 Reading에서 진행률이 낮아져서 Reading으로 변경되는 경우
                    if (currentCategory == BookCategory.AlmostFinished || currentCategory == BookCategory.Reading) {
                        userBook.setCategory(newCategory);
                        // 플래그는 유지 (명시적 변경 기록 보존)
                        return;
                    }
                }
            }
            
            // 3. 진행률이 70% 이상 99% 이하면 AlmostFinished로 자동 변경 허용
            if (progressPercentage >= 70 && progressPercentage < 100) {
                if (newCategory == BookCategory.AlmostFinished && 
                    currentCategory != BookCategory.AlmostFinished) {
                    userBook.setCategory(newCategory);
                    // 플래그는 유지 (명시적 변경 기록 보존)
                    return;
                }
            }
            
            // 4. 현재 읽은 페이지 수가 전체 페이지 수와 정확히 같을 때만 Finished로 자동 변경 허용
            // (진행률이 100%이고, readingProgress == totalPages인 경우만)
            if (readingProgress.equals(totalPages)) {
                if (newCategory == BookCategory.Finished && 
                    currentCategory != BookCategory.Finished) {
                    userBook.setCategory(newCategory);
                    // 플래그는 유지 (명시적 변경 기록 보존)
                    return;
                }
            }
            
            // 5. 그 외의 경우는 자동 변경하지 않음 (명시적 설정 우선)
            return;
        }
        
        // 플래그가 false이거나 null인 경우 (자동으로 설정된 카테고리)
        // 자유롭게 자동 변경 허용 (단, Finished로 변경은 현재 읽은 페이지 수가 전체 페이지 수와 정확히 같을 때만)
        if (currentCategory != newCategory) {
            // Finished로 변경하려면 현재 읽은 페이지 수가 전체 페이지 수와 정확히 같아야 함
            if (newCategory == BookCategory.Finished) {
                if (readingProgress.equals(totalPages)) {
                    userBook.setCategory(newCategory);
                    // 자동 변경이므로 플래그는 false 유지
                }
            } else {
                // Finished가 아닌 다른 카테고리로 변경은 자유롭게 허용
                userBook.setCategory(newCategory);
                // 자동 변경이므로 플래그는 false 유지
            }
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