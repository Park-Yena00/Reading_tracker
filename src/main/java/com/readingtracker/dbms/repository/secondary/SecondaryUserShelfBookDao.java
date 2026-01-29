package com.readingtracker.dbms.repository.secondary;

import com.readingtracker.dbms.entity.Book;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.server.common.constant.BookCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Secondary DB 전용 UserShelfBook DAO
 * 
 * PrimaryDB 연결이 끊겼을 때 SecondaryDB에서 UserShelfBook 데이터를 읽기 위한 DAO
 * JdbcTemplate을 사용하여 SecondaryDB에 직접 접근합니다.
 * Book과 JOIN하여 조회합니다.
 */
@Repository
@ConditionalOnProperty(name = "spring.datasource.secondary.enabled", havingValue = "true", matchIfMissing = false)
public class SecondaryUserShelfBookDao {
    
    @Autowired(required = false)
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * UserShelfBook RowMapper (Book JOIN 포함)
     */
    private static class UserShelfBookRowMapper implements RowMapper<UserShelfBook> {
        @Override
        public UserShelfBook mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserShelfBook userShelfBook = new UserShelfBook();
            userShelfBook.setId(rs.getLong("ub_id"));
            
            // User는 LAZY 로딩이므로 ID만 저장 (필요시 별도로 조회)
            
            // Book 정보 설정
            Book book = new Book();
            book.setId(rs.getLong("book_id"));
            book.setIsbn(rs.getString("isbn"));
            book.setTitle(rs.getString("title"));
            book.setAuthor(rs.getString("author"));
            book.setPublisher(rs.getString("publisher"));
            book.setDescription(rs.getString("description"));
            book.setCoverUrl(rs.getString("cover_url"));
            
            Integer totalPages = rs.getInt("total_pages");
            if (!rs.wasNull()) {
                book.setTotalPages(totalPages);
            }
            
            book.setMainGenre(rs.getString("main_genre"));
            
            java.sql.Date pubDate = rs.getDate("pub_date");
            if (pubDate != null) {
                book.setPubDate(pubDate.toLocalDate());
            }
            
            java.sql.Timestamp bookCreatedAt = rs.getTimestamp("book_created_at");
            if (bookCreatedAt != null) {
                book.setCreatedAt(bookCreatedAt.toLocalDateTime());
            }
            
            java.sql.Timestamp bookUpdatedAt = rs.getTimestamp("book_updated_at");
            if (bookUpdatedAt != null) {
                book.setUpdatedAt(bookUpdatedAt.toLocalDateTime());
            }
            
            userShelfBook.setBook(book);
            
            // UserShelfBook 필드 설정
            String categoryStr = rs.getString("category");
            if (categoryStr != null) {
                userShelfBook.setCategory(BookCategory.valueOf(categoryStr));
            }
            
            userShelfBook.setCategoryManuallySet(rs.getBoolean("category_manually_set"));
            userShelfBook.setExpectation(rs.getString("expectation"));
            
            java.sql.Date readingStartDate = rs.getDate("reading_start_date");
            if (readingStartDate != null) {
                userShelfBook.setReadingStartDate(readingStartDate.toLocalDate());
            }
            
            Integer readingProgress = rs.getInt("reading_progress");
            if (!rs.wasNull()) {
                userShelfBook.setReadingProgress(readingProgress);
            }
            
            String purchaseTypeStr = rs.getString("purchase_type");
            if (purchaseTypeStr != null) {
                userShelfBook.setPurchaseType(com.readingtracker.server.common.constant.PurchaseType.valueOf(purchaseTypeStr));
            }
            
            java.sql.Date readingFinishedDate = rs.getDate("reading_finished_date");
            if (readingFinishedDate != null) {
                userShelfBook.setReadingFinishedDate(readingFinishedDate.toLocalDate());
            }
            
            Integer rating = rs.getInt("rating");
            if (!rs.wasNull()) {
                userShelfBook.setRating(rating);
            }
            
            userShelfBook.setReview(rs.getString("review"));
            
            // createdAt과 updatedAt은 @CreatedDate, @LastModifiedDate로 자동 관리되므로 설정하지 않음
            // JPA Auditing과 동일한 동작을 위해 여기서는 설정하지 않음
            
            return userShelfBook;
        }
    }
    
    private static final UserShelfBookRowMapper USER_SHELF_BOOK_ROW_MAPPER = new UserShelfBookRowMapper();
    
    /**
     * 카테고리별 정렬된 조회 - 도서명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdAndCategoryOrderByTitleAsc(Long userId, BookCategory category) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId AND ub.category = :category " +
                    "ORDER BY b.title ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("category", category.name());
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 카테고리별 정렬된 조회 - 저자명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdAndCategoryOrderByAuthorAsc(Long userId, BookCategory category) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId AND ub.category = :category " +
                    "ORDER BY b.author ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("category", category.name());
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 카테고리별 정렬된 조회 - 출판사명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdAndCategoryOrderByPublisherAsc(Long userId, BookCategory category) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId AND ub.category = :category " +
                    "ORDER BY b.publisher ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("category", category.name());
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 카테고리별 정렬된 조회 - 메인 장르 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdAndCategoryOrderByGenreAsc(Long userId, BookCategory category) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId AND ub.category = :category " +
                    "ORDER BY b.main_genre ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("category", category.name());
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 정렬된 조회 - 도서명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdOrderByTitleAsc(Long userId) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId " +
                    "ORDER BY b.title ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 정렬된 조회 - 저자명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdOrderByAuthorAsc(Long userId) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId " +
                    "ORDER BY b.author ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 정렬된 조회 - 출판사명 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdOrderByPublisherAsc(Long userId) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId " +
                    "ORDER BY b.publisher ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
    
    /**
     * 정렬된 조회 - 메인 장르 기준 오름차순
     */
    public List<UserShelfBook> findByUserIdOrderByGenreAsc(Long userId) {
        String sql = "SELECT ub.id as ub_id, ub.user_id, ub.book_id, ub.category, ub.category_manually_set, " +
                    "ub.expectation, ub.reading_start_date, ub.reading_progress, ub.purchase_type, " +
                    "ub.reading_finished_date, ub.rating, ub.review, ub.created_at as ub_created_at, ub.updated_at as ub_updated_at, " +
                    "b.id as book_id, b.isbn, b.title, b.author, b.publisher, b.description, b.cover_url, " +
                    "b.total_pages, b.main_genre, b.pub_date, b.created_at as book_created_at, b.updated_at as book_updated_at " +
                    "FROM user_books ub " +
                    "JOIN books b ON ub.book_id = b.id " +
                    "WHERE ub.user_id = :userId " +
                    "ORDER BY b.main_genre ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_SHELF_BOOK_ROW_MAPPER);
    }
}

