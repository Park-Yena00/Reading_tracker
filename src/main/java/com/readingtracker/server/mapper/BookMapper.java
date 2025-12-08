package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.AladinBook;
import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSearchFilter;
import com.readingtracker.server.common.constant.PurchaseType;
import com.readingtracker.server.dto.UserShelfBookCacheDTO;
import com.readingtracker.server.dto.requestDTO.BookAdditionRequest;
import com.readingtracker.server.dto.requestDTO.BookDetailUpdateRequest;
import com.readingtracker.server.dto.requestDTO.FinishReadingRequest;
import com.readingtracker.server.dto.requestDTO.StartReadingRequest;
import com.readingtracker.server.dto.responseDTO.BookAdditionResponse;
import com.readingtracker.server.dto.responseDTO.BookSearchResponse;
import com.readingtracker.server.dto.responseDTO.MyShelfResponse;


import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookMapper {
    
    /**
     * BookAdditionRequest → UserShelfBook Entity 변환
     * 문서: MAPSTRUCT_ARCHITECTURE_DESIGN.md 준수
     * - user는 Controller에서 설정
     * - book은 Mapper 내부에서 생성
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)  // Controller에서 설정
    @Mapping(target = "book", expression = "java(createBookFromRequest(request))")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "categoryManuallySet", constant = "true")
    @Mapping(target = "expectation", source = "expectation")
    @Mapping(target = "readingStartDate", source = "readingStartDate")
    @Mapping(target = "readingProgress", source = "readingProgress")
    @Mapping(target = "purchaseType", source = "purchaseType")
    @Mapping(target = "readingFinishedDate", source = "readingFinishedDate")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "review", source = "review")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserShelfBook toUserShelfBookEntity(BookAdditionRequest request);
    
    /**
     * UserShelfBook → BookAdditionResponse 변환
     */
    @Mapping(target = "message", constant = "책이 내 서재에 추가되었습니다.")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "category", source = "category")
    BookAdditionResponse toBookAdditionResponse(UserShelfBook userBook);
    
    /**
     * UserShelfBook → MyShelfResponse.ShelfBook 변환
     */
    @Mapping(target = "userBookId", source = "id")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "author", source = "book.author")
    @Mapping(target = "publisher", source = "book.publisher")
    @Mapping(target = "description", source = "book.description")
    @Mapping(target = "coverUrl", source = "book.coverUrl")
    @Mapping(target = "totalPages", source = "book.totalPages")
    @Mapping(target = "mainGenre", source = "book.mainGenre")
    @Mapping(target = "pubDate", source = "book.pubDate")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "expectation", source = "expectation")
    @Mapping(target = "lastReadPage", source = "readingProgress")
    @Mapping(target = "lastReadAt", source = "readingStartDate")
    @Mapping(target = "readingFinishedDate", source = "readingFinishedDate")
    @Mapping(target = "purchaseType", source = "purchaseType")
    @Mapping(target = "addedAt", source = "createdAt")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "review", source = "review")
    MyShelfResponse.ShelfBook toShelfBook(UserShelfBook userBook);
    
    /**
     * List<UserShelfBook> → MyShelfResponse 변환
     */
    default MyShelfResponse toMyShelfResponse(List<UserShelfBook> userBooks) {
        if (userBooks == null || userBooks.isEmpty()) {
            return new MyShelfResponse(List.of(), 0);
        }
        
        List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
            .filter(ub -> ub != null && ub.getBook() != null) // null 체크 추가
            .map(this::toShelfBook)
            .toList();
        
        return new MyShelfResponse(shelfBooks, shelfBooks.size());
    }
    
    /**
     * AladinBook → BookSearchResponse.BookInfo 변환
     */
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "isbn13", source = "isbn13")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "publisher", source = "publisher")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "coverUrl", source = "coverUrl")
    @Mapping(target = "totalPages", source = "totalPages")
    @Mapping(target = "mainGenre", source = "mainGenre")
    @Mapping(target = "pubDate", source = "publishedAt")  // publishedAt → pubDate
    @Mapping(target = "priceSales", source = "priceSales")
    @Mapping(target = "priceStandard", source = "priceStandard")
    BookSearchResponse.BookInfo toBookSearchBookInfo(AladinBook aladinBook);
    
    /**
     * List<AladinBook> → BookSearchResponse 변환
     */
    default BookSearchResponse toBookSearchResponse(List<AladinBook> books, String query, BookSearchFilter searchFilter) {
        if (books == null) {
            return new BookSearchResponse(List.of(), 0, 1, 10, query, searchFilter);
        }
        
        List<BookSearchResponse.BookInfo> bookInfos = books.stream()
            .map(this::toBookSearchBookInfo)
            .toList();
        
        return new BookSearchResponse(bookInfos, bookInfos.size(), 1, bookInfos.size(), query, searchFilter);
    }
    
    /**
     * StartReadingRequest → UserShelfBook Entity 업데이트
     * 기존 Entity에 DTO의 필드값을 설정합니다.
     */
    default void updateUserShelfBookFromStartReadingRequest(UserShelfBook userBook, StartReadingRequest request) {
        if (request == null) {
            return;
        }
        
        if (request.getReadingStartDate() != null) {
            userBook.setReadingStartDate(request.getReadingStartDate());
        }
        
        if (request.getReadingProgress() != null) {
            userBook.setReadingProgress(request.getReadingProgress());
        }
        
        if (request.getPurchaseType() != null) {
            userBook.setPurchaseType(request.getPurchaseType());
        }
    }
    
    /**
     * FinishReadingRequest → UserShelfBook Entity 업데이트
     * 기존 Entity에 DTO의 필드값을 설정합니다.
     */
    default void updateUserShelfBookFromFinishReadingRequest(UserShelfBook userBook, FinishReadingRequest request) {
        if (request == null) {
            return;
        }
        
        if (request.getReadingFinishedDate() != null) {
            userBook.setReadingFinishedDate(request.getReadingFinishedDate());
        }
        
        if (request.getRating() != null) {
            userBook.setRating(request.getRating());
        }
        
        if (request.getReview() != null) {
            userBook.setReview(request.getReview());
        }
    }
    
    // ========== 내부 헬퍼 메서드 ==========
    
    /**
     * BookAdditionRequest → Book Entity 변환
     * 문서: MAPSTRUCT_ARCHITECTURE_DESIGN.md 준수
     * Mapper 내부에서 Book을 생성하기 위한 헬퍼 메서드
     */
    default Book createBookFromRequest(BookAdditionRequest request) {
        Book book = new Book(
            request.getIsbn(),
            request.getTitle(),
            request.getAuthor(),
            request.getPublisher()
        );
        book.setDescription(request.getDescription());
        book.setCoverUrl(request.getCoverUrl());
        book.setTotalPages(request.getTotalPages());
        book.setMainGenre(request.getMainGenre());
        book.setPubDate(request.getPubDate());
        return book;
    }
    
    /**
     * BookDetailUpdateRequest → UserShelfBook Entity 업데이트
     * 기존 Entity에 DTO의 필드값을 설정합니다. (null이 아닌 값만 업데이트)
     */
    default void updateUserShelfBookFromBookDetailUpdateRequest(UserShelfBook userBook, BookDetailUpdateRequest request) {
        if (request == null) {
            return;
        }
        
        if (request.getCategory() != null) {
            userBook.setCategory(request.getCategory());
            userBook.setCategoryManuallySet(true);
        }
        
        if (request.getExpectation() != null) {
            userBook.setExpectation(request.getExpectation());
        }
        
        if (request.getReadingStartDate() != null) {
            userBook.setReadingStartDate(request.getReadingStartDate());
        }
        
        if (request.getReadingProgress() != null) {
            userBook.setReadingProgress(request.getReadingProgress());
        }
        
        if (request.getPurchaseType() != null) {
            userBook.setPurchaseType(request.getPurchaseType());
        }
        
        if (request.getReadingFinishedDate() != null) {
            userBook.setReadingFinishedDate(request.getReadingFinishedDate());
        }
        
        if (request.getRating() != null) {
            userBook.setRating(request.getRating());
        }
        
        if (request.getReview() != null) {
            userBook.setReview(request.getReview());
        }
    }
    
    // ========== Redis 캐싱용 DTO 변환 ==========
    
    /**
     * UserShelfBook Entity → UserShelfBookCacheDTO 변환
     * Redis 캐싱을 위한 변환 (순환 참조 방지)
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "author", source = "book.author")
    @Mapping(target = "publisher", source = "book.publisher")
    @Mapping(target = "description", source = "book.description")
    @Mapping(target = "coverUrl", source = "book.coverUrl")
    @Mapping(target = "totalPages", source = "book.totalPages")
    @Mapping(target = "mainGenre", source = "book.mainGenre")
    @Mapping(target = "pubDate", source = "book.pubDate")
    UserShelfBookCacheDTO toUserShelfBookCacheDTO(UserShelfBook userBook);
    
    /**
     * List<UserShelfBook> → List<UserShelfBookCacheDTO> 변환
     */
    List<UserShelfBookCacheDTO> toUserShelfBookCacheDTOList(List<UserShelfBook> userBooks);
    
    /**
     * UserShelfBookCacheDTO → UserShelfBook Entity 변환
     * Redis 캐시에서 복원 시 사용
     * @Context UserRepository: User 프록시 생성을 위한 Repository
     */
    default UserShelfBook toUserShelfBookEntity(UserShelfBookCacheDTO dto, @Context com.readingtracker.dbms.repository.primary.UserRepository userRepository) {
        if (dto == null) {
            return null;
        }
        
        UserShelfBook entity = new UserShelfBook();
        
        // UserShelfBook 필드
        entity.setId(dto.getId());
        entity.setCategory(dto.getCategory());
        entity.setCategoryManuallySet(dto.getCategoryManuallySet());
        entity.setExpectation(dto.getExpectation());
        entity.setReadingStartDate(dto.getReadingStartDate());
        entity.setReadingProgress(dto.getReadingProgress());
        entity.setPurchaseType(dto.getPurchaseType());
        entity.setReadingFinishedDate(dto.getReadingFinishedDate());
        entity.setRating(dto.getRating());
        entity.setReview(dto.getReview());
        entity.setUpdatedAt(dto.getUpdatedAt());
        
        // Book 엔티티 생성 및 설정
        Book book = new Book();
        book.setId(dto.getBookId());
        book.setIsbn(dto.getIsbn());
        book.setTitle(dto.getTitle());
        book.setAuthor(dto.getAuthor());
        book.setPublisher(dto.getPublisher());
        book.setDescription(dto.getDescription());
        book.setCoverUrl(dto.getCoverUrl());
        book.setTotalPages(dto.getTotalPages());
        book.setMainGenre(dto.getMainGenre());
        book.setPubDate(dto.getPubDate());
        entity.setBook(book);
        
        // User 프록시 생성 (실제 DB 조회 없이 참조만 설정)
        if (dto.getUserId() != null && userRepository != null) {
            com.readingtracker.dbms.entity.User user = userRepository.getReferenceById(dto.getUserId());
            entity.setUser(user);
        }
        
        return entity;
    }
    
    /**
     * List<UserShelfBookCacheDTO> → List<UserShelfBook> 변환
     * Redis 캐시에서 복원 시 사용
     */
    default List<UserShelfBook> toUserShelfBookEntityList(List<UserShelfBookCacheDTO> dtos, @Context com.readingtracker.dbms.repository.primary.UserRepository userRepository) {
        if (dtos == null) {
            return List.of();
        }
        
        return dtos.stream()
            .map(dto -> toUserShelfBookEntity(dto, userRepository))
            .toList();
    }
}

