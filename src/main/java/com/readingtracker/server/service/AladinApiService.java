package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.BookSearchFilter;
import com.readingtracker.server.dto.requestDTO.BookSearchRequest;
import com.readingtracker.server.dto.responseDTO.BookDetailResponse;
import com.readingtracker.server.dto.responseDTO.BookSearchResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AladinApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(AladinApiService.class);
    
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
                    .queryParam("QueryType", request.getQueryType().getApiValue())
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
            return parseSearchResponse(responseBody, request.getQuery(), request.getQueryType());
            
        } catch (Exception e) {
            throw new RuntimeException("알라딘 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 알라딘 API 응답 파싱
     */
    @SuppressWarnings("unchecked")
    private BookSearchResponse parseSearchResponse(Map<String, Object> responseBody, String query, BookSearchFilter searchFilter) {
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
        
        // 검색 기준에 따른 결과 검증 (알라딘 API의 자동 대체 방지)
        if (searchFilter == BookSearchFilter.TITLE && !books.isEmpty()) {
            // TITLE 검색: 응답받은 책 목록 중에서 실제로 도서명에 검색어가 포함된 책만 필터링
            // 공백 제거 후 비교하여 공백 차이로 인한 누락 방지
            String queryNormalized = query.replaceAll("\\s+", "").toLowerCase();
            books = books.stream()
                    .filter(book -> {
                        String title = book.getTitle();
                        if (title == null || title.trim().isEmpty()) {
                            return false;
                        }
                        String titleNormalized = title.replaceAll("\\s+", "").toLowerCase();
                        return titleNormalized.contains(queryNormalized);
                    })
                    .collect(Collectors.toList());
            
            // 검증 후 실제 결과가 없으면 totalResults = 0으로 설정
            if (books.isEmpty()) {
                totalResults = 0;
            } else {
                totalResults = books.size();
            }
        } else if (searchFilter == BookSearchFilter.AUTHOR && !books.isEmpty()) {
            // AUTHOR 검색: 응답받은 책 목록 중에서 실제로 저자명에 검색어가 포함된 책만 필터링
            // 공백 제거 후 비교하여 공백 차이로 인한 누락 방지
            String queryNormalized = query.replaceAll("\\s+", "").toLowerCase();
            books = books.stream()
                    .filter(book -> {
                        String author = book.getAuthor();
                        if (author == null || author.trim().isEmpty()) {
                            return false;
                        }
                        String authorNormalized = author.replaceAll("\\s+", "").toLowerCase();
                        return authorNormalized.contains(queryNormalized);
                    })
                    .collect(Collectors.toList());
            
            // 검증 후 실제 결과가 없으면 totalResults = 0으로 설정
            if (books.isEmpty()) {
                totalResults = 0;
            } else {
                totalResults = books.size();
            }
        } else if (searchFilter == BookSearchFilter.PUBLISHER && !books.isEmpty()) {
            // PUBLISHER 검색: 응답받은 책 목록 중에서 실제로 출판사명에 검색어가 포함된 책만 필터링
            // 공백 제거 후 비교하여 공백 차이로 인한 누락 방지
            String queryNormalized = query.replaceAll("\\s+", "").toLowerCase();
            books = books.stream()
                    .filter(book -> {
                        String publisher = book.getPublisher();
                        if (publisher == null || publisher.trim().isEmpty()) {
                            return false;
                        }
                        String publisherNormalized = publisher.replaceAll("\\s+", "").toLowerCase();
                        return publisherNormalized.contains(queryNormalized);
                    })
                    .collect(Collectors.toList());
            
            // 검증 후 실제 결과가 없으면 totalResults = 0으로 설정
            if (books.isEmpty()) {
                totalResults = 0;
            } else {
                totalResults = books.size();
            }
        }
        
        return new BookSearchResponse(books, totalResults, startIndex, itemsPerPage, query, searchFilter);
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
            
            // 디버깅: subInfo 구조 로깅
            /*
            logger.debug("=== subInfo 구조 확인 ===");
            logger.debug("subInfo keys: {}", subInfo.keySet());
            logger.debug("subInfo 전체: {}", subInfo);
            */
            
            // 총 페이지 수 파싱 - 여러 가능한 필드명 시도
            Integer totalPages = parseTotalPages(subInfo);
            if (totalPages != null) {
                bookInfo.setTotalPages(totalPages);
                logger.debug("totalPages 파싱 성공: {}", totalPages);
            } else {
                logger.debug("totalPages를 파싱할 수 없습니다. 사용 가능한 필드: {}", subInfo.keySet());
            }
            
            // 장르 정보 파싱 (메인 태그)
            String mainGenre = parseMainGenre(subInfo);
            if (mainGenre != null) {
                bookInfo.setMainGenre(mainGenre);
                logger.debug("mainGenre 파싱 성공: {}", mainGenre);
            } else {
                logger.debug("mainGenre를 파싱할 수 없습니다. 사용 가능한 필드: {}", subInfo.keySet());
                bookInfo.setMainGenre(null);
            }
        } else {
            logger.debug("subInfo가 없습니다. item keys: {}", item.keySet());
            bookInfo.setMainGenre(null);
        }
        
        return bookInfo;
    }
    
    /**
     * 알라딘 API로 ISBN을 통해 도서 세부 정보 조회
     * ItemSearch API로 기본 정보를 가져온 후, ItemLookUp API로 상세 정보(페이지 수 등)를 가져옴
     */
    public BookDetailResponse getBookDetail(String isbn) {
        try {
            // ISBN 정제: 접두사 제거, 하이픈과 공백 제거 (알파벳 포함 ISBN 보존)
            String cleanIsbn = isbn
                    .replaceAll("(?i)^ISBN\\s*", "")  // "ISBN " 접두사 제거 (대소문자 무시)
                    .replaceAll("[\\s-]", "")          // 하이픈과 공백 제거
                    .trim();                           // 앞뒤 공백 제거
            
            if (cleanIsbn.isEmpty()) {
                throw new IllegalArgumentException("유효한 ISBN을 입력해주세요.");
            }
            
            // 1단계: ItemSearch API로 기본 정보 조회
            BookDetailResponse detail = getBookDetailFromItemSearch(cleanIsbn);
            
            // 2단계: ItemLookUp API로 상세 정보(페이지 수 등) 조회
            enrichBookDetailFromItemLookUp(detail, cleanIsbn);
            
            return detail;
            
        } catch (Exception e) {
            throw new RuntimeException("알라딘 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * ItemSearch API로 기본 도서 정보 조회
     */
    private BookDetailResponse getBookDetailFromItemSearch(String isbn) {
        // ItemSearch API를 ISBN으로 검색
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/ItemSearch.aspx")
                .queryParam("ttbkey", apiKey)
                .queryParam("Query", isbn)  // ISBN을 Query로 사용
                .queryParam("QueryType", "ISBN")  // QueryType을 ISBN으로 설정
                .queryParam("SearchTarget", "Book")
                .queryParam("Start", 1)
                .queryParam("MaxResults", 1)  // ISBN으로 검색하면 하나의 결과만 반환
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
        
        // 응답 파싱 (ItemSearch 응답을 BookDetailResponse로 변환)
        return parseDetailFromSearchResponse(responseBody, isbn);
    }
    
    /**
     * ItemLookUp API로 상세 정보(페이지 수 등) 조회하여 기존 BookDetailResponse에 추가
     */
    private void enrichBookDetailFromItemLookUp(BookDetailResponse detail, String isbn) {
        try {
            // ISBN 길이에 따라 ItemIdType 결정
            String itemIdType = isbn.length() == 13 ? "ISBN13" : "ISBN";
            
            // ItemLookUp API 호출
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/ItemLookUp.aspx")
                    .queryParam("ttbkey", apiKey)
                    .queryParam("ItemId", isbn)
                    .queryParam("ItemIdType", itemIdType)
                    .queryParam("Output", "JS")
                    .queryParam("Version", "20131101")
                    .build()
                    .toUriString();
            
            logger.debug("ItemLookUp API 호출 URL: {}", url);
            
            // API 호출
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            
            if (responseBody == null) {
                logger.debug("ItemLookUp API 응답이 비어있습니다.");
                return;
            }
            
            // ItemLookUp 응답에서 페이지 수와 장르 정보 파싱
            parseDetailFromLookUpResponse(responseBody, detail);
            
        } catch (Exception e) {
            // ItemLookUp 실패 시 기존 정보 유지 (ItemSearch에서 얻은 정보는 유지)
            logger.debug("ItemLookUp API 호출 실패 (기존 정보 유지): {}", e.getMessage());
        }
    }
    
    /**
     * ItemLookUp API 응답에서 상세 정보 파싱
     */
    @SuppressWarnings("unchecked")
    private void parseDetailFromLookUpResponse(Map<String, Object> responseBody, BookDetailResponse detail) {
        // item 배열 파싱
        if (responseBody.containsKey("item")) {
            Object itemObj = responseBody.get("item");
            Map<String, Object> item;
            
            if (itemObj instanceof List) {
                List<Map<String, Object>> itemList = (List<Map<String, Object>>) itemObj;
                if (itemList.isEmpty()) {
                    logger.debug("ItemLookUp 응답에 item이 없습니다.");
                    return;
                }
                item = itemList.get(0);
            } else {
                // 단일 아이템인 경우
                item = (Map<String, Object>) itemObj;
            }
            
            logger.debug("=== ItemLookUp item 구조 확인 ===");
            logger.debug("ItemLookUp item keys: {}", item.keySet());
            
            // subInfo에서 페이지 수 파싱
            if (item.containsKey("subInfo")) {
                Object subInfoObj = item.get("subInfo");
                if (subInfoObj instanceof Map) {
                    Map<String, Object> subInfo = (Map<String, Object>) subInfoObj;
                    logger.debug("ItemLookUp subInfo keys: {}", subInfo.keySet());
                    logger.debug("ItemLookUp subInfo 전체: {}", subInfo);
                    
                    // 총 페이지 수 파싱
                    Integer totalPages = parseTotalPages(subInfo);
                    if (totalPages != null) {
                        detail.setTotalPages(totalPages);
                        logger.debug("ItemLookUp에서 totalPages 파싱 성공: {}", totalPages);
                    } else {
                        logger.debug("ItemLookUp에서 totalPages를 파싱할 수 없습니다.");
                    }
                }
            }
        }
    }
    
    /**
     * ItemSearch API 응답을 BookDetailResponse로 변환
     */
    @SuppressWarnings("unchecked")
    private BookDetailResponse parseDetailFromSearchResponse(Map<String, Object> responseBody, String isbn) {
        BookDetailResponse detail = new BookDetailResponse();
        
        // item 배열 파싱 (ItemSearch 응답 구조 사용)
        if (responseBody.containsKey("item")) {
            Object itemObj = responseBody.get("item");
            Map<String, Object> item;
            
            if (itemObj instanceof List) {
                List<Map<String, Object>> itemList = (List<Map<String, Object>>) itemObj;
                if (itemList.isEmpty()) {
                    throw new RuntimeException("도서 정보를 찾을 수 없습니다. ISBN: " + isbn);
                }
                item = itemList.get(0);
            } else {
                // 단일 아이템인 경우
                item = (Map<String, Object>) itemObj;
            }
            
            // 디버깅: item 전체 구조 로깅
            /*
            logger.debug("=== BookDetail item 전체 구조 확인 ===");
            logger.debug("item keys: {}", item.keySet());
            logger.debug("item 전체: {}", item);
            */
            
            // ISBN 파싱
            detail.setIsbn((String) item.get("isbn"));
            detail.setIsbn13((String) item.get("isbn13"));
            
            // 기본 정보 파싱
            detail.setTitle((String) item.get("title"));
            detail.setAuthor((String) item.get("author"));
            detail.setPublisher((String) item.get("publisher"));
            detail.setDescription((String) item.get("description"));
            detail.setCoverUrl((String) item.get("cover"));
            
            // 출판일 파싱 (ItemSearch에서는 "pubdate" 소문자 사용)
            if (item.get("pubdate") != null) {
                String pubDateStr = (String) item.get("pubdate");
                try {
                    LocalDate pubDate = LocalDate.parse(pubDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    detail.setPubDate(pubDate);
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }
            
            // subInfo에서 추가 정보 파싱
            Map<String, Object> subInfo = null;
            if (item.containsKey("subInfo")) {
                Object subInfoObj = item.get("subInfo");
                if (subInfoObj instanceof Map) {
                    subInfo = (Map<String, Object>) subInfoObj;
                    logger.debug("=== BookDetail subInfo 구조 확인 ===");
                    logger.debug("subInfo keys: {}", subInfo.keySet());
                    logger.debug("subInfo 전체: {}", subInfo);
                } else {
                    logger.debug("subInfo가 Map이 아닙니다. 타입: {}", subInfoObj != null ? subInfoObj.getClass().getName() : "null");
                }
            } else {
                logger.debug("subInfo 필드가 없습니다. item keys: {}", item.keySet());
            }
            
            // 총 페이지 수 파싱 - subInfo와 item 레벨 모두 확인
            Integer totalPages = null;
            if (subInfo != null && !subInfo.isEmpty()) {
                totalPages = parseTotalPages(subInfo);
                logger.debug("subInfo에서 totalPages 파싱 시도: {}", totalPages);
            }
            
            // subInfo에서 찾지 못했다면 item 레벨에서 확인
            if (totalPages == null) {
                totalPages = parseTotalPages(item);
                logger.debug("item 레벨에서 totalPages 파싱 시도: {}", totalPages);
            }
            
            if (totalPages != null) {
                detail.setTotalPages(totalPages);
                logger.debug("totalPages 파싱 성공: {}", totalPages);
            } else {
                logger.debug("totalPages를 파싱할 수 없습니다.");
            }
            
            // 카테고리 정보 파싱 (메인 태그) - subInfo와 item 레벨 모두 확인
            String mainGenre = null;
            if (subInfo != null && !subInfo.isEmpty()) {
                mainGenre = parseMainGenre(subInfo);
                logger.debug("subInfo에서 mainGenre 파싱 시도: {}", mainGenre);
            }
            
            // subInfo에서 찾지 못했다면 item 레벨에서 확인
            if (mainGenre == null) {
                mainGenre = parseMainGenre(item);
                logger.debug("item 레벨에서 mainGenre 파싱 시도: {}", mainGenre);
            }
            
            if (mainGenre != null) {
                detail.setMainGenre(mainGenre);
                logger.debug("mainGenre 파싱 성공: {}", mainGenre);
            } else {
                logger.debug("mainGenre를 파싱할 수 없습니다.");
                detail.setMainGenre(null);
            }
        } else {
            throw new RuntimeException("도서 정보를 찾을 수 없습니다. ISBN: " + isbn);
        }
        
        return detail;
    }
    
    /**
     * 총 페이지 수 파싱 - 정확한 필드명 우선 + 제한된 폴백
     * 알라딘 API ItemLookUp의 subInfo.itemPage를 우선 사용
     */
    private Integer parseTotalPages(Map<String, Object> data) {
        // 1순위: 정확한 필드명 (ItemLookUp subInfo.itemPage)
        if (data.containsKey("itemPage")) {
            Object value = data.get("itemPage");
            logger.debug("itemPage 필드 발견: {} (타입: {})", value, value != null ? value.getClass().getName() : "null");
            Integer intValue = parseInteger(value);
            if (intValue != null) {
                return intValue;
            }
        }
        
        // 2순위: 실제 가능성 있는 필드명 (itemPageCount)
        if (data.containsKey("itemPageCount")) {
            Object value = data.get("itemPageCount");
            logger.debug("itemPageCount 필드 발견: {} (타입: {})", value, value != null ? value.getClass().getName() : "null");
            Integer intValue = parseInteger(value);
            if (intValue != null) {
                return intValue;
            }
        }
        
        // 모든 필드명을 시도했지만 찾지 못함
        logger.debug("totalPages를 찾을 수 없습니다. 사용 가능한 필드: {}", data.keySet());
        return null;
    }
    
    /**
     * 메인 장르 파싱 - 정확한 필드명 우선 + 제한된 폴백
     * 알라딘 API ItemSearch의 item.categoryName을 우선 사용
     */
    private String parseMainGenre(Map<String, Object> data) {
        // 1순위: 정확한 필드명 (ItemSearch item.categoryName)
        if (data.containsKey("categoryName")) {
            Object value = data.get("categoryName");
            if (value != null) {
                logger.debug("categoryName 필드에서 mainGenre 발견: {}", value);
                return value.toString();
            }
        }
        
        // 2순위: 실제 가능성 있는 필드명 (name)
        if (data.containsKey("name")) {
            Object value = data.get("name");
            if (value != null) {
                logger.debug("name 필드에서 mainGenre 발견: {}", value);
                return value.toString();
            }
        }
        
        // 3순위: categoryList에서 첫 번째 카테고리의 categoryName 추출
        if (data.containsKey("categoryList")) {
            Object categoryListObj = data.get("categoryList");
            if (categoryListObj instanceof List) {
                List<Map<String, Object>> categoryList = (List<Map<String, Object>>) categoryListObj;
                if (!categoryList.isEmpty()) {
                    Map<String, Object> firstCategory = categoryList.get(0);
                    // categoryName 우선
                    if (firstCategory.containsKey("categoryName")) {
                        Object value = firstCategory.get("categoryName");
                        if (value != null) {
                            logger.debug("categoryList[0].categoryName에서 mainGenre 발견: {}", value);
                            return value.toString();
                        }
                    }
                    // name 폴백
                    if (firstCategory.containsKey("name")) {
                        Object value = firstCategory.get("name");
                        if (value != null) {
                            logger.debug("categoryList[0].name에서 mainGenre 발견: {}", value);
                            return value.toString();
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 타입 변환: Object -> Integer
     * String, Integer, Long 등 다양한 타입을 Integer로 변환
     */
    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        }
        
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        
        if (value instanceof String) {
            String str = (String) value;
            // "300페이지" 같은 형태에서 숫자만 추출
            str = str.replaceAll("[^0-9]", "");
            if (!str.isEmpty()) {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    logger.debug("숫자 변환 실패: {}", value);
                    return null;
                }
            }
        }
        
        logger.debug("지원하지 않는 타입: {} ({})", value, value.getClass().getName());
        return null;
    }
}
