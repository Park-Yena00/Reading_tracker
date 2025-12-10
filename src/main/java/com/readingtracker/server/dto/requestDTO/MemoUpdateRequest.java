package com.readingtracker.server.dto.requestDTO;

import jakarta.validation.constraints.*;
import java.util.List;

public class MemoUpdateRequest {
    
    @Size(max = 5000, message = "메모 내용은 5000자를 초과할 수 없습니다.")
    private String content;
    
    private List<String> tags;
    
    private String tagCategory;  // 태그 대분류 (TYPE, TOPIC) - 태그 미선택 시 etc 태그 선택에 사용
    
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    private Integer pageNumber;  // 페이지 번호 (수정 가능)
    
    // Getters and Setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public String getTagCategory() { return tagCategory; }
    public void setTagCategory(String tagCategory) { this.tagCategory = tagCategory; }
    
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
}

