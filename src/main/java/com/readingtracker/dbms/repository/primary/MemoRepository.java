package com.readingtracker.dbms.repository.primary;

import com.readingtracker.dbms.entity.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MemoRepository extends JpaRepository<Memo, Long> {
    
    // 특정 사용자의 특정 책에 대한 메모 조회 (페이지별)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("userShelfBookId") Long userShelfBookId
    );
    
    // 특정 사용자의 특정 책의 특정 페이지에 대한 메모 개수 조회
    @Query("SELECT COUNT(m) FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "AND m.pageNumber = :pageNumber")
    long countByUserIdAndUserShelfBookIdAndPageNumber(
        @Param("userId") Long userId, 
        @Param("userShelfBookId") Long userShelfBookId, 
        @Param("pageNumber") Integer pageNumber
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (오늘의 흐름 - 시간순)
    // 태그별 그룹화를 위해 시간순으로만 정렬 (TAG 모드용)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (오늘의 흐름 - 책별 그룹화)
    // 책별로 그룹화: book_id를 기준으로 먼저 정렬하고, 각 책 그룹 내에서 타임라인 순으로 정렬
    // 기본 정렬: 메모가 작성된 시간(memo_start_time) 기준 시간순(오름차순)
    // 가장 오래된 메모가 첫 번째, 가장 최근 메모가 마지막에 위치
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.userShelfBook.id ASC, m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (태그별 정렬)
    // 태그별로 그룹화하고, 각 태그 그룹 내에서 타임라인 순으로 정렬
    @Query("SELECT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY t.sortOrder ASC, m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByTagAndMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 모든 메모 조회 (타임라인 정렬)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdOrderByMemoStartTimeAsc(@Param("userId") Long userId);
    
    // 특정 사용자의 특정 기간 메모 조회
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime BETWEEN :startDate AND :endDate " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // 태그별 메모 조회
    @Query("SELECT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId AND t.code = :tagCode " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByBookIdAndTagCode(
        @Param("bookId") Long bookId, 
        @Param("tagCode") String tagCode
    );
    
    // 태그별로 그룹화된 메모 조회
    @Query("SELECT t.code, m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId " +
           "ORDER BY t.sortOrder, m.memoStartTime ASC")
    List<Object[]> findMemosGroupedByTag(@Param("bookId") Long bookId);
    
    // 특정 날짜의 특정 책의 메모 조회
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndUserShelfBookIdAndDate(
        @Param("userId") Long userId,
        @Param("userShelfBookId") Long userShelfBookId,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 최근 기간 내에 메모가 작성된 책별 최신 메모 작성 시간 조회
    @Query("SELECT m.userShelfBook.id, MAX(m.memoStartTime) as lastMemoTime " +
           "FROM Memo m " +
           "WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startDate " +
           "GROUP BY m.userShelfBook.id " +
           "ORDER BY MAX(m.memoStartTime) DESC")
    List<Object[]> findUserShelfBookIdsWithLastMemoTime(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate
    );
    
    // 특정 사용자의 특정 년/월에 메모가 작성된 날짜 목록 조회 (중복 제거, 캘린더용)
    // 날짜 범위로 조회하여 애플리케이션 레벨에서 날짜만 추출
    // 문서: ARCHITECTURE.md 준수 - JPQL 사용, 네이티브 쿼리 지양
    @Query("SELECT m.memoStartTime FROM Memo m " +
           "WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfMonth " +
           "AND m.memoStartTime < :startOfNextMonth " +
           "ORDER BY m.memoStartTime ASC")
    List<LocalDateTime> findMemoStartTimesByUserIdAndYearAndMonth(
        @Param("userId") Long userId,
        @Param("startOfMonth") LocalDateTime startOfMonth,
        @Param("startOfNextMonth") LocalDateTime startOfNextMonth
    );
}

