package com.readingtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.BookSearchRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.BookSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private static final Logger logger = LoggerFactory.getLogger(AladinApiService.class);
    
    @Value("${aladin.api.key}")
    private String apiKey;
    
    @Value("${aladin.api.base-url}")
    private String baseUrl;
    
    private final RestTemplate restTemplate;
    
    public AladinApiService() {
        // RestTemplate에 User-Agent 헤더 추가를 위한 Interceptor 설정
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        
        this.restTemplate = new RestTemplate(factory);
        
        // User-Agent 헤더 추가 (알라딘 API 접근을 위해 필요할 수 있음)
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            request.getHeaders().add("Accept", "application/json, text/plain, */*");
            return execution.execute(request, body);
        });
    }
    
    /**
     * 알라딘 API로 책 검색
     */
    public BookSearchResponse searchBooks(BookSearchRequest request) {
        try {
            // QueryType 검증 및 변환
            String queryType = validateAndConvertQueryType(request.getQueryType());
            
            // API URL 구성 (한글 검색어를 위한 URL 인코딩)
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/ItemSearch.aspx")
                    .queryParam("ttbkey", apiKey)
                    .queryParam("Query", request.getQuery())
                    .queryParam("QueryType", queryType)
                    .queryParam("SearchTarget", request.getSearchTarget())
                    .queryParam("Start", request.getStart())
                    .queryParam("MaxResults", request.getMaxResults())
                    .queryParam("Output", "JS")
                    .queryParam("Version", "20131101");
            
            // URL 인코딩 적용
            String url = builder.encode().toUriString();
            
            logger.debug("알라딘 API 호출 URL: {}", url.replaceAll("ttbkey=[^&]+", "ttbkey=***"));
            
            // API 호출
            try {
                // 먼저 String으로 응답 받기 (HTML 에러 페이지 가능성 고려)
                ResponseEntity<String> stringResponse = restTemplate.getForEntity(url, String.class);
                
                logger.debug("알라딘 API 응답 상태: {}", stringResponse.getStatusCode());
                logger.debug("알라딘 API 응답 Content-Type: {}", stringResponse.getHeaders().getContentType());
                
                if (stringResponse.getStatusCode().isError()) {
                    throw new RuntimeException("알라딘 API HTTP 오류: " + stringResponse.getStatusCode());
                }
                
                String responseBodyString = stringResponse.getBody();
                if (responseBodyString == null || responseBodyString.trim().isEmpty()) {
                    logger.error("알라딘 API 응답이 비어있습니다.");
                    throw new RuntimeException("알라딘 API 응답이 비어있습니다.");
                }
                
                // HTML 응답인 경우 에러 처리
                String trimmedResponse = responseBodyString.trim();
                if (trimmedResponse.startsWith("<") || trimmedResponse.contains("<html") 
                    || trimmedResponse.contains("<!DOCTYPE") || trimmedResponse.contains("<body")) {
                    // 점검 안내 페이지인지 확인
                    if (responseBodyString.contains("서비스를 점검하고 있습니다") || responseBodyString.contains("점검 안내")) {
                        String errorMessage = extractErrorMessageFromHtml(responseBodyString);
                        logger.error("알라딘 API가 점검 중입니다. URL: {}", url.replaceAll("ttbkey=[^&]+", "ttbkey=***"));
                        throw new RuntimeException("알라딘 API 점검 중: " + errorMessage);
                    }
                    // HTML 응답에서 에러 메시지 추출 시도
                    String errorMessage = extractErrorMessageFromHtml(responseBodyString);
                    logger.error("알라딘 API가 HTML 응답을 반환했습니다. URL: {}", url.replaceAll("ttbkey=[^&]+", "ttbkey=***"));
                    logger.error("HTML 응답 내용 (처음 1000자): {}", responseBodyString.substring(0, Math.min(1000, responseBodyString.length())));
                    throw new RuntimeException("알라딘 API 오류: " + errorMessage);
                }
                
                // JSON 파싱
                Map<String, Object> responseBody;
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(responseBodyString, Map.class);
                    responseBody = parsed;
                    logger.debug("알라딘 API 응답 본문 키: {}", responseBody.keySet());
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    logger.error("알라딘 API 응답 JSON 파싱 실패. 응답 내용: {}", responseBodyString.substring(0, Math.min(500, responseBodyString.length())), e);
                    throw new RuntimeException("알라딘 API 응답을 JSON으로 파싱할 수 없습니다: " + e.getMessage(), e);
                }
                
                // 알라딘 API 에러 체크 (다양한 에러 필드명 확인)
                if (responseBody.containsKey("errorCode") || responseBody.containsKey("errorMessage") 
                    || responseBody.containsKey("error") || responseBody.containsKey("Error")) {
                    String errorCode = getStringValue(responseBody, "errorCode", "error", "Error");
                    String errorMessage = getStringValue(responseBody, "errorMessage", "errorMessage", "Error");
                    logger.error("알라딘 API 오류: {} - {}", errorCode, errorMessage);
                    throw new RuntimeException("알라딘 API 오류: " + errorCode + " - " + errorMessage);
                }
                
                // 응답 파싱
                BookSearchResponse result = parseSearchResponse(responseBody, request.getQuery());
                logger.debug("알라딘 API 파싱 완료: {} 개 결과", result.getBooks().size());
                return result;
                
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // HTTP 오류 응답 본문 추출 시도
                String errorResponseBody = e.getResponseBodyAsString();
                logger.error("알라딘 API HTTP 오류: {} - {}", e.getStatusCode(), e.getMessage());
                
                // 응답 본문이 HTML인 경우 (점검 안내 등)
                if (errorResponseBody != null && !errorResponseBody.trim().isEmpty()) {
                    String trimmedErrorBody = errorResponseBody.trim();
                    if (trimmedErrorBody.startsWith("<") || trimmedErrorBody.contains("<html") 
                        || trimmedErrorBody.contains("<!DOCTYPE") || trimmedErrorBody.contains("<body")) {
                        // 점검 안내 페이지인지 확인
                        if (errorResponseBody.contains("서비스를 점검하고 있습니다") || errorResponseBody.contains("점검 안내")) {
                            String errorMessage = extractErrorMessageFromHtml(errorResponseBody);
                            logger.error("알라딘 API가 점검 중입니다. URL: {}", url.replaceAll("ttbkey=[^&]+", "ttbkey=***"));
                            throw new RuntimeException("알라딘 API 점검 중: " + errorMessage);
                        }
                        // 일반 HTML 오류 응답
                        String errorMessage = extractErrorMessageFromHtml(errorResponseBody);
                        logger.error("알라딘 API HTML 오류 응답: {}", errorMessage);
                        throw new RuntimeException("알라딘 API 오류: " + errorMessage);
                    }
                }
                
                // 일반 HTTP 오류 (응답 본문이 없거나 JSON인 경우)
                String errorMessage = "알라딘 API HTTP 오류: " + e.getStatusCode();
                if (errorResponseBody != null && !errorResponseBody.trim().isEmpty()) {
                    logger.error("알라딘 API HTTP 오류 상세: {}", errorResponseBody);
                    errorMessage += " - " + errorResponseBody;
                } else {
                    errorMessage += " - " + e.getMessage();
                }
                throw new RuntimeException(errorMessage);
            } catch (org.springframework.web.client.RestClientException e) {
                logger.error("알라딘 API 호출 실패: {}", e.getMessage(), e);
                throw new RuntimeException("알라딘 API 호출 실패: " + e.getMessage(), e);
            }
            
        } catch (RuntimeException e) {
            // 이미 처리된 예외는 그대로 전달
            logger.error("알라딘 API 처리 중 오류: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("알라딘 API 호출 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new RuntimeException("알라딘 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * QueryType 검증 및 변환
     * 알라딘 API는 대소문자를 구분하므로 정확한 값으로 변환
     */
    private String validateAndConvertQueryType(String queryType) {
        if (queryType == null || queryType.trim().isEmpty()) {
            return "Title"; // 기본값
        }
        
        // 대소문자 무시하고 비교하여 정확한 값으로 변환
        String normalized = queryType.trim();
        switch (normalized.toLowerCase()) {
            case "title":
                return "Title";
            case "author":
                return "Author";
            case "publisher":
                return "Publisher";
            case "keyword":
                return "Keyword";
            default:
                // 알 수 없는 값이면 기본값 반환
                return "Title";
        }
    }
    
    /**
     * 알라딘 API 응답 파싱
     */
    @SuppressWarnings("unchecked")
    private BookSearchResponse parseSearchResponse(Map<String, Object> responseBody, String query) {
        List<BookSearchResponse.BookInfo> books = new ArrayList<>();
        
        // totalResults 파싱 (안전한 타입 변환)
        Integer totalResults = 0;
        if (responseBody.containsKey("totalResults")) {
            totalResults = parseIntegerSafely(responseBody.get("totalResults"), 0);
        }
        
        // startIndex 파싱 (안전한 타입 변환)
        Integer startIndex = 1;
        if (responseBody.containsKey("startIndex")) {
            startIndex = parseIntegerSafely(responseBody.get("startIndex"), 1);
        }
        
        // itemsPerPage 파싱 (안전한 타입 변환)
        Integer itemsPerPage = 10;
        if (responseBody.containsKey("itemsPerPage")) {
            itemsPerPage = parseIntegerSafely(responseBody.get("itemsPerPage"), 10);
        }
        
        // item 배열 파싱
        if (responseBody.containsKey("item")) {
            Object itemObj = responseBody.get("item");
            List<Map<String, Object>> itemList;
            
            if (itemObj == null) {
                // item이 null인 경우 빈 리스트로 처리
                itemList = new ArrayList<>();
            } else if (itemObj instanceof List) {
                itemList = (List<Map<String, Object>>) itemObj;
            } else if (itemObj instanceof Map) {
                // 단일 아이템인 경우 리스트로 변환
                itemList = new ArrayList<>();
                itemList.add((Map<String, Object>) itemObj);
            } else {
                // 예상치 못한 타입인 경우 빈 리스트로 처리
                itemList = new ArrayList<>();
            }
            
            for (Map<String, Object> item : itemList) {
                try {
                    BookSearchResponse.BookInfo bookInfo = parseBookInfo(item);
                    books.add(bookInfo);
                } catch (Exception e) {
                    // 개별 아이템 파싱 실패 시 로그만 남기고 계속 진행
                    System.err.println("책 정보 파싱 실패: " + e.getMessage());
                }
            }
        }
        
        return new BookSearchResponse(books, totalResults, startIndex, itemsPerPage, query);
    }
    
    /**
     * 개별 책 정보 파싱
     */
    @SuppressWarnings("unchecked")
    private BookSearchResponse.BookInfo parseBookInfo(Map<String, Object> item) {
        if (item == null) {
            throw new RuntimeException("책 정보가 null입니다.");
        }
        
        BookSearchResponse.BookInfo bookInfo = new BookSearchResponse.BookInfo();
        
        // ISBN 파싱 (null-safe)
        bookInfo.setIsbn(item.get("isbn") != null ? item.get("isbn").toString() : null);
        bookInfo.setIsbn13(item.get("isbn13") != null ? item.get("isbn13").toString() : null);
        
        // 기본 정보 파싱 (null-safe)
        bookInfo.setTitle(item.get("title") != null ? item.get("title").toString() : null);
        bookInfo.setAuthor(item.get("author") != null ? item.get("author").toString() : null);
        bookInfo.setPublisher(item.get("publisher") != null ? item.get("publisher").toString() : null);
        bookInfo.setDescription(item.get("description") != null ? item.get("description").toString() : null);
        bookInfo.setCoverUrl(item.get("cover") != null ? item.get("cover").toString() : null);
        
        // 가격 정보 파싱 (null-safe)
        if (item.get("pricesales") != null) {
            try {
                Object priceObj = item.get("pricesales");
                if (priceObj instanceof Number) {
                    bookInfo.setPriceSales(((Number) priceObj).intValue());
                } else if (priceObj instanceof String) {
                    bookInfo.setPriceSales(Integer.parseInt((String) priceObj));
                }
            } catch (Exception e) {
                // 가격 파싱 실패 시 무시
            }
        }
        if (item.get("pricestandard") != null) {
            try {
                Object priceObj = item.get("pricestandard");
                if (priceObj instanceof Number) {
                    bookInfo.setPriceStandard(((Number) priceObj).intValue());
                } else if (priceObj instanceof String) {
                    bookInfo.setPriceStandard(Integer.parseInt((String) priceObj));
                }
            } catch (Exception e) {
                // 가격 파싱 실패 시 무시
            }
        }
        
        // 출판일 파싱
        if (item.get("pubdate") != null) {
            String pubDateStr = item.get("pubdate").toString();
            try {
                LocalDate pubDate = LocalDate.parse(pubDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                bookInfo.setPubDate(pubDate);
            } catch (Exception e) {
                // 날짜 파싱 실패 시 무시
            }
        }
        
        // subInfo에서 추가 정보 파싱
        if (item.containsKey("subInfo") && item.get("subInfo") instanceof Map) {
            Map<String, Object> subInfo = (Map<String, Object>) item.get("subInfo");
            
            // 총 페이지 수 파싱
            if (subInfo != null && subInfo.get("itemPage") != null) {
                try {
                    Object pageObj = subInfo.get("itemPage");
                    if (pageObj instanceof Number) {
                        bookInfo.setTotalPages(((Number) pageObj).intValue());
                    } else if (pageObj instanceof String) {
                        bookInfo.setTotalPages(Integer.parseInt((String) pageObj));
                    }
                } catch (Exception e) {
                    // 페이지 수 파싱 실패 시 무시
                }
            }
        }
        
        // 장르 정보는 기본적으로 null로 설정 (알라딘 API에서 직접 제공하지 않음)
        bookInfo.setMainGenre(null);
        
        return bookInfo;
    }
    
    /**
     * 안전한 Integer 파싱 헬퍼 메서드
     */
    private Integer parseIntegerSafely(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else {
                return Integer.parseInt(value.toString());
            }
        } catch (Exception e) {
            // 파싱 실패 시 기본값 반환
            return defaultValue;
        }
    }
    
    /**
     * Map에서 여러 키 중 하나의 값을 안전하게 가져오는 헬퍼 메서드
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key).toString();
            }
        }
        return "UNKNOWN";
    }
    
    /**
     * HTML 응답에서 에러 메시지 추출
     */
    private String extractErrorMessageFromHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "알 수 없는 오류";
        }
        
        // HTML에서 에러 메시지 패턴 찾기
        String lowerHtml = html.toLowerCase();
        
        // title 태그에서 에러 메시지 찾기
        int titleStart = lowerHtml.indexOf("<title>");
        if (titleStart != -1) {
            int titleEnd = lowerHtml.indexOf("</title>", titleStart);
            if (titleEnd != -1) {
                String title = html.substring(titleStart + 7, titleEnd).trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }
        
        // body 태그 내의 텍스트 찾기
        int bodyStart = lowerHtml.indexOf("<body>");
        if (bodyStart != -1) {
            int bodyEnd = lowerHtml.indexOf("</body>", bodyStart);
            if (bodyEnd != -1) {
                String bodyContent = html.substring(bodyStart + 6, bodyEnd);
                // HTML 태그 제거
                String text = bodyContent.replaceAll("<[^>]+>", " ").trim();
                if (text.length() > 200) {
                    text = text.substring(0, 200) + "...";
                }
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        
        // 전체 응답의 일부 반환
        if (html.length() > 500) {
            return html.substring(0, 500) + "...";
        }
        return html;
    }
}
