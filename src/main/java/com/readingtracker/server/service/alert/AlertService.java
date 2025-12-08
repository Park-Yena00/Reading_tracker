package com.readingtracker.server.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 알림 서비스
 * 
 * 시스템의 심각한 오류(CRITICAL) 발생 시 운영팀에 알림을 발송합니다.
 * 
 * 현재 구현:
 * - 로그 기반 알림 (CRITICAL 로그 기록)
 * 
 * 향후 확장 가능:
 * - SMS 알림
 * - 이메일 알림
 * - 슬랙 알림
 * - 모니터링 시스템 연동 (Prometheus + AlertManager)
 */
@Service
public class AlertService {
    
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    
    /**
     * CRITICAL 알림 발송
     * 
     * 시스템이 스스로 해결할 수 없는 심각한 오류 발생 시 운영팀에 알림을 발송합니다.
     * 
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public void sendCriticalAlert(String title, String message) {
        // CRITICAL 레벨 로그 기록 (가장 높은 우선순위)
        log.error("CRITICAL ALERT: {} - {}", title, message);
        
        // TODO: 향후 확장 가능한 알림 채널
        // - SMS 알림 발송
        // - 이메일 알림 발송
        // - 슬랙 알림 발송
        // - 모니터링 시스템 연동 (Prometheus + AlertManager)
        
        // 현재는 CRITICAL 로그를 통해 알림을 기록합니다.
        // 운영팀은 로그 모니터링 시스템을 통해 이 알림을 감지할 수 있습니다.
    }
    
    /**
     * WARNING 알림 발송
     * 
     * 주의가 필요한 상황 발생 시 알림을 발송합니다.
     * 
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public void sendWarningAlert(String title, String message) {
        log.warn("WARNING ALERT: {} - {}", title, message);
        
        // TODO: 향후 확장 가능한 알림 채널
    }
    
    /**
     * INFO 알림 발송
     * 
     * 정보성 알림을 발송합니다.
     * 
     * @param title 알림 제목
     * @param message 알림 메시지
     */
    public void sendInfoAlert(String title, String message) {
        log.info("INFO ALERT: {} - {}", title, message);
        
        // TODO: 향후 확장 가능한 알림 채널
    }
}

