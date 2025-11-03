package com.readingtracker.service;

import com.readingtracker.dto.ClientServerDTO.RequestDTO.BookSearchRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.BookSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AladinApiService {
    
    @Value("${aladin.api.key}")
    private String apiKey;
    
    @Value("${aladin.api.base-url}")
    private String baseUrl;
    
    private final RestTemplate restTemplate;
    
    public AladinApiService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 알라딘 API로 책 검색
     */
    public BookSearchResponse searchBooks(BookSearchRequest request) {
        try {
            // API URL 구성
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/ItemSearch.aspx")
                    .queryParam("ttbkey", apiKey)
                    .queryParam("Query", request.getQuery())
                    .queryParam("QueryType", request.getQueryType())
                    .queryParam("SearchTarget", request.getSearchTarget())
                    .queryParam("Start", request.getStart())
                    .queryParam("MaxResults", request.getMaxResults())
                    .queryParam("Output", "JS")
                    .queryParam("Version", "20131101")
                    .build()
                    .toUriString();
            
            // API 호출
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            
            if (responseBody == null) {
                throw new RuntimeException("알라딘 API 응답이 비어있습니다.");
            }
            
            // 응답 파싱
            return parseSearchResponse(responseBody, request.getQuery());
            
        } catch (Exception e) {
            throw new RuntimeException("알라딘 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 알라딘 API 응답 파싱
     */
    @SuppressWarnings("unchecked")
    private BookSearchResponse parseSearchResponse(Map<String, Object> responseBody, String query) {
        List<BookSearchResponse.BookInfo> books = new ArrayList<>();
        
        // totalResults 파싱
        Integer totalResults = 0;
        if (responseBody.containsKey("totalResults")) {
            totalResults = (Integer) responseBody.get("totalResults");
        }
        
        // startIndex 파싱
        Integer startIndex = 1;
        if (responseBody.containsKey("startIndex")) {
            startIndex = (Integer) responseBody.get("startIndex");
        }
        
        // itemsPerPage 파싱
        Integer itemsPerPage = 10;
        if (responseBody.containsKey("itemsPerPage")) {
            itemsPerPage = (Integer) responseBody.get("itemsPerPage");
        }
        
        // item 배열 파싱
        if (responseBody.containsKey("item")) {
            Object itemObj = responseBody.get("item");
            List<Map<String, Object>> itemList;
            
            if (itemObj instanceof List) {
                itemList = (List<Map<String, Object>>) itemObj;
            } else {
                // 단일 아이템인 경우 리스트로 변환
                itemList = new ArrayList<>();
                itemList.add((Map<String, Object>) itemObj);
            }
            
            for (Map<String, Object> item : itemList) {
                BookSearchResponse.BookInfo bookInfo = parseBookInfo(item);
                books.add(bookInfo);
            }
        }
        
        return new BookSearchResponse(books, totalResults, startIndex, itemsPerPage, query);
    }
    
    /**
     * 개별 책 정보 파싱
     */
    @SuppressWarnings("unchecked")
    private BookSearchResponse.BookInfo parseBookInfo(Map<String, Object> item) {
        BookSearchResponse.BookInfo bookInfo = new BookSearchResponse.BookInfo();
        
        // ISBN 파싱
        bookInfo.setIsbn((String) item.get("isbn"));
        bookInfo.setIsbn13((String) item.get("isbn13"));
        
        // 기본 정보 파싱
        bookInfo.setTitle((String) item.get("title"));
        bookInfo.setAuthor((String) item.get("author"));
        bookInfo.setPublisher((String) item.get("publisher"));
        bookInfo.setDescription((String) item.get("description"));
        bookInfo.setCoverUrl((String) item.get("cover"));
        
        // 가격 정보 파싱
        if (item.get("pricesales") != null) {
            bookInfo.setPriceSales((Integer) item.get("pricesales"));
        }
        if (item.get("pricestandard") != null) {
            bookInfo.setPriceStandard((Integer) item.get("pricestandard"));
        }
        
        // 출판일 파싱
        if (item.get("pubdate") != null) {
            String pubDateStr = (String) item.get("pubdate");
            try {
                LocalDate pubDate = LocalDate.parse(pubDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                bookInfo.setPubDate(pubDate);
            } catch (Exception e) {
                // 날짜 파싱 실패 시 무시
            }
        }
        
        // subInfo에서 추가 정보 파싱
        if (item.containsKey("subInfo")) {
            Map<String, Object> subInfo = (Map<String, Object>) item.get("subInfo");
            
            // 총 페이지 수 파싱
            if (subInfo.get("itemPage") != null) {
                bookInfo.setTotalPages((Integer) subInfo.get("itemPage"));
            }
        }
        
        // 장르 정보는 기본적으로 null로 설정 (알라딘 API에서 직접 제공하지 않음)
        bookInfo.setMainGenre(null);
        
        return bookInfo;
    }
}
