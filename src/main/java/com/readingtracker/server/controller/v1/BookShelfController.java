package com.readingtracker.server.controller.v1;

import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.common.constant.BookSortCriteria;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.dto.requestDTO.BookAdditionRequest;
import com.readingtracker.server.dto.responseDTO.BookAdditionResponse;
import com.readingtracker.server.dto.requestDTO.StartReadingRequest;
import com.readingtracker.server.dto.requestDTO.BookDetailUpdateRequest;
import com.readingtracker.server.dto.responseDTO.MyShelfResponse;
import com.readingtracker.server.dto.requestDTO.FinishReadingRequest;
import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.UserRepository;
import com.readingtracker.dbms.repository.UserShelfBookRepository;
import com.readingtracker.server.mapper.BookMapper;
import com.readingtracker.server.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "내 서재 관리", description = "사용자 서재 관리 API")
public class BookShelfController extends BaseV1Controller {
    
    @Autowired
    private BookService bookService;
    
    @Autowired
    private BookMapper bookMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
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
            @Valid @RequestBody BookAdditionRequest request) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // Mapper를 통해 RequestDTO → Entity 변환
        Book book = bookMapper.toBookEntity(request);
        UserShelfBook userShelfBook = bookMapper.toUserShelfBookEntity(request, user);
        
        // Service 호출 (Entity만 전달)
        UserShelfBook savedUserBook = bookService.addBookToShelf(book, userShelfBook);
        
        // Mapper를 통해 Entity → ResponseDTO 변환
        BookAdditionResponse response = bookMapper.toBookAdditionResponse(savedUserBook);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 내 서재 조회 (인증 필요)
     * GET /api/v1/user/books
     */
    @GetMapping("/user/books")
    @Operation(
        summary = "내 서재 조회", 
        description = "내 서재에 있는 책 목록을 조회합니다 (인증 필요). 정렬 기준: 도서명(TITLE), 저자명(AUTHOR), 출판사명(PUBLISHER), 태그/메인 장르(GENRE). 모든 정렬은 가나다순/ABC순 오름차순입니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MyShelfResponse> getMyShelf(
            @Parameter(description = "카테고리 필터 (ToRead, Reading, AlmostFinished, Finished)")
            @RequestParam(required = false) BookCategory category,
            @Parameter(description = "정렬 기준 (TITLE: 도서명, AUTHOR: 저자명, PUBLISHER: 출판사명, GENRE: 태그/메인 장르). 기본값: TITLE")
            @RequestParam(required = false) BookSortCriteria sortBy) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 내 서재 조회 (Entity 반환)
        List<UserShelfBook> userBooks = bookService.getMyShelf(user.getId(), category, sortBy);
        
        // Mapper를 통해 Entity → ResponseDTO 변환
        MyShelfResponse response = bookMapper.toMyShelfResponse(userBooks);
        
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
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // UserShelfBook 조회 및 소유권 확인
        UserShelfBook userBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // Service 호출 (Entity만 전달)
        bookService.removeBookFromShelf(userBook);
        
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
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // UserShelfBook 조회 및 소유권 확인
        UserShelfBook userBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // Service 호출 (Entity만 전달)
        bookService.updateBookCategory(userBook, category);
        
        return ApiResponse.success("책의 읽기 상태가 변경되었습니다.");
    }
    
    /**
     * 책 읽기 시작 (ToRead → Reading)
     */
    @PostMapping("/user/books/{userBookId}/start-reading")
    @Operation(
        summary = "책 읽기 시작",
        description = "ToRead 상태의 책을 읽기 시작합니다. 독서 시작일과 진행률(페이지 수)을 입력받아 Reading 상태로 변경합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> startReading(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Valid @RequestBody StartReadingRequest request) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // UserShelfBook 조회 및 소유권 확인
        UserShelfBook userBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // RequestDTO의 필드를 Entity에 설정
        userBook.setReadingStartDate(request.getReadingStartDate());
        userBook.setReadingProgress(request.getReadingProgress());
        if (request.getPurchaseType() != null) {
            userBook.setPurchaseType(request.getPurchaseType());
        }
        
        // Service 호출 (Entity만 전달)
        bookService.startReading(userBook);
        
        return ApiResponse.success("책 읽기를 시작했습니다.");
    }
    
    /**
     * 책 완독 (AlmostFinished → Finished)
     */
    @PostMapping("/user/books/{userBookId}/finish-reading")
    @Operation(
        summary = "책 완독",
        description = "AlmostFinished 상태의 책을 완독 처리합니다. 독서 종료일과 평점을 입력받아 Finished 상태로 변경합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> finishReading(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Valid @RequestBody FinishReadingRequest request) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // UserShelfBook 조회 및 소유권 확인
        UserShelfBook userBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // RequestDTO의 필드를 Entity에 설정
        userBook.setReadingFinishedDate(request.getReadingFinishedDate());
        userBook.setRating(request.getRating());
        if (request.getReview() != null) {
            userBook.setReview(request.getReview());
        }
        
        // Service 호출 (Entity만 전달)
        bookService.finishReading(userBook);
        
        return ApiResponse.success("책이 완독 처리되었습니다.");
    }
    
    /**
     * 책 상세 정보 변경 (인증 필요)
     * PUT /api/v1/user/books/{userBookId}
     */
    @PutMapping("/user/books/{userBookId}")
    @Operation(
        summary = "책 상세 정보 변경", 
        description = "독서 시작일, 독서 종료일, 진행률(페이지수), 평점, 후기 등 책의 상세 정보를 변경합니다 (인증 필요). 기존 값은 유지되고, 입력된 값만 업데이트됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> updateBookDetail(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "책 상세 정보 변경 요청", required = true)
            @Valid @RequestBody BookDetailUpdateRequest request) {
        
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // UserShelfBook 조회 및 소유권 확인
        UserShelfBook userBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // RequestDTO의 필드를 Entity에 설정 (null이 아닌 값만 업데이트)
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
        
        // Service 호출 (Entity만 전달)
        bookService.updateBookDetail(userBook);
        
        return ApiResponse.success("책 상세 정보가 변경되었습니다.");
    }
}

