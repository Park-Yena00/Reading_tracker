package com.readingtracker.server.controller.v1;

import com.readingtracker.server.common.constant.BookSearchFilter;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.dto.requestDTO.BookSearchRequest;
import com.readingtracker.server.dto.responseDTO.BookDetailResponse;
import com.readingtracker.server.dto.responseDTO.BookSearchResponse;
import com.readingtracker.server.service.AladinApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "책 검색", description = "책 검색 및 도서 세부 정보 조회 API")
public class BookSearchController extends BaseV1Controller {
    
    @Autowired
    private AladinApiService aladinApiService;
    
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
            @Parameter(description = "검색 필터 (TITLE: 도서명, AUTHOR: 저자명, PUBLISHER: 출판사명)")
            @RequestParam(defaultValue = "TITLE") BookSearchFilter queryType,
            @Parameter(description = "시작 페이지")
            @RequestParam(defaultValue = "1") Integer start,
            @Parameter(description = "페이지당 결과 수 (최대 50)")
            @RequestParam(defaultValue = "10") Integer maxResults) {
        
        // BookSearchRequest 생성 및 검증
        BookSearchRequest request = new BookSearchRequest();
        request.setQuery(query);
        request.setQueryType(queryType);
        request.setStart(start);
        request.setMaxResults(maxResults);
        
        // 검증 수행 (@Valid는 @ModelAttribute와 함께 사용하거나 수동 검증 필요)
        // GET 요청이므로 @ModelAttribute 대신 수동으로 검증하거나 Validator 사용
        // 여기서는 간단하게 DTO 생성 후 Service 호출
        // 실제 검증은 Service 계층에서 처리하거나 별도 Validator 사용 가능
        
        BookSearchResponse response = aladinApiService.searchBooks(request);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 도서 세부 정보 검색 (비인증)
     * GET /api/v1/books/{isbn}
     */
    @GetMapping("/books/{isbn}")
    @Operation(
        summary = "도서 세부 정보 검색",
        description = "ISBN을 통해 알라딘 Open API에서 도서의 상세 정보를 조회합니다 (비인증 접근 가능)"
    )
    public ApiResponse<BookDetailResponse> getBookDetail(
            @Parameter(description = "도서 ISBN", required = true)
            @PathVariable String isbn) {
        
        BookDetailResponse response = aladinApiService.getBookDetail(isbn);
        
        return ApiResponse.success(response);
    }
}

