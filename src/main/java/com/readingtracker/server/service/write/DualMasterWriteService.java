package com.readingtracker.server.service.write;

import com.readingtracker.server.common.exception.DatabaseWriteException;
import com.readingtracker.server.service.recovery.CompensationFailureEvent;
import com.readingtracker.server.service.recovery.RecoveryQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.function.Function;

/**
 * MySQL 이중화를 위한 Custom Dual Write 서비스
 * 
 * Dual-Master 방식: Primary DB와 Secondary DB 둘 다 필수
 * 
 * 동작 방식:
 * - Primary DB에 먼저 쓰기 → 성공 시 Secondary DB에 쓰기
 * - Secondary 실패 시 Primary에 보상 트랜잭션 실행
 * 
 * 주의: Secondary DB가 비활성화된 경우 서비스 사용 불가 (예외 발생)
 * 서버는 시작되지만, 실제 Read/Write 작업 시도 시 서비스가 차단됩니다.
 */
@Service
public class DualMasterWriteService {
    
    private static final Logger log = LoggerFactory.getLogger(DualMasterWriteService.class);
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired(required = false)
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired(required = false)
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    @Autowired
    private RecoveryQueueService recoveryQueueService;
    
    /**
     * Custom Dual Write: Primary → Secondary 순차 쓰기
     * Secondary 실패 시 Primary에 보상 트랜잭션 실행
     * 
     * Primary는 JPA Repository를 사용하고, Secondary는 JdbcTemplate을 사용합니다.
     * 
     * @param primaryWriteOperation Primary DB에 쓰기 작업 (JPA Repository 사용)
     * @param secondaryWriteOperation Secondary DB에 쓰기 작업 (JdbcTemplate 사용, Primary 결과를 받음)
     * @param compensationOperation Secondary 실패 시 Primary에 실행할 보상 트랜잭션
     * @param entityType 엔티티 타입 (예: "Memo", "UserShelfBook", "UserDevice") - Recovery Queue 발행용
     * @return Primary DB 쓰기 결과
     */
    public <T> T writeWithDualWrite(
            java.util.function.Supplier<T> primaryWriteOperation,
            java.util.function.BiFunction<JdbcTemplate, T, Void> secondaryWriteOperation,
            Function<T, Void> compensationOperation,
            String entityType) {
        
        // Phase 1: Primary에 쓰기 (JPA Repository 사용)
        final T primaryResult;
        try {
            TransactionTemplate primaryTx = new TransactionTemplate(primaryTxManager);
            primaryResult = primaryTx.execute(status -> primaryWriteOperation.get());
            
            log.debug("Primary DB 쓰기 성공");
            
        } catch (Exception e) {
            // Primary 실패 시 즉시 Exception (Secondary로 Failover 불가)
            log.error("Primary DB 쓰기 실패", e);
            throw new DatabaseWriteException("Primary DB 쓰기 실패", e);
        }
        
        // Phase 2: Secondary에 쓰기 (JdbcTemplate 사용, Primary 결과 전달)
        // Dual-Master 방식이므로 Secondary DB가 필수입니다.
        if (secondaryTxManager == null || secondaryJdbcTemplate == null) {
            log.error("Secondary DB가 설정되지 않음. Dual-Master 방식에서는 Secondary DB가 필수입니다.");
            // Primary에 보상 트랜잭션 실행 (Primary에 이미 쓰기가 완료되었으므로)
            try {
                TransactionTemplate compensationTx = new TransactionTemplate(primaryTxManager);
                compensationTx.execute(status -> {
                    compensationOperation.apply(primaryResult);
                    return null;
                });
                log.info("Secondary DB 미설정으로 인한 보상 트랜잭션 실행 완료");
            } catch (Exception compensationError) {
                log.error("CRITICAL: Secondary DB 미설정으로 인한 보상 트랜잭션 실행 실패", compensationError);
            }
            throw new DatabaseWriteException("Secondary DB가 설정되지 않아 서비스를 사용할 수 없습니다. Dual-Master 방식에서는 Secondary DB가 필수입니다.");
        }
        
        try {
            TransactionTemplate secondaryTx = new TransactionTemplate(secondaryTxManager);
            secondaryTx.execute(status -> {
                secondaryWriteOperation.apply(secondaryJdbcTemplate, primaryResult);
                return null;
            });
            
            log.debug("Secondary DB 쓰기 성공");
            
            // 양쪽 모두 성공
            return primaryResult;
            
        } catch (Exception e) {
            // Secondary 실패 시 Primary에 보상 트랜잭션 실행
            log.error("Secondary DB 쓰기 실패, Primary에 보상 트랜잭션 실행", e);
            
            // 데모 시연용: Primary DB 변경사항이 잠시 적용된 상태를 확인하기 위한 대기 시간
            log.info("데모 시연: Primary DB 변경사항 확인을 위해 3초 대기 중... (보상 트랜잭션 실행 전)");
            try {
                Thread.sleep(3000); // 3초 대기
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("대기 시간 중 인터럽트 발생", ie);
            }
            log.info("대기 완료. 보상 트랜잭션 실행 시작...");
            
            final T finalPrimaryResult = primaryResult;
            try {
                TransactionTemplate compensationTx = new TransactionTemplate(primaryTxManager);
                compensationTx.execute(status -> {
                    compensationOperation.apply(finalPrimaryResult);
                    return null;
                });
                
                log.info("보상 트랜잭션 실행 성공");
                
            } catch (Exception compensationError) {
                log.error("CRITICAL: 보상 트랜잭션 실행 실패", compensationError);
                
                // Recovery Queue에 이벤트 발행 (Secondary DB 정리를 위한 재시도 요청)
                try {
                    Long entityId = extractEntityId(finalPrimaryResult);
                    if (entityId != null && entityType != null) {
                        CompensationFailureEvent event = new CompensationFailureEvent(
                            "SECONDARY_SYNC_RETRY",  // Secondary DB 동기화 재시도
                            entityId,
                            entityType,
                            "Secondary",  // 대상 DB
                            Instant.now(),  // 실패 시간
                            compensationError.getMessage()  // 에러 메시지
                        );
                        
                        recoveryQueueService.publish(event);
                        
                        log.error("CRITICAL: 보상 트랜잭션 실패로 인한 데이터 불일치 발생. " +
                                  "entityType: {}, entityId: {}, failureTime: {}, error: {}. " +
                                  "Recovery Queue에 발행됨. CompensationRecoveryWorker가 자동 복구를 시도합니다.",
                                  event.getEntityType(), 
                                  event.getEntityId(), 
                                  event.getFailureTime(), 
                                  compensationError.getMessage());
                    } else {
                        log.warn("Recovery Queue 발행 실패: entityId 또는 entityType이 null입니다. entityId={}, entityType={}", 
                                entityId, entityType);
                    }
                } catch (Exception queueError) {
                    log.error("Recovery Queue 발행 중 오류 발생", queueError);
                    // Recovery Queue 발행 실패는 로깅만 하고 계속 진행
                }
            }
            
            // Secondary 실패 Exception 발생
            throw new DatabaseWriteException("Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행됨", e);
        }
    }
    
