package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.server.dto.requestDTO.BookAdditionRequest;
import com.readingtracker.server.dto.responseDTO.BookAdditionResponse;
import com.readingtracker.server.dto.responseDTO.MyShelfResponse;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookMapper {
    
    BookMapper INSTANCE = Mappers.getMapper(BookMapper.class);
    
    /**
     * BookAdditionRequest → Book Entity 변환
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userBooks", ignore = true)
    Book toBookEntity(BookAdditionRequest request);
    
    /**
     * BookAdditionRequest + User → UserShelfBook Entity 변환
     * 주의: Book은 별도로 생성되어야 함
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "book", ignore = true) // Service에서 별도 설정 필요
    @Mapping(target = "category", source = "request.category")
    @Mapping(target = "categoryManuallySet", constant = "true")
    @Mapping(target = "expectation", source = "request.expectation")
    @Mapping(target = "memo", ignore = true)
    @Mapping(target = "readingStartDate", source = "request.readingStartDate")
    @Mapping(target = "readingProgress", source = "request.readingProgress")
    @Mapping(target = "purchaseType", source = "request.purchaseType")
    @Mapping(target = "readingFinishedDate", source = "request.readingFinishedDate")
    @Mapping(target = "rating", source = "request.rating")
    @Mapping(target = "review", source = "request.review")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserShelfBook toUserShelfBookEntity(BookAdditionRequest request, User user);
    
    /**
     * UserShelfBook → BookAdditionResponse 변환
     */
    @Mapping(target = "message", constant = "책이 내 서재에 추가되었습니다.")
    @Mapping(target = "bookId", source = "userBook.bookId")
    @Mapping(target = "title", source = "userBook.book.title")
    @Mapping(target = "category", source = "userBook.category")
    BookAdditionResponse toBookAdditionResponse(UserShelfBook userBook);
    
    /**
     * UserShelfBook → MyShelfResponse.ShelfBook 변환
     */
    @Mapping(target = "userBookId", source = "id")
    @Mapping(target = "bookId", source = "bookId")
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
    @Mapping(target = "lastReadPage", source = "readingProgress")
    @Mapping(target = "lastReadAt", source = "readingStartDate")
    @Mapping(target = "addedAt", source = "createdAt")
    MyShelfResponse.ShelfBook toShelfBook(UserShelfBook userBook);
    
    /**
     * List<UserShelfBook> → MyShelfResponse 변환
     */
    default MyShelfResponse toMyShelfResponse(List<UserShelfBook> userBooks) {
        if (userBooks == null) {
            return new MyShelfResponse();
        }
        
        List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
            .map(this::toShelfBook)
            .toList();
        
        return new MyShelfResponse(shelfBooks, shelfBooks.size());
    }
}

