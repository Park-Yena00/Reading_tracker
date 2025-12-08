package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.primary.TagRepository;
import com.readingtracker.server.dto.requestDTO.MemoCreateRequest;
import com.readingtracker.server.dto.requestDTO.MemoUpdateRequest;
import com.readingtracker.server.dto.responseDTO.BookResponse;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TodayFlowResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MemoMapper {
    
    // ========== RequestDTO → Entity 변환 ==========
    
    /**
     * MemoCreateRequest → Memo Entity 변환
     * 
     * 태그 처리: 태그 코드 리스트를 Tag 엔티티 리스트로 변환
     * 태그 자동 연결 규칙은 섹션 18.3 태그 사용 규칙을 참조하세요.
     * 
     * @param request MemoCreateRequest DTO
     * @param user User 엔티티 (Context로 전달)
     * @param userShelfBook UserShelfBook 엔티티 (Controller에서 조회 후 전달)
     * @param tagRepository TagRepository (Context로 전달, 태그 처리용)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", expression = "java(user)")
    @Mapping(target = "userShelfBook", expression = "java(userShelfBook)")
    @Mapping(target = "pageNumber", source = "request.pageNumber")
    @Mapping(target = "content", source = "request.content")
    @Mapping(target = "tags", expression = "java(processTags(request.getTags(), request.getTagCategory(), tagRepository))")
    @Mapping(target = "memoStartTime", source = "request.memoStartTime")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Memo toMemoEntity(MemoCreateRequest request, @Context User user, 
                      @Context UserShelfBook userShelfBook, @Context TagRepository tagRepository);
    
    /**
     * MemoUpdateRequest → Memo Entity 부분 업데이트
     * 
     * 기존 Memo 엔티티에 RequestDTO의 필드만 업데이트합니다.
     * content와 tags만 수정 가능하며, pageNumber는 수정 불가입니다.
     * 
     * @param memo 기존 Memo 엔티티 (업데이트 대상)
     * @param request MemoUpdateRequest DTO
     * @param tagRepository TagRepository (Context로 전달, 태그 처리용)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "userShelfBook", ignore = true)
    @Mapping(target = "pageNumber", ignore = true)  // 수정 불가
    @Mapping(target = "content", source = "request.content")
    @Mapping(target = "tags", expression = "java(processTags(request.getTags(), request.getTagCategory(), tagRepository))")
    @Mapping(target = "memoStartTime", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateMemoFromRequest(@MappingTarget Memo memo, MemoUpdateRequest request, 
                                @Context TagRepository tagRepository);
    
    // ========== Entity → ResponseDTO 변환 ==========
    
    @Mapping(target = "userBookId", source = "userShelfBook.id")
    @Mapping(target = "bookTitle", source = "userShelfBook.book.title")
    @Mapping(target = "bookIsbn", source = "userShelfBook.book.isbn")
    @Mapping(target = "tags", expression = "java(convertTagsToStringList(memo.getTags()))")
    MemoResponse toMemoResponse(Memo memo);
    
    List<MemoResponse> toMemoResponseList(List<Memo> memos);
    
    /**
     * UserShelfBook 엔티티를 BookResponse DTO로 변환
     * Book 엔티티 null 체크 포함 (방어적 프로그래밍)
     */
    default BookResponse toBookResponse(com.readingtracker.dbms.entity.UserShelfBook book) {
        if (book == null) {
            return null;
        }
        
        BookResponse response = new BookResponse();
        response.setId(book.getId());
        response.setCategory(book.getCategory());
        response.setReadingProgress(book.getReadingProgress());
        
        // Book 엔티티 null 체크 (방어적 프로그래밍)
        // 이론적으로는 null이 아니어야 하지만, LAZY 로딩 미초기화나 데이터 무결성 이슈 대비
        if (book.getBook() != null) {
            response.setTitle(book.getBook().getTitle());
            response.setAuthor(book.getBook().getAuthor());
            response.setIsbn(book.getBook().getIsbn());
        }
        
        return response;
    }
    
    List<BookResponse> toBookResponseList(List<com.readingtracker.dbms.entity.UserShelfBook> books);
    
    /**
     * TodayFlowResponse 생성 헬퍼 메서드
     * 
     * 참고: 이 메서드는 현재 사용되지 않으며, Controller에서 직접 TodayFlowResponse를 구성합니다.
     * 향후 필요 시 사용할 수 있습니다.
     */
    default TodayFlowResponse toTodayFlowResponse(List<Memo> memos, LocalDate date, String sortBy) {
        TodayFlowResponse response = new TodayFlowResponse();
        response.setDate(date);
        // memos는 Controller에서 memosByBook 또는 memosByTag로 변환하여 설정
        response.setTotalMemoCount((long) memos.size());
        response.setSortBy(sortBy);
        return response;
    }
    
    /**
     * Tag 엔티티 리스트를 태그 코드 리스트로 변환
     */
    default List<String> convertTagsToStringList(List<com.readingtracker.dbms.entity.Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .map(com.readingtracker.dbms.entity.Tag::getCode)
            .toList();
    }
    
    /**
     * 태그 코드 리스트를 Tag 엔티티 리스트로 변환
     * 
     * 태그 자동 연결 규칙:
     * - 태그 미선택 시 '기타' 태그 자동 연결 (현재 선택된 대분류에 맞는 etc 태그 선택)
     * - 허용된 태그 코드만 연결 (카탈로그 검증)
     * - 활성화된 태그만 연결
     * 
     * 참고: TagRepository는 @Context 파라미터로 전달됩니다.
     * 상세 내용은 섹션 18.3 태그 사용 규칙을 참조하세요.
     */
    default List<com.readingtracker.dbms.entity.Tag> processTags(
            List<String> tagCodes,
            String tagCategory,
            @Context TagRepository tagRepository) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            // 태그 미선택 시 '기타' 태그 자동 연결
            // 현재 선택된 대분류에 맞는 etc 태그 선택 (기본값: TYPE)
            final com.readingtracker.dbms.entity.TagCategory finalCategory;
            if (tagCategory != null && "TOPIC".equalsIgnoreCase(tagCategory)) {
                finalCategory = com.readingtracker.dbms.entity.TagCategory.TOPIC;
            } else {
                finalCategory = com.readingtracker.dbms.entity.TagCategory.TYPE;
            }
            
            // 대분류와 code로 etc 태그 조회
            return tagRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(finalCategory).stream()
                .filter(tag -> "etc".equals(tag.getCode()))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("'%s' 대분류의 '기타' 태그가 카탈로그에 존재하지 않습니다.", finalCategory)));
        }
        
        List<com.readingtracker.dbms.entity.Tag> tags = new ArrayList<>();
        for (String tagCode : tagCodes) {
            if (tagCode == null || tagCode.trim().isEmpty()) {
                continue;
            }
            
            // 태그 코드로 조회 (카탈로그에 존재하는 태그만 허용)
            com.readingtracker.dbms.entity.Tag tag = tagRepository.findByCode(tagCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("태그 코드 '%s'는 허용된 카탈로그에 존재하지 않습니다.", tagCode)));
            
            // 활성화된 태그만 연결
            if (!tag.getIsActive()) {
                throw new IllegalArgumentException(
                    String.format("태그 코드 '%s'는 비활성화된 태그입니다.", tagCode));
            }
            
            tags.add(tag);
        }
        return tags;
    }
}

