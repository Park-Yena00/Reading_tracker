package com.readingtracker.dbms.repository.primary;

import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByCode(String code);
    boolean existsByCode(String code);
    List<Tag> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(TagCategory category);
    List<Tag> findByIsActiveTrueOrderBySortOrderAsc();
}

