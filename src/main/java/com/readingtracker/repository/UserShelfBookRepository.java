package com.readingtracker.repository;

import com.readingtracker.common.BookCategory;
import com.readingtracker.entity.UserShelfBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserShelfBookRepository extends JpaRepository<UserShelfBook, Long> {
    
    // 특정 사용자의 모든 UserBook 조회
    @Query("SELECT ub FROM UserShelfBook ub WHERE ub.user.id = :userId")
    List<UserShelfBook> findByUserId(@Param("userId") Long userId);
    
    // 특정 사용자의 특정 카테고리 UserBook 조회
    @Query("SELECT ub FROM UserShelfBook ub WHERE ub.user.id = :userId AND ub.category = :category")
    List<UserShelfBook> findByUserIdAndCategory(@Param("userId") Long userId, @Param("category") BookCategory category);
    
    // 특정 사용자의 특정 책이 이미 저장되어 있는지 확인
    @Query("SELECT ub FROM UserShelfBook ub WHERE ub.user.id = :userId AND ub.book.id = :bookId")
    Optional<UserShelfBook> findByUserIdAndBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);
    
    // 특정 사용자의 UserBook 개수 조회
    @Query("SELECT COUNT(ub) FROM UserShelfBook ub WHERE ub.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
    
    // 특정 사용자의 특정 카테고리 UserBook 개수 조회
    @Query("SELECT COUNT(ub) FROM UserShelfBook ub WHERE ub.user.id = :userId AND ub.category = :category")
    Long countByUserIdAndCategory(@Param("userId") Long userId, @Param("category") BookCategory category);
    
    // 특정 사용자의 특정 ISBN으로 저장된 책 조회
    @Query("SELECT ub FROM UserShelfBook ub JOIN ub.book b WHERE ub.user.id = :userId AND b.isbn = :isbn")
    Optional<UserShelfBook> findByUserIdAndBookIsbn(@Param("userId") Long userId, @Param("isbn") String isbn);
    
    // 정렬된 조회 - 추가된 날짜 순
    @Query("SELECT ub FROM UserShelfBook ub WHERE ub.user.id = :userId ORDER BY ub.createdAt DESC")
    List<UserShelfBook> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    // 카테고리별 정렬된 조회
    @Query("SELECT ub FROM UserShelfBook ub WHERE ub.user.id = :userId AND ub.category = :category ORDER BY ub.createdAt DESC")
    List<UserShelfBook> findByUserIdAndCategoryOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("category") BookCategory category);
}





