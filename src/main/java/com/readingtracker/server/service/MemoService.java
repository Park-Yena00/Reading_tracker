package com.readingtracker.server.service;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.primary.MemoRepository;
import com.readingtracker.dbms.repository.primary.UserShelfBookRepository;
import com.readingtracker.dbms.repository.secondary.SecondaryMemoDao;
import com.readingtracker.server.common.constant.BookCategory;
import com.readingtracker.server.dto.responseDTO.BookMemoGroup;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TagMemoGroup;
import com.readingtracker.server.mapper.MemoMapper;
import com.readingtracker.server.service.write.DualMasterWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
// @Transactional 제거 (Dual Write를 위해)
public class MemoService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoService.class);
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Autowired
    private MemoMapper memoMapper;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
    @Autowired
    private DualMasterWriteService dualMasterWriteService;
    
    @Autowired
    private com.readingtracker.server.service.read.DualMasterReadService dualMasterReadService;
    
    @Autowired
    private SecondaryMemoDao secondaryMemoDao;
    
    @Autowired
    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    @Autowired
    private com.readingtracker.server.service.recovery.RecoveryQueueService recoveryQueueService;
    
    /**
     * 메모 작성
     * - 메모 내용 필수 검증
     * - 태그 자동 생성 및 연결
     * - 페이지당 메모 개수 제한 없음 (사용자가 자유롭게 기록 가능)
     * - pageNumber: 메모 작성 시점의 SESSION 모드 기준 초기 위치를 저장 (정렬 방식 변경 시에도 변경하지 않음)
     * 
     * 참고: ARCHITECTURE 원칙에 따라 Entity만 사용합니다.
     * RequestDTO → Entity 변환은 Mapper 계층에서 처리됩니다.
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public Memo createMemo(User user, Memo memo) {
        // 1. UserShelfBook 소유권 확인
        UserShelfBook userShelfBook = memo.getUserShelfBook();
        if (userShelfBook == null) {
            throw new IllegalArgumentException("책을 찾을 수 없습니다.");
        }
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 2. 태그 처리 확인
        // 태그는 Mapper에서 이미 Tag 엔티티 리스트로 변환되어 Entity에 설정됨
        // 추가 검증이 필요한 경우 여기서 수행
        
        // 3. Memo 엔티티 저장 (Dual Write)
        memo.setUser(user);
        
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> memoRepository.save(memo),
            
            // Secondary: JdbcTemplate 사용 (Primary 결과를 받아서 ID 사용)
            (jdbcTemplate, savedMemo) -> {
                NamedParameterJdbcTemplate namedTemplate = secondaryNamedParameterJdbcTemplate;
                
                // Memo INSERT (Primary에서 생성된 ID 사용)
                String insertMemoSql = "INSERT INTO memo (id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at) " +
                                     "VALUES (:id, :userId, :userShelfBookId, :pageNumber, :content, :memoStartTime, :createdAt, :updatedAt)";
                
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> memoParams = new java.util.HashMap<>();
                memoParams.put("id", savedMemo.getId());
                memoParams.put("userId", savedMemo.getUserId());
                memoParams.put("userShelfBookId", savedMemo.getUserShelfBookId());
                memoParams.put("pageNumber", savedMemo.getPageNumber());
                memoParams.put("content", savedMemo.getContent());
                memoParams.put("memoStartTime", savedMemo.getMemoStartTime());
                memoParams.put("createdAt", savedMemo.getCreatedAt() != null ? savedMemo.getCreatedAt() : now);
                memoParams.put("updatedAt", savedMemo.getUpdatedAt() != null ? savedMemo.getUpdatedAt() : now);
                
                namedTemplate.update(insertMemoSql, memoParams);
                
                // memo_tags INSERT (태그가 있는 경우)
                if (savedMemo.getTags() != null && !savedMemo.getTags().isEmpty()) {
                    String insertMemoTagsSql = "INSERT INTO memo_tags (memo_id, tag_id) VALUES (:memoId, :tagId)";
                    for (Tag tag : savedMemo.getTags()) {
                        Map<String, Object> tagParams = new java.util.HashMap<>();
                        tagParams.put("memoId", savedMemo.getId());
                        tagParams.put("tagId", tag.getId());
                        namedTemplate.update(insertMemoTagsSql, tagParams);
                    }
                }
                
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 DELETE
            (savedMemo) -> {
                if (savedMemo != null && savedMemo.getId() != null) {
                    memoRepository.deleteById(savedMemo.getId());
                }
                return null;
            },
            "Memo"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * 메모 수정
     * 
     * 참고: ARCHITECTURE 원칙에 따라 Entity만 사용합니다.
     * RequestDTO → Entity 변환은 Mapper 계층에서 처리됩니다.
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public Memo updateMemo(User user, Long memoId, Memo memo) {
        // 1. 메모 조회 및 소유권 확인
        if (memoId == null) {
            throw new IllegalArgumentException("메모 ID는 필수입니다.");
        }
        Memo existingMemo = memoRepository.findById(memoId)
            .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다."));
        
        if (!existingMemo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 2. UPDATE 전 원래 상태를 DB에서 조회 (보상 트랜잭션용)
        // 주의: existingMemo는 이미 조회된 상태이지만, 보상 트랜잭션에서 사용할 원래 상태를 명확히 저장
        final Memo originalState;
        if (existingMemo.getId() != null) {
            // DB에서 조회한 원래 상태를 복사
            originalState = new Memo();
            originalState.setId(existingMemo.getId());
            originalState.setContent(existingMemo.getContent());
            originalState.setPageNumber(existingMemo.getPageNumber());
            originalState.setMemoStartTime(existingMemo.getMemoStartTime());
            originalState.setUpdatedAt(existingMemo.getUpdatedAt());
            // 원래 태그 목록도 저장 (보상 트랜잭션에서 복구용)
            if (existingMemo.getTags() != null) {
                originalState.setTags(new java.util.ArrayList<>(existingMemo.getTags()));
            } else {
                originalState.setTags(new java.util.ArrayList<>());
            }
        } else {
            log.warn("보상 트랜잭션용 원래 상태 조회 실패: memoId가 null입니다.");
            originalState = null;
        }
        
        // 3. 필드 업데이트 (content, tags, pageNumber 수정 가능)
        // Mapper에서 이미 변환된 Entity의 필드만 업데이트
        // Mapper의 updateMemoFromRequest에서 이미 필드가 업데이트되었으므로 여기서는 저장만 수행
        
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                existingMemo.setContent(memo.getContent());
                existingMemo.setTags(memo.getTags());
                // pageNumber도 수정 가능 (Mapper에서 이미 설정됨)
                if (memo.getPageNumber() != null) {
                    existingMemo.setPageNumber(memo.getPageNumber());
                }
                existingMemo.setUpdatedAt(LocalDateTime.now());
                return memoRepository.save(existingMemo);
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, updatedMemo) -> {
                NamedParameterJdbcTemplate namedTemplate = secondaryNamedParameterJdbcTemplate;
                
                // Memo UPDATE (페이지 번호도 포함)
                String updateMemoSql = "UPDATE memo SET content = :content, page_number = :pageNumber, updated_at = :updatedAt WHERE id = :id";
                Map<String, Object> updateParams = new java.util.HashMap<>();
                updateParams.put("id", memoId);
                updateParams.put("content", updatedMemo.getContent());
                updateParams.put("pageNumber", updatedMemo.getPageNumber());
                updateParams.put("updatedAt", LocalDateTime.now());
                namedTemplate.update(updateMemoSql, updateParams);
                
                // memo_tags 삭제 후 재삽입
                String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
                Map<String, Object> deleteParams = new java.util.HashMap<>();
                deleteParams.put("memoId", memoId);
                namedTemplate.update(deleteMemoTagsSql, deleteParams);
                
                if (updatedMemo.getTags() != null && !updatedMemo.getTags().isEmpty()) {
                    String insertMemoTagsSql = "INSERT INTO memo_tags (memo_id, tag_id) VALUES (:memoId, :tagId)";
                    for (Tag tag : updatedMemo.getTags()) {
                        Map<String, Object> tagParams = new java.util.HashMap<>();
                        tagParams.put("memoId", memoId);
                        tagParams.put("tagId", tag.getId());
                        namedTemplate.update(insertMemoTagsSql, tagParams);
                    }
                }
                
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 원래 상태로 복구
            (updatedMemo) -> {
                if (updatedMemo != null && originalState != null) {
                    // DB에서 엔티티를 다시 조회하여 원래 상태로 복구
                    Memo memoToRestore = memoRepository.findById(updatedMemo.getId())
                        .orElseThrow(() -> new IllegalStateException("보상 트랜잭션: 복구할 메모를 찾을 수 없습니다. memoId=" + updatedMemo.getId()));
                    
                    // 원래 상태로 복구
                    memoToRestore.setContent(originalState.getContent());
                    memoToRestore.setPageNumber(originalState.getPageNumber());
                    memoToRestore.setMemoStartTime(originalState.getMemoStartTime());
                    memoToRestore.setUpdatedAt(originalState.getUpdatedAt());
                    
                    // 원래 태그 목록으로 복구
                    if (originalState.getTags() != null) {
                        memoToRestore.setTags(new java.util.ArrayList<>(originalState.getTags()));
                    } else {
                        memoToRestore.setTags(new java.util.ArrayList<>());
                    }
                    
                    // DB에 저장하여 롤백 완료
                    memoRepository.save(memoToRestore);
                    log.info("보상 트랜잭션: Primary DB에서 원래 상태로 복구 완료. memoId={}", updatedMemo.getId());
                } else {
                    log.warn("보상 트랜잭션: 원래 상태가 없어 복구할 수 없습니다. memoId={}", 
                            updatedMemo != null ? updatedMemo.getId() : "null");
                }
                return null;
            },
            "Memo"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * 메모 조회 (내부 사용)
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public Memo getMemoById(User user, Long memoId) {
        if (memoId == null) {
            throw new IllegalArgumentException("메모 ID는 필수입니다.");
        }
        
        // Dual Read Failover 적용
        Memo memo = dualMasterReadService.readWithFailover(
            () -> memoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다.")),
            () -> {
                Memo foundMemo = secondaryMemoDao.findByIdWithTags(memoId);
                if (foundMemo == null) {
                    throw new IllegalArgumentException("메모를 찾을 수 없습니다.");
                }
                return foundMemo;
            }
        );
        
        if (!memo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        return memo;
    }
    
    /**
     * 메모 삭제
     * 멱등성 보장: 이미 삭제된 메모에 대해서도 성공 응답을 반환하여 동일한 요청을 여러 번 실행해도 결과가 동일하도록 함
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void deleteMemo(User user, Long memoId) {
        if (memoId == null) {
            throw new IllegalArgumentException("메모 ID는 필수입니다.");
        }
        Memo memo = memoRepository.findById(memoId).orElse(null);
        
        // 이미 삭제된 메모인 경우 성공으로 처리 (멱등성 보장)
        if (memo == null) {
            return;
        }
        
        if (!memo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // Primary 선 삭제, Secondary는 베스트 에포트로 처리
        // Secondary 실패 시에도 예외를 던지지 않고 UI에 즉시 반영될 수 있도록 함
        // (Secondary 정리는 관리자/Recovery Queue로 처리)
        // 1) Primary 삭제
        memoRepository.delete(memo);
        
        // 2) Secondary 삭제 시도 (베스트 에포트)
        try {
            NamedParameterJdbcTemplate namedTemplate = secondaryNamedParameterJdbcTemplate;
            
            String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
            Map<String, Object> deleteTagsParams = new java.util.HashMap<>();
            deleteTagsParams.put("memoId", memoId);
            namedTemplate.update(deleteMemoTagsSql, deleteTagsParams);
            
            String deleteMemoSql = "DELETE FROM memo WHERE id = :id";
            Map<String, Object> deleteParams = new java.util.HashMap<>();
            deleteParams.put("id", memoId);
            namedTemplate.update(deleteMemoSql, deleteParams);
        } catch (Exception secondaryError) {
            log.warn("Secondary DB 메모 삭제 실패, Recovery Queue에 정리 요청 발행. memoId={}, error={}", memoId, secondaryError.getMessage());
            // Secondary 정리를 위해 Recovery Queue에 발행
            com.readingtracker.server.service.recovery.CompensationFailureEvent event = 
                new com.readingtracker.server.service.recovery.CompensationFailureEvent(
                    "DELETE_SECONDARY_CLEANUP",
                    memoId,
                    "Memo",
                    "Secondary",
                    java.time.Instant.now(),
                    "Primary DELETE 성공 후 Secondary DELETE 실패"
                );
            try {
                recoveryQueueService.publish(event);
            } catch (Exception queueError) {
                log.error("Recovery Queue 발행 실패: memoId={}, error={}", memoId, queueError.getMessage());
            }
        }
    }
    
    /**
     * 날짜 범위 계산 헬퍼 메서드
     * 
     * LocalDate를 LocalDateTime 범위로 변환합니다.
     * 향후 타임존 지원을 위해 구조화되어 있습니다.
     * 
     * 현재 구현: 서버 타임존 기준으로 처리
     * 향후 확장: 사용자 타임존 정보를 받아 해당 타임존 기준으로 계산 가능
     * 
     * @param date 조회할 날짜
     * @return 날짜 범위 (startOfDay, startOfNextDay)를 담은 배열
     */
    private LocalDateTime[] calculateDateRange(LocalDate date) {
        // 현재: 서버 타임존 기준으로 처리
        // 향후: 사용자 타임존을 받아 처리할 수 있도록 구조화
        // 예: ZoneId userTimeZone = getUserTimeZone(user);
        //     ZonedDateTime startOfDay = date.atStartOfDay(userTimeZone);
        //     return new LocalDateTime[] { 
        //         startOfDay.toLocalDateTime(), 
        //         startOfDay.plusDays(1).toLocalDateTime() 
        //     };
        
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();
        return new LocalDateTime[] { startOfDay, startOfNextDay };
    }
    
    /**
     * 오늘의 흐름 조회 (책별 그룹화)
     * 
     * 기능:
     * - 오늘 날짜의 메모 조회 (기본값)
     * - 과거 날짜의 메모 조회 가능 (독서 캘린더 연동)
     * - 책별로 그룹화하여 반환
     * - 각 책 그룹 내에서 타임라인 순으로 정렬 (memo_start_time 기준)
     * 
     * 백엔드 책임:
     * - Repository: book_id를 기준으로 데이터를 조회
     * - Service: 데이터를 책별로 그룹화하는 최종 변환 담당
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a)
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public Map<Long, BookMemoGroup> getTodayFlowGroupedByBook(User user, LocalDate date) {
        LocalDateTime[] dateRange = calculateDateRange(date);
        
        // Dual Read Failover 적용
        List<Memo> memos = dualMasterReadService.readWithFailover(
            () -> memoRepository.findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(
                user.getId(), dateRange[0], dateRange[1]),
            () -> {
                List<Memo> foundMemos = secondaryMemoDao.findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(
                    user.getId(), dateRange[0], dateRange[1]);
                return secondaryMemoDao.loadTagsForMemos(foundMemos);
            }
        );
        
        // null 체크: userShelfBook이 null인 메모는 필터링
        memos = memos.stream()
            .filter(m -> m.getUserShelfBook() != null && m.getUserShelfBook().getBook() != null)
            .collect(Collectors.toList());
        
        // 책별로 그룹화
        return memos.stream()
            .collect(Collectors.groupingBy(
                m -> m.getUserShelfBook().getId(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    memoList -> {
                        if (memoList.isEmpty()) {
                            return null; // 빈 리스트는 null 반환 (필터링됨)
                        }
                        
                        BookMemoGroup group = new BookMemoGroup();
                        Memo firstMemo = memoList.get(0);
                        UserShelfBook userShelfBook = firstMemo.getUserShelfBook();
                        
                        // null 체크 (이미 필터링했지만 안전을 위해)
                        if (userShelfBook == null || userShelfBook.getBook() == null) {
                            return null;
                        }
                        
                        group.setBookId(userShelfBook.getId());
                        group.setBookTitle(userShelfBook.getBook().getTitle());
                        group.setBookIsbn(userShelfBook.getBook().getIsbn());
                        group.setMemos(memoMapper.toMemoResponseList(memoList));
                        group.setMemoCount(memoList.size());
                        return group;
                    }
                )
            ))
            .entrySet().stream()
            .filter(entry -> entry.getValue() != null) // null 그룹 제거
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> v1,
                LinkedHashMap::new
            ));
    }
    
    /**
     * 오늘의 흐름 조회 (태그별 그룹화)
     * 
     * 기능:
     * - 태그별로 그룹화하여 반환 (1차 그룹화)
     * - 각 태그 그룹 내부에서 책별로 다시 그룹화 (2차 그룹화)
     * - 각 책 그룹 내에서 타임라인 순으로 정렬 (3차 정렬)
     * 
     * 대표 태그 결정 로직:
     * - 대표 태그 결정 규칙은 섹션 12.2.2를 참조하세요.
     * - tagCategory에 따라 대표 태그 결정 우선순위가 변경됩니다 (기본값: TYPE).
     * 
     * 백엔드 책임:
     * - Repository: 날짜 기준으로 모든 메모를 조회
     * - Service: 태그별로 그룹화한 후, 각 태그 그룹 내부에서 책별로 다시 그룹화하는 최종 변환 담당
     * 
     * @param tagCategory 태그 대분류 (TYPE 또는 TOPIC). 선택된 대분류가 대표 태그 결정 시 1순위가 됨 (기본값: TYPE)
     * @return 태그별로 그룹화된 메모 (태그 그룹 -> 책 그룹 -> 메모 구조)
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public Map<String, TagMemoGroup> getTodayFlowGroupedByTag(User user, LocalDate date, TagCategory tagCategory) {
        // 날짜 기준으로 모든 메모를 시간순으로 조회
        LocalDateTime[] dateRange = calculateDateRange(date);
        
        // Dual Read Failover 적용
        List<Memo> memos = dualMasterReadService.readWithFailover(
            () -> memoRepository.findByUserIdAndDateOrderByMemoStartTimeAsc(
                user.getId(), dateRange[0], dateRange[1]),
            () -> {
                List<Memo> foundMemos = secondaryMemoDao.findByUserIdAndDateOrderByMemoStartTimeAsc(
                    user.getId(), dateRange[0], dateRange[1]);
                return secondaryMemoDao.loadTagsForMemos(foundMemos);
            }
        );
        
        // 대표 태그 결정 헬퍼 메서드
        // tagCategory에 따라 우선순위가 변경됨:
        // - tagCategory가 선택되면 해당 대분류가 1순위
        // - 선택된 대분류 내에서는 sort_order가 가장 작은 태그를 대표 태그로 사용
        Function<Memo, Tag> getRepresentativeTag = (memo) -> {
            if (memo.getTags().isEmpty()) {
                return null;  // 태그가 없으면 null 반환 (나중에 "etc" 처리)
            }
            
            // 선택된 대분류의 태그들을 먼저 찾기
            List<Tag> categoryTags = memo.getTags().stream()
                .filter(tag -> tag.getCategory() == tagCategory)
                .sorted(Comparator.comparing(Tag::getSortOrder))
                .collect(Collectors.toList());
            
            if (!categoryTags.isEmpty()) {
                return categoryTags.get(0);  // 선택된 대분류의 첫 번째 태그 (sort_order 최소)
            }
            
            // 선택된 대분류에 태그가 없으면 다른 대분류의 태그 찾기
            TagCategory otherCategory = (tagCategory == TagCategory.TYPE) 
                ? TagCategory.TOPIC 
                : TagCategory.TYPE;
            List<Tag> otherCategoryTags = memo.getTags().stream()
                .filter(tag -> tag.getCategory() == otherCategory)
                .sorted(Comparator.comparing(Tag::getSortOrder))
                .collect(Collectors.toList());
            
            if (!otherCategoryTags.isEmpty()) {
                return otherCategoryTags.get(0);  // 다른 대분류의 첫 번째 태그
            }
            
            return null;  // 태그가 있지만 대분류가 맞지 않는 경우 (이론적으로 발생하지 않아야 함)
        };
        
        // 1차 그룹화: 태그별로 그룹화
        Map<String, List<Memo>> memosByTagCode = new LinkedHashMap<>();
        
        for (Memo memo : memos) {
            Tag representativeTag = getRepresentativeTag.apply(memo);
            String tagCode = (representativeTag == null) ? "etc" : representativeTag.getCode();
            memosByTagCode.computeIfAbsent(tagCode, k -> new ArrayList<>()).add(memo);
        }
        
        // 태그별 그룹을 sort_order 기준으로 정렬 (etc는 마지막)
        List<Map.Entry<String, List<Memo>>> sortedTagEntries = memosByTagCode.entrySet().stream()
            .sorted((e1, e2) -> {
                if ("etc".equals(e1.getKey())) return 1;  // etc는 항상 마지막
                if ("etc".equals(e2.getKey())) return -1;
                
                // 각 태그 그룹의 첫 번째 메모의 대표 태그 sort_order로 비교
                Tag tag1 = getRepresentativeTag.apply(e1.getValue().get(0));
                Tag tag2 = getRepresentativeTag.apply(e2.getValue().get(0));
                if (tag1 != null && tag2 != null) {
                    return tag1.getSortOrder().compareTo(tag2.getSortOrder());
                }
                return 0;
            })
            .collect(Collectors.toList());
        
        // 결과 구성: 태그 그룹 -> 책 그룹 -> 메모
        Map<String, TagMemoGroup> result = new LinkedHashMap<>();
        
        for (Map.Entry<String, List<Memo>> tagEntry : sortedTagEntries) {
            String tagCode = tagEntry.getKey();
            List<Memo> tagMemos = tagEntry.getValue();
            
            TagMemoGroup tagGroup = new TagMemoGroup();
            tagGroup.setTagCode(tagCode);
            tagGroup.setMemoCount(tagMemos.size());
            
            // 2차 그룹화: 각 태그 그룹 내에서 책별로 그룹화
            Map<Long, List<Memo>> memosByBook = tagMemos.stream()
                .collect(Collectors.groupingBy(m -> m.getUserShelfBook().getId()));
            
            Map<Long, BookMemoGroup> bookGroups = new LinkedHashMap<>();
            
            for (Map.Entry<Long, List<Memo>> bookEntry : memosByBook.entrySet()) {
                Long bookId = bookEntry.getKey();
                List<Memo> bookMemos = bookEntry.getValue();
                
                // 3차 정렬: 각 책 그룹 내에서 시간순으로 정렬
                bookMemos.sort(Comparator.comparing(Memo::getMemoStartTime));
                
                BookMemoGroup bookGroup = new BookMemoGroup();
                Memo firstMemo = bookMemos.get(0);
                bookGroup.setBookId(bookId);
                bookGroup.setBookTitle(firstMemo.getUserShelfBook().getBook().getTitle());
                bookGroup.setBookIsbn(firstMemo.getUserShelfBook().getBook().getIsbn());
                bookGroup.setMemoCount(bookMemos.size());
                
                // 메모를 MemoResponse로 변환
                List<MemoResponse> memoResponses = bookMemos.stream()
                    .map(memoMapper::toMemoResponse)
                    .collect(Collectors.toList());
                bookGroup.setMemos(memoResponses);
                
                bookGroups.put(bookId, bookGroup);
            }
            
            tagGroup.setMemosByBook(bookGroups);
            result.put(tagCode, tagGroup);
        }
        
        return result;
    }
    
    /**
     * 특정 날짜의 특정 책의 메모 조회 (오늘의 흐름에서 특정 책 필터링)
     * 
     * 기능 설명:
     * - 오늘의 흐름 화면에서 특정 책을 선택하면 해당 책의 메모만 표시
     * - 선택한 날짜에 작성된 메모만 조회
     * - 오늘의 흐름과 동일한 화면 구성으로 표시
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1)
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public List<Memo> getBookMemosByDate(User user, Long userBookId, LocalDate date) {
        if (userBookId == null) {
            throw new IllegalArgumentException("책 ID는 필수입니다.");
        }
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 날짜 범위 쿼리 사용 (인덱스 활용 최적화)
        LocalDateTime[] dateRange = calculateDateRange(date);
        
        // Dual Read Failover 적용
        return dualMasterReadService.readWithFailover(
            () -> memoRepository.findByUserIdAndUserShelfBookIdAndDate(
                user.getId(), userBookId, dateRange[0], dateRange[1]),
            () -> {
                List<Memo> foundMemos = secondaryMemoDao.findByUserIdAndUserShelfBookIdAndDate(
                    user.getId(), userBookId, dateRange[0], dateRange[1]);
                return secondaryMemoDao.loadTagsForMemos(foundMemos);
            }
        );
    }
    
    /**
     * 특정 책의 모든 메모 조회 (날짜 제한 없음)
     * 
     * 기능 설명:
     * - 특정 책에 작성된 모든 메모를 날짜 제한 없이 조회
     * - 오늘의 흐름 형식으로 표시 가능
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * - 가장 오래된 메모가 위에, 가장 최근 메모가 아래에 표시
     * 
     * 사용 시나리오:
     * - 책의 흐름 기능: 월별 책 목록 또는 내 서재 책 목록에서 선택한 책의 모든 메모 조회
     * - 전체 메모 조회: 선택한 책의 모든 메모를 오늘의 흐름 형식으로 조회 (날짜 제한 없음)
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        if (userBookId == null) {
            throw new IllegalArgumentException("책 ID는 필수입니다.");
        }
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 날짜 제한 없이 모든 메모 조회 (타임라인 순서)
        // Dual Read Failover 적용
        return dualMasterReadService.readWithFailover(
            () -> memoRepository.findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
                user.getId(), userBookId),
            () -> {
                List<Memo> foundMemos = secondaryMemoDao.findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
                    user.getId(), userBookId);
                return secondaryMemoDao.loadTagsForMemos(foundMemos);
            }
        );
    }
    
    /**
     * 최근 기간 내에 메모가 작성된 책 목록 조회
     * 
     * 기능:
     * - 최근 N개월 이내에 메모가 작성된 책들의 목록을 반환
     * - 각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬 (가장 최근에 메모를 작성한 책이 위에)
     * - 책 정보(제목, 저자, ISBN, 카테고리 등) 포함
     * 
     * 사용 시나리오:
     * - 책의 흐름 기능: 월별 책 목록 조회
     * - 최근 활동한 책들을 우선적으로 표시
     * - 내 서재와 연동하여 최근 메모 작성 책 목록 제공
     * 
     * 구현 방식:
     * 1. Repository에서 최근 기간 내에 메모가 작성된 책 ID와 최신 메모 작성 시간 조회
     * 2. 조회된 책 ID 목록을 기반으로 UserShelfBook 정보 조회
     * 3. 최신 메모 작성 시간 기준으로 정렬하여 반환
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public List<UserShelfBook> getBooksWithRecentMemos(User user, int monthsAgo) {
        // 최근 N개월 전 날짜 계산
        LocalDateTime startDate = LocalDateTime.now().minusMonths(monthsAgo);
        
        // 최근 기간 내에 메모가 작성된 책 ID 목록 조회 (최신 메모 작성 시간 기준 정렬)
        // Dual Read Failover 적용
        List<Object[]> results = dualMasterReadService.readWithFailover(
            () -> memoRepository.findUserShelfBookIdsWithLastMemoTime(
                user.getId(), startDate),
            () -> secondaryMemoDao.findUserShelfBookIdsWithLastMemoTime(
                user.getId(), startDate)
        );
        
        // 책 ID 목록 추출
        List<Long> bookIds = results.stream()
            .map(result -> (Long) result[0])
            .collect(Collectors.toList());
        
        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // UserShelfBook 정보 조회 (최신 메모 작성 시간 순서 유지)
        List<UserShelfBook> books = userShelfBookRepository.findAllById(bookIds);
        
        // 결과 순서를 최신 메모 작성 시간 기준으로 정렬
        Map<Long, LocalDateTime> lastMemoTimeMap = results.stream()
            .collect(Collectors.toMap(
                result -> (Long) result[0],
                result -> (LocalDateTime) result[1]
            ));
        
        // 정렬: 최신 메모 작성 시간 기준 내림차순
        // 사용자가 특정 책을 선택하여 삭제하면, 해당 책의 데이터와 해당 책에 작성된 메모의 데이터가 함께 전부 삭제됨 (ON DELETE CASCADE).
        // 이 경우 findAllById()는 삭제된 책을 찾지 못하므로 결과에서 자동으로 제외됨 (정상 동작).
        // getOrDefault()는 방어적 프로그래밍으로 동시성 이슈나 예외 상황에 대비.
        books.sort((b1, b2) -> {
            LocalDateTime time1 = lastMemoTimeMap.getOrDefault(b1.getId(), LocalDateTime.MIN);
            LocalDateTime time2 = lastMemoTimeMap.getOrDefault(b2.getId(), LocalDateTime.MIN);
            return time2.compareTo(time1); // 내림차순 (최신이 위에)
        });
        
        return books;
    }
    
    /**
     * 책 덮기 (독서 활동 종료)
     * 
     * 기능 요약:
     * - 마지막으로 읽은 페이지 수를 기록하고 독서 진행률을 업데이트합니다.
     * - 독서 진행률에 따라 카테고리가 자동으로 변경됩니다.
     * - 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.
     * - ToRead 카테고리 책의 경우 독서 시작일을 첫 메모 작성 날짜로 자동 설정
     * - Finished 카테고리로 변경될 경우 독서 종료일, 평점, 후기를 설정
     * 
     * 상세 기능 설명은 섹션 1.2 주요 요구사항의 "책 덮기" 항목 및 섹션 15를 참조하세요.
     * 
     * 참고: 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568)
     */
    public void closeBook(User user, Long userBookId, com.readingtracker.server.dto.requestDTO.CloseBookRequest request) {
        if (userBookId == null) {
            throw new IllegalArgumentException("책 ID는 필수입니다.");
        }
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        Integer lastReadPage = request.getLastReadPage();
        
        // 2. 페이지 수 유효성 검증
        if (lastReadPage == null || lastReadPage < 1) {
            throw new IllegalArgumentException("페이지 수는 1 이상이어야 합니다.");
        }
        
        // 전체 페이지 수 확인 및 검증
        Integer totalPages = userShelfBook.getBook() != null ? 
            userShelfBook.getBook().getTotalPages() : null;
        
        if (totalPages != null && lastReadPage > totalPages) {
            throw new IllegalArgumentException(
                String.format("페이지 수는 전체 페이지 수(%d페이지)를 초과할 수 없습니다.", totalPages)
            );
        }
        
        // 3. 진행률 업데이트
        userShelfBook.setReadingProgress(lastReadPage);
        
        // 4. 카테고리 자동 변경 로직
        // 진행률에 따라 카테고리 자동 변경
        // 진행률 계산: (lastReadPage / totalPages) * 100
        // 카테고리 변경 기준은 섹션 15.4.2 데이터베이스 업데이트의 "카테고리 변경 기준 (예시)"를 참조하세요.
        // 진행률 0%: ToRead
        // 진행률 1~80%: Reading
        // 진행률 81~99%: AlmostFinished
        // 진행률 100%: Finished
        BookCategory newCategory = null;
        if (totalPages != null && totalPages > 0) {
            double progressPercentage = (lastReadPage * 100.0) / totalPages;
            
            if (progressPercentage == 0) {
                newCategory = BookCategory.ToRead;
            } else if (progressPercentage >= 1 && progressPercentage <= 80) {
                newCategory = BookCategory.Reading;
            } else if (progressPercentage >= 81 && progressPercentage <= 99) {
                newCategory = BookCategory.AlmostFinished;
            } else { // progressPercentage >= 100
                newCategory = BookCategory.Finished;
            }
            
            // 카테고리 변경 (명시적 변경 플래그는 유지)
            userShelfBook.setCategory(newCategory);
        }
        
        // 5. ToRead 카테고리 책의 경우 독서 시작일 자동 설정
        if (userShelfBook.getCategory() == BookCategory.ToRead) {
            // ToRead 카테고리에서 다른 카테고리로 변경되는 경우
            // 첫 메모 작성 날짜를 독서 시작일로 설정
            List<Memo> memos = memoRepository.findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
                user.getId(), userBookId
            );
            
            if (!memos.isEmpty()) {
                Memo firstMemo = memos.get(0);
                LocalDate firstMemoDate = firstMemo.getMemoStartTime().toLocalDate();
                userShelfBook.setReadingStartDate(firstMemoDate);
            }
            
            // 구매/대여 항목은 null로 설정 (프론트엔드에서 '선택하세요'로 표시)
            userShelfBook.setPurchaseType(null);
        }
        
        // 6. Finished 카테고리로 변경될 경우 추가 필드 설정
        if (newCategory == BookCategory.Finished) {
            // 독서 종료일 검증 및 설정
            if (request.getReadingFinishedDate() == null) {
                throw new IllegalArgumentException("독서 종료일은 필수 입력 항목입니다.");
            }
            userShelfBook.setReadingFinishedDate(request.getReadingFinishedDate());
            
            // 평점 검증 및 설정
            if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
                throw new IllegalArgumentException("평점은 1 이상 5 이하여야 합니다.");
            }
            userShelfBook.setRating(request.getRating());
            
            // 후기 설정 (선택사항)
            userShelfBook.setReview(request.getReview());
        }
        
        // Dual Write 적용: UserShelfBook 업데이트
        updateUserShelfBookWithDualWrite(userShelfBook);
    }
    
    /**
     * UserShelfBook 업데이트를 Dual Write로 처리하는 헬퍼 메서드
     * (BookService의 동일한 메서드와 유사한 패턴 사용)
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
                return userShelfBookRepository.save(userShelfBook);
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
                
                Map<String, Object> updateParams = new java.util.HashMap<>();
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
                    userShelfBookRepository.save(userShelfBook);
                }
                return null;
            },
            "UserShelfBook"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * 특정 년/월에 메모가 작성된 날짜 목록 조회
     * 캘린더 표시용: 날짜 문자열 리스트 반환 (ISO 8601 형식: YYYY-MM-DD)
     * 
     * @param user 사용자
     * @param year 조회할 년도
     * @param month 조회할 월 (1-12)
     * @return 날짜 문자열 리스트 (예: ["2024-01-15", "2024-01-20"])
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public List<String> getMemoDates(User user, int year, int month) {
        // 해당 월의 시작일과 다음 달 시작일 계산
        LocalDateTime startOfMonth = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        
        // Dual Read Failover 적용
        List<LocalDateTime> memoStartTimes = dualMasterReadService.readWithFailover(
            () -> memoRepository.findMemoStartTimesByUserIdAndYearAndMonth(
                user.getId(), startOfMonth, startOfNextMonth),
            () -> secondaryMemoDao.findMemoStartTimesByUserIdAndYearAndMonth(
                user.getId(), startOfMonth, startOfNextMonth)
        );
        
        // LocalDateTime에서 날짜만 추출하여 중복 제거 및 정렬
        return memoStartTimes.stream()
            .map(LocalDateTime::toLocalDate)  // LocalDateTime → LocalDate 변환
            .distinct()  // 중복 제거
            .sorted()  // 날짜순 정렬
            .map(LocalDate::toString)  // ISO 8601 형식으로 변환
            .collect(Collectors.toList());
    }
}

