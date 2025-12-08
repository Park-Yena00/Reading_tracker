package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSortCriteria;
import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.primary.BookRepository;
import com.readingtracker.dbms.repository.primary.UserShelfBookRepository;
import com.readingtracker.server.service.write.DualMasterWriteService;
import com.readingtracker.server.dto.UserShelfBookCacheDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
// @Transactional 제거 (Dual Write를 위해)
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private UserShelfBookRepository userBookRepository;
    
    @Autowired
    private com.readingtracker.dbms.repository.primary.UserRepository userRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private DualMasterWriteService dualMasterWriteService;
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    @Autowired
    private com.readingtracker.server.service.recovery.RecoveryQueueService recoveryQueueService;
    
    @Autowired
    private com.readingtracker.server.service.read.DualMasterReadService dualMasterReadService;
    
    @Autowired
    private com.readingtracker.server.mapper.BookMapper bookMapper;
    
    private static final String CACHE_KEY_PREFIX = "my_shelf:user:";
    private static final long TTL_MINUTES = 5; // 5분 (안전망 역할)
    
    /**
     * 내 서재에 책 추가
     * 문서: MAPSTRUCT_ARCHITECTURE_DESIGN.md 준수 - Entity만 받음
     * 
     * Dual Write 적용: 복잡한 비즈니스 로직을 원자적 단위로 분리
     * 1. Book 조회/생성 (원자적 단위 1)
     * 2. UserShelfBook 저장 (원자적 단위 2, Dual Write 적용)
     * 3. Redis 캐시 무효화 (부수 효과, DB 트랜잭션 외부)
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
                // 기존 Book이 없으면 새로 생성 (Dual Write 적용)
                savedBook = saveBookWithDualWrite(book);
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
        
        // 5. UserShelfBook 저장 (Dual Write 적용)
        UserShelfBook savedUserShelfBook = saveUserShelfBookWithDualWrite(userShelfBook);
        
        // 6. 내 서재 캐시 무효화 (부수 효과, DB 트랜잭션 외부로 분리)
        invalidateMyShelfCache(userShelfBook.getUserId());
        
        return savedUserShelfBook;
    }
    
    /**
     * Book 저장 (원자적 단위 1, Dual Write 적용)
     */
    private Book saveBookWithDualWrite(Book book) {
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> bookRepository.save(book),
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, savedBook) -> {
                // Book 테이블 INSERT (실제 컬럼명 사용)
                String insertBookSql = "INSERT INTO books (id, isbn, title, author, publisher, " +
                                     "total_pages, description, cover_url, main_genre, pub_date, created_at, updated_at) " +
                                     "VALUES (:id, :isbn, :title, :author, :publisher, " +
                                     ":totalPages, :description, :coverUrl, :mainGenre, :pubDate, :createdAt, :updatedAt)";
                
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> bookParams = new HashMap<>();
                bookParams.put("id", savedBook.getId());
                bookParams.put("isbn", savedBook.getIsbn());
                bookParams.put("title", savedBook.getTitle());
                bookParams.put("author", savedBook.getAuthor());
                bookParams.put("publisher", savedBook.getPublisher());
                bookParams.put("totalPages", savedBook.getTotalPages());
                bookParams.put("description", savedBook.getDescription());
                bookParams.put("coverUrl", savedBook.getCoverUrl());
                bookParams.put("mainGenre", savedBook.getMainGenre());
                bookParams.put("pubDate", savedBook.getPubDate());
                bookParams.put("createdAt", savedBook.getCreatedAt() != null ? savedBook.getCreatedAt() : now);
                bookParams.put("updatedAt", savedBook.getUpdatedAt() != null ? savedBook.getUpdatedAt() : now);
                
                secondaryNamedParameterJdbcTemplate.update(insertBookSql, bookParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 DELETE
            (savedBook) -> {
                if (savedBook != null && savedBook.getId() != null) {
                    bookRepository.deleteById(savedBook.getId());
                }
                return null;
            },
            "Book"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * UserShelfBook 저장 (원자적 단위 2, Dual Write 적용)
     */
    private UserShelfBook saveUserShelfBookWithDualWrite(UserShelfBook userShelfBook) {
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                userShelfBook.setUpdatedAt(LocalDateTime.now());
                return userBookRepository.save(userShelfBook);
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, savedUserShelfBook) -> {
                String insertUserShelfBookSql = "INSERT INTO user_books (id, user_id, book_id, category, " +
                                               "category_manually_set, expectation, reading_start_date, " +
                                               "reading_progress, purchase_type, reading_finished_date, " +
                                               "rating, review, created_at, updated_at) " +
                                               "VALUES (:id, :userId, :bookId, :category, " +
                                               ":categoryManuallySet, :expectation, :readingStartDate, " +
                                               ":readingProgress, :purchaseType, :readingFinishedDate, " +
                                               ":rating, :review, :createdAt, :updatedAt)";
                
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> params = new HashMap<>();
                params.put("id", savedUserShelfBook.getId());
                params.put("userId", savedUserShelfBook.getUserId());
                params.put("bookId", savedUserShelfBook.getBook() != null ? savedUserShelfBook.getBook().getId() : null);
                params.put("category", savedUserShelfBook.getCategory() != null ? savedUserShelfBook.getCategory().name() : null);
                params.put("categoryManuallySet", savedUserShelfBook.isCategoryManuallySet() != null ? savedUserShelfBook.isCategoryManuallySet() : false);
                params.put("expectation", savedUserShelfBook.getExpectation());
                params.put("readingStartDate", savedUserShelfBook.getReadingStartDate());
                params.put("readingProgress", savedUserShelfBook.getReadingProgress());
                params.put("purchaseType", savedUserShelfBook.getPurchaseType() != null ? savedUserShelfBook.getPurchaseType().name() : null);
                params.put("readingFinishedDate", savedUserShelfBook.getReadingFinishedDate());
                params.put("rating", savedUserShelfBook.getRating());
                params.put("review", savedUserShelfBook.getReview());
                params.put("createdAt", savedUserShelfBook.getCreatedAt() != null ? savedUserShelfBook.getCreatedAt() : now);
                params.put("updatedAt", savedUserShelfBook.getUpdatedAt() != null ? savedUserShelfBook.getUpdatedAt() : now);
                
                secondaryNamedParameterJdbcTemplate.update(insertUserShelfBookSql, params);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 DELETE
            (savedUserShelfBook) -> {
                if (savedUserShelfBook != null && savedUserShelfBook.getId() != null) {
                    userBookRepository.deleteById(savedUserShelfBook.getId());
                }
                return null;
            },
            "UserShelfBook"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * UserShelfBook 업데이트를 Dual Write로 처리하는 헬퍼 메서드
     * 
     * @param userShelfBook 업데이트할 UserShelfBook 엔티티
     * @return 업데이트된 UserShelfBook 엔티티
     */
    private UserShelfBook updateUserShelfBookWithDualWrite(UserShelfBook userShelfBook) {
        // 이전 상태 저장 (보상 트랜잭션용)
        UserShelfBook originalState = new UserShelfBook();
        originalState.setId(userShelfBook.getId());
        originalState.setCategory(userShelfBook.getCategory());
        originalState.setCategoryManuallySet(userShelfBook.isCategoryManuallySet());
        originalState.setExpectation(userShelfBook.getExpectation());
        originalState.setReadingStartDate(userShelfBook.getReadingStartDate());
        originalState.setReadingProgress(userShelfBook.getReadingProgress());
        originalState.setPurchaseType(userShelfBook.getPurchaseType());
        originalState.setReadingFinishedDate(userShelfBook.getReadingFinishedDate());
        originalState.setRating(userShelfBook.getRating());
        originalState.setReview(userShelfBook.getReview());
        originalState.setUpdatedAt(userShelfBook.getUpdatedAt());
        
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                userShelfBook.setUpdatedAt(LocalDateTime.now());
                return userBookRepository.save(userShelfBook);
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, updatedUserShelfBook) -> {
                String updateUserShelfBookSql = "UPDATE user_books SET category = :category, " +
                                               "category_manually_set = :categoryManuallySet, " +
                                               "expectation = :expectation, reading_start_date = :readingStartDate, " +
                                               "reading_progress = :readingProgress, purchase_type = :purchaseType, " +
                                               "reading_finished_date = :readingFinishedDate, rating = :rating, " +
                                               "review = :review, updated_at = :updatedAt " +
                                               "WHERE id = :id";
                
                Map<String, Object> updateParams = new HashMap<>();
                updateParams.put("id", updatedUserShelfBook.getId());
                updateParams.put("category", updatedUserShelfBook.getCategory() != null ? updatedUserShelfBook.getCategory().name() : null);
                updateParams.put("categoryManuallySet", updatedUserShelfBook.isCategoryManuallySet() != null ? updatedUserShelfBook.isCategoryManuallySet() : false);
                updateParams.put("expectation", updatedUserShelfBook.getExpectation());
                updateParams.put("readingStartDate", updatedUserShelfBook.getReadingStartDate());
                updateParams.put("readingProgress", updatedUserShelfBook.getReadingProgress());
                updateParams.put("purchaseType", updatedUserShelfBook.getPurchaseType() != null ? updatedUserShelfBook.getPurchaseType().name() : null);
                updateParams.put("readingFinishedDate", updatedUserShelfBook.getReadingFinishedDate());
                updateParams.put("rating", updatedUserShelfBook.getRating());
                updateParams.put("review", updatedUserShelfBook.getReview());
                updateParams.put("updatedAt", LocalDateTime.now());
                
                secondaryNamedParameterJdbcTemplate.update(updateUserShelfBookSql, updateParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 원래 상태로 복구
            (updatedUserShelfBook) -> {
                if (updatedUserShelfBook != null) {
                    userShelfBook.setCategory(originalState.getCategory());
                    userShelfBook.setCategoryManuallySet(originalState.isCategoryManuallySet());
                    userShelfBook.setExpectation(originalState.getExpectation());
                    userShelfBook.setReadingStartDate(originalState.getReadingStartDate());
                    userShelfBook.setReadingProgress(originalState.getReadingProgress());
                    userShelfBook.setPurchaseType(originalState.getPurchaseType());
                    userShelfBook.setReadingFinishedDate(originalState.getReadingFinishedDate());
                    userShelfBook.setRating(originalState.getRating());
                    userShelfBook.setReview(originalState.getReview());
                    userShelfBook.setUpdatedAt(originalState.getUpdatedAt());
                    userBookRepository.save(userShelfBook);
                }
                return null;
            },
            "UserShelfBook"  // 엔티티 타입 (Recovery Queue 발행용)
        );
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
        
        // Dual Write 적용
        UserShelfBook savedBook = updateUserShelfBookWithDualWrite(userBook);
        
        // 내 서재 캐시 무효화 (부수 효과, DB 트랜잭션 외부로 분리)
        invalidateMyShelfCache(userBook.getUserId());
        
        return savedBook;
    }
    
    /**
     * 내 서재 조회
     * userId를 받아서 처리
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     * Redis 캐싱: DTO 변환 후 저장하여 순환 참조 방지
     * 문서: MAPSTRUCT_ARCHITECTURE_DESIGN.md 준수 - DTO 변환은 Mapper가 담당
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<UserShelfBook> getMyShelf(Long userId, BookCategory category, BookSortCriteria sortBy) {
        // 1. 정렬 기준이 지정되지 않은 경우 기본값은 도서명 오름차순
        final BookSortCriteria finalSortBy = (sortBy != null) ? sortBy : BookSortCriteria.TITLE;
        
        // 2. 캐시 키 생성
        String cacheKey = buildCacheKey(userId, category, finalSortBy);
        
        // 3. Redis 캐시 확인 (DTO로 저장되어 있음)
        List<UserShelfBookCacheDTO> cachedDtos = (List<UserShelfBookCacheDTO>) redisTemplate.opsForValue().get(cacheKey);
        if (cachedDtos != null) {
            // Mapper를 통해 DTO를 엔티티로 변환하여 반환
            return bookMapper.toUserShelfBookEntityList(cachedDtos, userRepository);
        }
        
        // 4. DB 조회 (Dual Read Failover 적용)
        final BookCategory finalCategory = category; // effectively final
        List<UserShelfBook> books = dualMasterReadService.readWithFailover(() -> {
            if (finalCategory != null) {
                return getMyShelfByCategoryAndSort(userId, finalCategory, finalSortBy);
            } else {
                return getMyShelfBySort(userId, finalSortBy);
            }
        });
        
        // 5. Mapper를 통해 엔티티를 DTO로 변환하여 Redis에 저장 (TTL: 5분)
        List<UserShelfBookCacheDTO> dtos = bookMapper.toUserShelfBookCacheDTOList(books);
        redisTemplate.opsForValue().set(cacheKey, dtos, TTL_MINUTES, TimeUnit.MINUTES);
        
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
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void removeBookFromShelf(UserShelfBook userBook) {
        // 소유권 확인은 Controller에서 이미 완료된 것으로 가정
        Long userId = userBook.getUserId();
        Long userBookId = userBook.getId();
        
        // Dual Write 적용
        dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                userBookRepository.delete(userBook);
                return null;
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, result) -> {
                String deleteUserShelfBookSql = "DELETE FROM user_books WHERE id = :id";
                Map<String, Object> deleteParams = new HashMap<>();
                deleteParams.put("id", userBookId);
                secondaryNamedParameterJdbcTemplate.update(deleteUserShelfBookSql, deleteParams);
                return null;
            },
            
            // 보상 트랜잭션: DELETE의 보상은 복구가 어려우므로 Recovery Queue에 발행
            (result) -> {
                com.readingtracker.server.service.recovery.CompensationFailureEvent event = 
                    new com.readingtracker.server.service.recovery.CompensationFailureEvent(
                        "DELETE_SECONDARY_CLEANUP",
                        userBookId,
                        "UserShelfBook",
                        "Secondary",
                        java.time.Instant.now(),
                        "Primary DELETE 성공 후 Secondary DELETE 실패로 인한 유령 데이터 정리 필요"
                    );
                
                recoveryQueueService.publish(event);
                org.slf4j.LoggerFactory.getLogger(BookService.class)
                    .warn("DELETE 보상 트랜잭션: Secondary 유령 데이터 정리를 위해 Recovery Queue에 발행 (userBookId: {})", userBookId);
                
                return null;
            },
            "UserShelfBook"  // 엔티티 타입 (Recovery Queue 발행용)
        );
        
        // 내 서재 캐시 무효화 (부수 효과, DB 트랜잭션 외부로 분리)
        invalidateMyShelfCache(userId);
    }
    
    /**
     * 책 읽기 상태 변경
     * UserShelfBook Entity를 받아서 처리
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void updateBookCategory(UserShelfBook userBook, BookCategory category) {
        // 소유권 확인은 Controller에서 이미 완료된 것으로 가정
        Long userId = userBook.getUserId();
        
        // Entity만 받아서 카테고리 변경 처리
        userBook.setCategory(category);
        userBook.setUpdatedAt(LocalDateTime.now());
        
        // Dual Write 적용
        updateUserShelfBookWithDualWrite(userBook);
        
        // 내 서재 캐시 무효화 (부수 효과, DB 트랜잭션 외부로 분리)
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
        
        // Dual Write 적용
        return updateUserShelfBookWithDualWrite(userBook);
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
        
        // Dual Write 적용
        UserShelfBook savedBook = updateUserShelfBookWithDualWrite(userBook);
        
        // 내 서재 캐시 무효화 (부수 효과, DB 트랜잭션 외부로 분리)
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