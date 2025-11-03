-- refresh_tokens 테이블의 token 컬럼 크기 확장
-- JWT 토큰은 보통 400-600자이므로 TEXT 타입으로 변경
ALTER TABLE refresh_tokens 
MODIFY COLUMN token TEXT NOT NULL;



