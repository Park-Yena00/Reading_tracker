package com.readingtracker.controller.v1;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Base V1", description = "API v1 기본 컨트롤러")
public abstract class BaseV1Controller {
    
    // 모든 v1 API 컨트롤러의 기본 클래스
    // 공통 기능이나 설정이 필요할 때 사용
}

