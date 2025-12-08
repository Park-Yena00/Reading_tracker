package com.readingtracker.dbms.repository.primary;

import com.readingtracker.dbms.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    
    /**
     * ISBN으로 책 조회
     */
    Optional<Book> findByIsbn(String isbn);
    
    /**
     * ISBN으로 책 존재 여부 확인
     */
    boolean existsByIsbn(String isbn);
}

