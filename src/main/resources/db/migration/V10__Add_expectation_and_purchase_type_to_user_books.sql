-- user_books 테이블에 expectation과 purchase_type 컬럼 추가
ALTER TABLE user_books
    ADD COLUMN expectation VARCHAR(500) NULL AFTER category,
    ADD COLUMN purchase_type ENUM('PURCHASE', 'RENTAL') NULL AFTER reading_progress;

