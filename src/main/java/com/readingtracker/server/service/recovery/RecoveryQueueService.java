package com.readingtracker.server.service.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 보상 트랜잭션 실패 복구 큐 서비스
 * 
 * 현재는 인메모리 큐를 사용하며, 향후 메시지 큐(Kafka, RabbitMQ 등)로 전환 가능
 */
@Service
public class RecoveryQueueService {
    
    private static final Logger log = LoggerFactory.getLogger(RecoveryQueueService.class);
    
    private final BlockingQueue<CompensationFailureEvent> recoveryQueue = new LinkedBlockingQueue<>();
    private final List<CompensationFailureEvent> failedEvents = new ArrayList<>();
    
    /**
     * 복구 큐에 이벤트 발행
     */
    public void publish(CompensationFailureEvent event) {
        try {
            recoveryQueue.put(event);
            log.info("복구 큐에 이벤트 발행: {}", event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("복구 큐에 이벤트 발행 실패", e);
        }
    }
    
    /**
     * 복구 큐에서 이벤트 소비
     */
    public List<CompensationFailureEvent> consume() {
        List<CompensationFailureEvent> events = new ArrayList<>();
        recoveryQueue.drainTo(events);
        return events;
    }
    
    /**
     * 복구 성공 시 이벤트 확인
     */
    public void acknowledge(CompensationFailureEvent event) {
        log.info("복구 성공 확인: {}", event);
        // 인메모리 큐에서는 별도 처리 불필요
    }
    
    /**
     * 복구 실패 시 재큐잉
     */
    public void requeue(CompensationFailureEvent event) {
        try {
            recoveryQueue.put(event);
            log.info("복구 이벤트 재큐잉: {}", event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("복구 이벤트 재큐잉 실패", e);
        }
    }
    
    /**
     * 복구 실패로 마킹
     */
    public void markAsFailed(CompensationFailureEvent event) {
        synchronized (failedEvents) {
            failedEvents.add(event);
        }
        log.error("복구 실패로 마킹: {}", event);
    }
    
    /**
     * 실패한 이벤트 목록 조회
     */
    public List<CompensationFailureEvent> getFailedEvents() {
        synchronized (failedEvents) {
            return new ArrayList<>(failedEvents);
        }
    }
}