    /**
     * 엔티티에서 ID를 추출하는 헬퍼 메서드
     * Reflection을 사용하여 getId() 메서드를 호출합니다.
     * 
     * @param entity 엔티티 객체
     * @return 엔티티 ID (추출 실패 시 null)
     */
    private Long extractEntityId(Object entity) {
        if (entity == null) {
            return null;
        }
        
        try {
            // getId() 메서드 찾기
            Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            
            if (id instanceof Long) {
                return (Long) id;
            } else if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            
            return null;
        } catch (Exception e) {
            log.debug("엔티티 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Custom Dual Write: Primary → Secondary 순차 쓰기 (entityType 없이 호출)
     * 
     * @deprecated entityType 파라미터를 포함한 메서드를 사용하세요.
     * 이 메서드는 하위 호환성을 위해 유지되며, entityType이 null인 경우 Recovery Queue 발행이 생략됩니다.
     */
    @Deprecated
    public <T> T writeWithDualWrite(
            java.util.function.Supplier<T> primaryWriteOperation,
            java.util.function.BiFunction<JdbcTemplate, T, Void> secondaryWriteOperation,
            Function<T, Void> compensationOperation) {
        return writeWithDualWrite(primaryWriteOperation, secondaryWriteOperation, compensationOperation, null);
    }
}

