package com.readingtracker.server.service.recovery;

import com.readingtracker.dbms.repository.primary.MemoRepository;
import com.readingtracker.server.service.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보상 트랜잭션 실패 복구 작업자
 * 
 * 주기적으로 복구 큐를 확인하여 실패한 보상 트랜잭션을 재시도합니다.
 */
@Service
public class CompensationRecoveryWorker {
    
    private static final Logger log = LoggerFactory.getLogger(CompensationRecoveryWorker.class);
    private static final int MAX_RETRY_COUNT = 10;
    
    @Autowired
    private RecoveryQueueService recoveryQueueService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    @Autowired
    private AlertService alertService;
    
    /**
     * 복구 큐에서 실패한 보상 트랜잭션을 처리
     * 1분마다 실행 (백그라운드 스레드)
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void processRecoveryQueue() {
        List<CompensationFailureEvent> events = recoveryQueueService.consume();
        
        if (events.isEmpty()) {
            return;
        }
        
        log.info("복구 큐 처리 시작: {} 개 이벤트", events.size());
        
        for (CompensationFailureEvent event : events) {
            processRecoveryEvent(event);
        }
    }
    
    /**
     * 개별 복구 이벤트 처리
     */
    private void processRecoveryEvent(CompensationFailureEvent event) {
        try {
            String action = event.getCompensationAction();
            
            // Secondary DB 동기화 재시도 (SECONDARY_SYNC_RETRY)
            // 보상 트랜잭션 실패 시 DualMasterWriteService에서 발행된 이벤트
            if ("SECONDARY_SYNC_RETRY".equals(action)) {
                Long entityId = event.getEntityId();
                String entityType = event.getEntityType();
                int deletedRows = 0;
                
                if ("Memo".equals(entityType)) {
                    // Secondary DB에서 memo_tags 삭제
                    String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
                    Map<String, Object> deleteTagsParams = new HashMap<>();
                    deleteTagsParams.put("memoId", entityId);
                    secondaryNamedParameterJdbcTemplate.update(deleteMemoTagsSql, deleteTagsParams);
                    
                    // Secondary DB에서 memo 삭제
                    String deleteMemoSql = "DELETE FROM memo WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteMemoSql, deleteParams);
                } else if ("UserShelfBook".equals(entityType)) {
                    // Secondary DB에서 user_books 삭제
                    String deleteUserShelfBookSql = "DELETE FROM user_books WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserShelfBookSql, deleteParams);
                } else if ("UserDevice".equals(entityType)) {
                    // Secondary DB에서 user_devices 삭제
                    String deleteUserDeviceSql = "DELETE FROM user_devices WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserDeviceSql, deleteParams);
                } else if ("User".equals(entityType)) {
                    // Secondary DB에서 users 삭제
                    String deleteUserSql = "DELETE FROM users WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserSql, deleteParams);
                } else if ("Book".equals(entityType)) {
                    // Secondary DB에서 books 삭제
                    String deleteBookSql = "DELETE FROM books WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteBookSql, deleteParams);
                } else {
                    log.warn("지원하지 않는 엔티티 타입: {}", entityType);
                    return;
                }
                
                if (deletedRows > 0) {
                    log.info("Secondary 동기화 재시도 성공: entityType={}, entityId={}, deletedRows={}", 
                             entityType, entityId, deletedRows);
                } else {
                    log.warn("Secondary 동기화 재시도: 데이터가 이미 정리되었거나 존재하지 않음: entityType={}, entityId={}", 
                             entityType, entityId);
                }
                
                recoveryQueueService.acknowledge(event);
            }
            // Primary DB에서 DELETE 실행 (보상 트랜잭션 실패 복구) - 레거시 지원
            else if ("DELETE".equals(action)) {
                memoRepository.deleteById(event.getEntityId());
                log.info("복구 성공: entityType={}, entityId={}", 
                         event.getEntityType(), event.getEntityId());
                recoveryQueueService.acknowledge(event);
            }
            // Secondary DB에서 유령 데이터 정리 (DELETE_SECONDARY_CLEANUP) - 레거시 지원
            else if ("DELETE_SECONDARY_CLEANUP".equals(action)) {
                Long entityId = event.getEntityId();
                String entityType = event.getEntityType();
                int deletedRows = 0;
                
                if ("Memo".equals(entityType)) {
                    // Secondary DB에서 memo_tags 삭제
                    String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
                    Map<String, Object> deleteTagsParams = new HashMap<>();
                    deleteTagsParams.put("memoId", entityId);
                    secondaryNamedParameterJdbcTemplate.update(deleteMemoTagsSql, deleteTagsParams);
                    
                    // Secondary DB에서 memo 삭제
                    String deleteMemoSql = "DELETE FROM memo WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteMemoSql, deleteParams);
                } else if ("UserShelfBook".equals(entityType)) {
                    // Secondary DB에서 user_books 삭제
                    String deleteUserShelfBookSql = "DELETE FROM user_books WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserShelfBookSql, deleteParams);
                } else if ("UserDevice".equals(entityType)) {
                    // Secondary DB에서 user_devices 삭제
                    String deleteUserDeviceSql = "DELETE FROM user_devices WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserDeviceSql, deleteParams);
                } else {
                    log.warn("지원하지 않는 엔티티 타입: {}", entityType);
                    return;
                }
                
                if (deletedRows > 0) {
                    log.info("Secondary 유령 데이터 정리 성공: entityType={}, entityId={}, deletedRows={}", 
                             entityType, entityId, deletedRows);
                } else {
                    log.warn("Secondary 유령 데이터가 이미 정리되었거나 존재하지 않음: entityType={}, entityId={}", 
                             entityType, entityId);
                }
                
                recoveryQueueService.acknowledge(event);
            } else {
                log.warn("지원하지 않는 보상 액션: {}", action);
            }
        } catch (Exception e) {
            handleRecoveryFailure(event, e);
        }
    }
    
    /**
     * 복구 실패 처리
     */
    private void handleRecoveryFailure(CompensationFailureEvent event, Exception e) {
        int retryCount = event.incrementRetryCount();
        log.warn("복구 재시도 실패: entityId={}, retryCount={}", 
                event.getEntityId(), retryCount);
        
        if (retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 시 수동 개입 필요 알림
            log.error("CRITICAL: 복구 작업 최대 재시도 횟수 초과: entityType={}, entityId={}, retryCount={}, 수동 개입 필요", 
                     event.getEntityType(), event.getEntityId(), retryCount);
            
            // AlertService를 통한 CRITICAL 알림 발송
            String alertTitle = "복구 작업 최대 재시도 횟수 초과";
            String alertMessage = String.format(
                "복구 작업이 최대 재시도 횟수(%d회)를 초과하여 실패했습니다. " +
                "시스템이 스스로 해결할 수 없는 데이터 정합성 오류입니다. " +
                "즉시 수동 개입이 필요합니다.\n" +
                "- Entity Type: %s\n" +
                "- Entity ID: %d\n" +
                "- Action: %s\n" +
                "- Target DB: %s\n" +
                "- Failure Time: %s\n" +
                "- Error Message: %s",
                MAX_RETRY_COUNT,
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getTargetDB(),
                event.getFailureTime(),
                event.getErrorMessage()
            );
            
            alertService.sendCriticalAlert(alertTitle, alertMessage);
            
            recoveryQueueService.markAsFailed(event);
        } else {
            // 재시도 큐에 다시 추가
            recoveryQueueService.requeue(event);
        }
    }
}

