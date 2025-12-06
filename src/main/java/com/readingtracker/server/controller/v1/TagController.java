package com.readingtracker.server.controller.v1;

import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "태그", description = "태그 조회 API")
public class TagController extends BaseV1Controller {
    
    @Autowired
    private TagService tagService;
    
    /**
     * 모든 태그 조회
     * GET /api/v1/tags
     */
    @GetMapping("/tags")
    @Operation(
        summary = "모든 태그 조회",
        description = "모든 활성 태그를 조회합니다. Redis 캐싱을 사용하여 빠르게 응답합니다."
    )
    public ApiResponse<List<Tag>> getAllTags() {
        List<Tag> tags = tagService.getAllTags();
        return ApiResponse.success(tags);
    }
    
    /**
     * 카테고리별 태그 조회
     * GET /api/v1/tags?category={category}
     */
    @GetMapping(value = "/tags", params = "category")
    @Operation(
        summary = "카테고리별 태그 조회",
        description = "카테고리(TYPE 또는 TOPIC)별로 태그를 조회합니다. Redis 캐싱을 사용하여 빠르게 응답합니다."
    )
    public ApiResponse<List<Tag>> getTagsByCategory(
            @Parameter(description = "태그 카테고리 (TYPE 또는 TOPIC)", required = true)
            @RequestParam TagCategory category) {
        List<Tag> tags = tagService.getTagsByCategory(category);
        return ApiResponse.success(tags);
    }
}

