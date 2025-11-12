-- user_books 테이블에 category_manually_set 컬럼 추가
-- 명시적으로 카테고리를 변경했는지 여부를 추적하는 플래그
ALTER TABLE user_books
    ADD COLUMN category_manually_set BOOLEAN NOT NULL DEFAULT FALSE AFTER category;

-- 인덱스 추가 (선택사항, 쿼리 성능 최적화용)
CREATE INDEX idx_user_books_category_manually_set ON user_books(category_manually_set);

