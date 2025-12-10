package com.readingtracker.server.controller.v1;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.TagCategory;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.primary.TagRepository;
import com.readingtracker.dbms.repository.primary.UserShelfBookRepository;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.dto.requestDTO.CloseBookRequest;
import com.readingtracker.server.dto.requestDTO.MemoCreateRequest;
import com.readingtracker.server.dto.requestDTO.MemoUpdateRequest;
import com.readingtracker.server.dto.responseDTO.BookMemoGroup;
import com.readingtracker.server.dto.responseDTO.BookResponse;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TagMemoGroup;
import com.readingtracker.server.dto.responseDTO.TodayFlowResponse;
import com.readingtracker.server.mapper.MemoMapper;
import com.readingtracker.server.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "오늘의 흐름", description = "독서 메모 작성 및 관리 API")
public class MemoController extends BaseV1Controller {
    
    @Autowired
    private MemoService memoService;
    
    @Autowired
    private MemoMapper memoMapper;
    
    @Autowired
    private com.readingtracker.server.service.UserService userService;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
    @Autowired
    private TagRepository tagRepository;
    
    @Autowired
    private com.readingtracker.server.service.IdempotencyKeyService idempotencyKeyService;
    
    /**
     * 메모 작성
     * POST /api/v1/memos
     * 
     * 멱등성 보장:
     * - 클라이언트가 Idempotency-Key 헤더에 UUID를 포함하여 전송
     * - 동일한 키로 여러 번 요청해도 중복 메모가 생성되지 않음
     * - 네트워크 재차단 시에도 안전하게 재시도 가능
     */
    @PostMapping("/memos")
    @Operation(
        summary = "메모 작성",
        description = "독서 중 메모를 작성합니다. 페이지당 메모 개수 제한 없이 자유롭게 기록할 수 있습니다. " +
                     "Idempotency-Key 헤더를 포함하면 중복 요청을 방지할 수 있습니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MemoResponse> createMemo(
            @Parameter(description = "멱등성 키 (선택, 중복 요청 방지용)", required = false)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "메모 작성 요청", required = true)
            @Valid @RequestBody MemoCreateRequest request) {
        
        // 1. 멱등성 키 검증 및 처리
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            ApiResponse<MemoResponse> cachedResponse = 
                idempotencyKeyService.getCachedResponse(idempotencyKey);
            if (cachedResponse != null) {
                // 캐시된 응답 반환 (중복 요청 무시)
                return cachedResponse;
            }
            
            // PROCESSING 상태로 저장 (분산 저장소에 빠르게 저장하여 동시성 이슈 방지)
            idempotencyKeyService.markAsProcessing(idempotencyKey);
        }
        
        User user = getCurrentUser();
        
        // UserShelfBook 조회 (Mapper에서 처리하기 어려우므로 Controller에서 조회)
        Long userBookId = request.getUserBookId();
        if (userBookId == null) {
            throw new IllegalArgumentException("책 ID는 필수입니다.");
        }
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        // Mapper를 통한 RequestDTO → Entity 변환 (ARCHITECTURE 원칙 준수)
        // @Context로 User, UserShelfBook, TagRepository 전달
        Memo memo = memoMapper.toMemoEntity(request, user, userShelfBook, tagRepository);
        
        // Service는 Entity만 받음
        Memo savedMemo = memoService.createMemo(user, memo);
        
        // Mapper를 통한 Entity → ResponseDTO 변환
        MemoResponse response = memoMapper.toMemoResponse(savedMemo);
        ApiResponse<MemoResponse> apiResponse = ApiResponse.success(response);
        
        // 2. 멱등성 키가 있으면 COMPLETED로 저장 및 응답 캐싱
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            idempotencyKeyService.markAsCompleted(idempotencyKey, apiResponse);
        }
        
        return apiResponse;
    }
    
    /**
     * 메모 수정
     * PUT /api/v1/memos/{memoId}
     */
    @PutMapping("/memos/{memoId}")
    @Operation(
        summary = "메모 수정",
        description = "작성한 메모의 내용, 태그, 페이지 번호를 수정합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MemoResponse> updateMemo(
            @Parameter(description = "메모 ID", required = true)
            @PathVariable Long memoId,
            @Parameter(description = "메모 수정 요청", required = true)
            @Valid @RequestBody MemoUpdateRequest request) {
        
        User user = getCurrentUser();
        
        // 기존 메모 조회
        Memo existingMemo = memoService.getMemoById(user, memoId);
        
        // Mapper를 통한 RequestDTO → Entity 변환 (부분 업데이트)
        // @Context로 TagRepository 전달
        memoMapper.updateMemoFromRequest(existingMemo, request, tagRepository);
        
        // Service는 Entity만 받음
        Memo updatedMemo = memoService.updateMemo(user, memoId, existingMemo);
        
        // Mapper를 통한 Entity → ResponseDTO 변환
        MemoResponse response = memoMapper.toMemoResponse(updatedMemo);
        return ApiResponse.success(response);
    }
    
    /**
     * 메모 삭제
     * DELETE /api/v1/memos/{memoId}
     */
    @DeleteMapping("/memos/{memoId}")
    @Operation(
        summary = "메모 삭제",
        description = "작성한 메모를 삭제합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> deleteMemo(
            @Parameter(description = "메모 ID", required = true)
            @PathVariable Long memoId) {
        
        User user = getCurrentUser();
        memoService.deleteMemo(user, memoId);
        return ApiResponse.success("메모가 삭제되었습니다.");
    }
    
    /**
     * 오늘의 흐름 조회
     * GET /api/v1/today-flow
     * 
     * 기능:
     * - 오늘 날짜의 메모 조회 (기본값)
     * - 과거 날짜의 메모 조회 가능 (독서 캘린더 연동)
     * - sortBy 파라미터에 따라 다른 정렬 방식으로 반환:
     *   - SESSION (기본값): 책별로 그룹화하여 반환. 프론트엔드에서 시간축에 재배치하여 세션 단위로 구성
     *   - BOOK: 책별로 그룹화하여 반환
     *   - TAG: 태그별로 그룹화하여 반환 (태그 그룹 내부에서 책별로 다시 그룹화)
     * 
     * 백엔드 책임:
     * - Controller: sortBy 파라미터에 따라 적절한 Service 메서드를 호출하여 정렬을 제어
     * - Repository & Service: 
     *   - SESSION/BOOK 모드: book_id를 기준으로 데이터를 조회하고, 책별로 그룹화하는 최종 변환 담당
     *   - TAG 모드: 날짜 기준으로 모든 메모를 조회하고, 태그별로 그룹화한 후 각 태그 그룹 내에서 책별로 그룹화하는 최종 변환 담당
     * 
     * 프론트엔드 책임:
     * - 정렬 옵션 제공: 사용자가 SESSION/BOOK/TAG 정렬 옵션을 선택할 수 있는 UI 요소 제공
     * - Response DTO 사용: 
     *   - SESSION/BOOK 모드: Map<Long, BookMemoGroup> 형태의 데이터를 해석하여 UI에 표시
     *   - TAG 모드: Map<String, TagMemoGroup> 형태의 데이터를 해석하여 UI에 표시
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a)
     */
    @GetMapping("/today-flow")
    @Operation(
        summary = "오늘의 흐름 조회",
        description = "특정 날짜의 메모를 조회합니다. " +
                     "날짜 파라미터가 없으면 오늘 날짜의 메모를 조회합니다. " +
                     "독서 캘린더와 연동하여 과거 날짜의 메모도 조회할 수 있습니다. " +
                     "sortBy 파라미터로 정렬 방식을 선택할 수 있습니다 (SESSION: 세션 그룹화 기본, BOOK: 책별 그룹화, TAG: 태그별 그룹화). " +
                     "TAG 모드 사용 시 tagCategory 파라미터로 태그 대분류를 선택할 수 있습니다 (TYPE: 유형 기본값, TOPIC: 주제). " +
                     "tagCategory가 지정되면 해당 대분류가 대표 태그 결정 시 1순위가 됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<TodayFlowResponse> getTodayFlow(
            @Parameter(description = "조회할 날짜 (기본값: 오늘)", required = false)
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "정렬 방식 (SESSION | BOOK | TAG)", 
                       required = false)
            @RequestParam(required = false, defaultValue = "SESSION") String sortBy,
            @Parameter(description = "태그 대분류 (TYPE | TOPIC) - TAG 모드에서만 사용", 
                       required = false)
            @RequestParam(required = false) String tagCategory) {
        
        if (date == null) {
            date = LocalDate.now();
        }
        
        User user = getCurrentUser();
        TodayFlowResponse response = new TodayFlowResponse();
        response.setDate(date);
        response.setSortBy(sortBy);
        
        // Step 2: SESSION 모드 구현 완료
        // Step 3: BOOK 모드 구현 완료
        // Step 4: TAG 모드 구현 완료
        if ("TAG".equals(sortBy)) {
            // 태그별 그룹화 (태그 그룹 내부에서 책별로 다시 그룹화)
            // tagCategory가 null이면 기본값으로 TYPE 사용 (기본 우선순위)
            TagCategory category = (tagCategory != null && "TOPIC".equalsIgnoreCase(tagCategory)) 
                ? TagCategory.TOPIC 
                : TagCategory.TYPE;
            Map<String, TagMemoGroup> memosByTag = 
                memoService.getTodayFlowGroupedByTag(user, date, category);
            response.setMemosByTag(memosByTag);
            long totalCount = memosByTag.values().stream()
                .mapToLong(TagMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        } else if ("BOOK".equals(sortBy)) {
            // 책별 그룹화
            Map<Long, BookMemoGroup> memosByBook = 
                memoService.getTodayFlowGroupedByBook(user, date);
            response.setMemosByBook(memosByBook);
            long totalCount = memosByBook.values().stream()
                .mapToLong(BookMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        } else {
            // 기본: SESSION — 시간 순 데이터 제공(세션 UI 구성은 프론트에서 처리)
            Map<Long, BookMemoGroup> memosByBook = 
                memoService.getTodayFlowGroupedByBook(user, date);
            response.setMemosByBook(memosByBook);
            long totalCount = memosByBook.values().stream()
                .mapToLong(BookMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        }
        
        return ApiResponse.success(response);
    }
    
    /**
     * 특정 책의 메모 조회 (오늘의 흐름에서 특정 책 필터링)
     * GET /api/v1/memos/books/{userBookId}
     * 
     * 기능 설명:
     * - 오늘의 흐름 화면에서 특정 책 아이콘을 선택하면 해당 책의 메모만 표시
     * - 날짜 파라미터가 있으면 해당 날짜에 작성된 메모만 조회
     * - 날짜 파라미터가 없으면 날짜 제한 없이 해당 책의 모든 메모를 조회 (전체 메모 조회)
     * - 오늘의 흐름과 동일한 화면 구성으로 표시
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1)
     */
    @GetMapping("/memos/books/{userBookId}")
    @Operation(
        summary = "특정 책의 메모 조회",
        description = "오늘의 흐름 화면에서 특정 책을 선택하면 해당 책에 작성된 메모를 조회합니다. " +
                     "날짜 파라미터가 있으면 해당 날짜에 작성된 메모만 조회하고, " +
                     "날짜 파라미터가 없으면 날짜 제한 없이 해당 책의 모든 메모를 조회합니다. " +
                     "오늘의 흐름과 동일한 화면 구성으로 표시되며, 메모는 타임라인 순서로 정렬됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<List<MemoResponse>> getBookMemos(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "조회할 날짜 (선택, 없으면 날짜 제한 없이 모든 메모 조회)", required = false)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        User user = getCurrentUser();
        List<Memo> memos;
        
        if (date != null) {
            // 특정 날짜의 메모만 조회
            memos = memoService.getBookMemosByDate(user, userBookId, date);
        } else {
            // 날짜 제한 없이 모든 메모 조회
            memos = memoService.getAllBookMemos(user, userBookId);
        }
        
        List<MemoResponse> response = memoMapper.toMemoResponseList(memos);
        return ApiResponse.success(response);
    }
    
    /**
     * 최근 기간 내에 메모가 작성된 책 목록 조회
     * GET /api/v1/memos/books/recent
     * 
     * 기능:
     * - 최근 N개월 이내에 메모가 작성된 책들의 목록을 반환
     * - 각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬
     * - 책의 흐름 기능에서 월별 책 목록으로 사용
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     */
    @GetMapping("/memos/books/recent")
    @Operation(
        summary = "최근 메모 작성 책 목록 조회",
        description = "최근 N개월 이내에 메모가 작성된 책들의 목록을 조회합니다. " +
                     "각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬됩니다. " +
                     "months 파라미터로 조회 기간을 조정할 수 있습니다 (기본값: 1개월). " +
                     "책의 흐름 기능에서 월별 책 목록으로 사용됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<List<BookResponse>> getBooksWithRecentMemos(
            @Parameter(description = "조회 기간 (개월 수, 기본값: 1)", required = false)
            @RequestParam(required = false, defaultValue = "1")
            @Min(value = 1, message = "조회 기간은 1개월 이상이어야 합니다.")
            Integer months) {
        
        User user = getCurrentUser();
        List<UserShelfBook> books = memoService.getBooksWithRecentMemos(user, months);
        
        // Mapper를 통한 Entity → DTO 변환 (ARCHITECTURE 원칙 준수)
        List<BookResponse> response = memoMapper.toBookResponseList(books);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 책 덮기 (독서 활동 종료)
     * POST /api/v1/memos/books/{userBookId}/close
     * 
     * 기능 요약:
     * - 마지막으로 읽은 페이지 수를 기록하고 독서 진행률을 업데이트합니다.
     * - 독서 진행률에 따라 카테고리가 자동으로 변경됩니다.
     * - 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.
     * 
     * 상세 기능 설명은 섹션 1.2 주요 요구사항의 "책 덮기" 항목 및 섹션 15를 참조하세요.
     * 
     * 참고: 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568)
     */
    @PostMapping("/memos/books/{userBookId}/close")
    @Operation(
        summary = "책 덮기",
        description = "독서 활동을 종료하고 마지막으로 읽은 페이지 수를 기록합니다. " +
                     "독서 진행률이 업데이트되며, 진행률에 따라 카테고리가 자동으로 변경될 수 있습니다. " +
                     "책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> closeBook(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "책 덮기 요청", required = true)
            @Valid @RequestBody CloseBookRequest request) {
        
        User user = getCurrentUser();
        memoService.closeBook(user, userBookId, request);
        return ApiResponse.success("독서 활동이 종료되었습니다.");
    }
    
    /**
     * 메모 작성 날짜 목록 조회 (캘린더용)
     * GET /api/v1/memos/dates?year={year}&month={month}
     * 
     * 특정 년/월에 메모가 작성된 날짜 목록을 조회합니다.
     * 캘린더에서 메모가 작성된 날짜를 표시하는 데 사용됩니다.
     */
    @GetMapping("/memos/dates")
    @Operation(
        summary = "메모 작성 날짜 목록 조회",
        description = "특정 년/월에 메모가 작성된 날짜 목록을 조회합니다. " +
                     "캘린더에서 메모가 작성된 날짜를 표시하는 데 사용됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<List<String>> getMemoDates(
            @Parameter(description = "조회할 년도", required = true)
            @RequestParam int year,
            @Parameter(description = "조회할 월 (1-12)", required = true)
            @RequestParam @Min(1) @Max(12) int month) {
        
        User user = getCurrentUser();
        List<String> dates = memoService.getMemoDates(user, year, month);
        return ApiResponse.success(dates);
    }
    
    /**
     * 현재 로그인한 사용자 조회 헬퍼 메서드
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        User user = userService.findActiveUserByLoginId(loginId);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        return user;
    }
}

