package com.readingtracker.controller.v1;

import com.readingtracker.common.BookCategory;
import com.readingtracker.dto.ApiResponse;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.BookAdditionRequest;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.BookSearchRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.BookAdditionResponse;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.BookSearchResponse;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.MyShelfResponse;
import com.readingtracker.entity.Book;
import com.readingtracker.entity.UserShelfBook;
import com.readingtracker.service.AladinApiService;
import com.readingtracker.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "책 관리", description = "책 검색 및 내 서재 관리 API")
public class BookController extends BaseV1Controller {
    
    @Autowired
    private AladinApiService aladinApiService;
    
    @Autowired
    private BookService bookService;
    
    /**
     * 책 검색 (비인증)
     * GET /api/v1/books/search
     */
    @GetMapping("/books/search")
    @Operation(
        summary = "책 검색", 
        description = "알라딘 Open API를 통해 책을 검색합니다 (비인증 접근 가능)"
    )
    public ApiResponse<BookSearchResponse> searchBooks(
            @Parameter(description = "검색어", required = true)
            @RequestParam String query,
            @Parameter(description = "검색 타입 (Title, Author, Publisher, Keyword)")
            @RequestParam(defaultValue = "Title") String queryType,
            @Parameter(description = "시작 페이지")
            @RequestParam(defaultValue = "1") Integer start,
            @Parameter(description = "페이지당 결과 수 (최대 50)")
            @RequestParam(defaultValue = "10") Integer maxResults) {
        
        BookSearchRequest request = new BookSearchRequest(query, queryType, start, maxResults);
        BookSearchResponse response = aladinApiService.searchBooks(request);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 내 서재에 책 추가 (인증 필요)
     * POST /api/v1/user/books
     */
    @PostMapping("/user/books")
    @Operation(
        summary = "내 서재에 책 추가", 
        description = "검색한 책을 내 서재에 추가합니다 (인증 필요)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<BookAdditionResponse> addBookToShelf(
            @Parameter(description = "책 추가 정보", required = true)
            @RequestBody BookAdditionRequest request) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 책 추가 처리
        UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
        
        BookAdditionResponse response = new BookAdditionResponse(
            "책이 내 서재에 추가되었습니다.",
            userBook.getBookId(),
            request.getTitle(),
            userBook.getCategory()
        );
        
        return ApiResponse.success(response);
    }
    
    /**
     * 내 서재 조회 (인증 필요)
     * GET /api/v1/user/books
     */
    @GetMapping("/user/books")
    @Operation(
        summary = "내 서재 조회", 
        description = "내 서재에 있는 책 목록을 조회합니다 (인증 필요)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MyShelfResponse> getMyShelf(
            @Parameter(description = "카테고리 필터 (READ_LATER, READING, NEARLY_DONE, COMPLETED)")
            @RequestParam(required = false) BookCategory category) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 내 서재 조회
        List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category);
        
        // DTO 변환
        List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
            .map(this::convertToShelfBook)
            .collect(Collectors.toList());
        
        MyShelfResponse response = new MyShelfResponse(shelfBooks, shelfBooks.size());
        
        return ApiResponse.success(response);
    }
    
    /**
     * 내 서재에서 책 제거 (인증 필요)
     * DELETE /api/v1/user/books/{userBookId}
     */
    @DeleteMapping("/user/books/{userBookId}")
    @Operation(
        summary = "내 서재에서 책 제거", 
        description = "내 서재에서 책을 제거합니다 (인증 필요)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> removeBookFromShelf(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 책 제거 처리
        bookService.removeBookFromShelf(loginId, userBookId);
        
        return ApiResponse.success("책이 내 서재에서 제거되었습니다.");
    }
    
    /**
     * 책 읽기 상태 변경 (인증 필요)
     * PUT /api/v1/user/books/{userBookId}/category
     */
    @PutMapping("/user/books/{userBookId}/category")
    @Operation(
        summary = "책 읽기 상태 변경", 
        description = "책의 읽기 상태(카테고리)를 변경합니다 (인증 필요)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> updateBookCategory(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "새로운 카테고리", required = true)
            @RequestParam BookCategory category) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 책 상태 변경 처리
        bookService.updateBookCategory(loginId, userBookId, category);
        
        return ApiResponse.success("책의 읽기 상태가 변경되었습니다.");
    }
    
    /**
     * UserBook을 ShelfBook으로 변환
     */
    private MyShelfResponse.ShelfBook convertToShelfBook(UserShelfBook userBook) {
        MyShelfResponse.ShelfBook shelfBook = new MyShelfResponse.ShelfBook();
        
        shelfBook.setUserBookId(userBook.getId());
        shelfBook.setBookId(userBook.getBookId());
        shelfBook.setCategory(userBook.getCategory());
        shelfBook.setLastReadPage(userBook.getLastReadPage());
        shelfBook.setLastReadAt(userBook.getLastReadAt());
        shelfBook.setAddedAt(userBook.getAddedAt());
        
        // Book 정보 설정
        Book book = userBook.getBook();
        if (book != null) {
            shelfBook.setIsbn(book.getIsbn());
            shelfBook.setTitle(book.getTitle());
            shelfBook.setAuthor(book.getAuthor());
            shelfBook.setPublisher(book.getPublisher());
            shelfBook.setDescription(book.getDescription());
            shelfBook.setCoverUrl(book.getCoverUrl());
            shelfBook.setTotalPages(book.getTotalPages());
            shelfBook.setMainGenre(book.getMainGenre());
            shelfBook.setPubDate(book.getPubDate());
        }
        
        return shelfBook;
    }
}
