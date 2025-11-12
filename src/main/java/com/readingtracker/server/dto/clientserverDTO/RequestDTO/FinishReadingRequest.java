package com.readingtracker.server.dto.clientserverDTO.requestDTO;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 완독 요청 DTO
 */
public class FinishReadingRequest {

    /**
     * 독서 종료일 (필수)
     */
    @NotNull(message = "독서 종료일은 필수 입력 항목입니다.")
    private LocalDate readingFinishedDate;

    /**
     * 평점 (필수, 1~5)
     */
    @NotNull(message = "평점은 필수 입력 항목입니다.")
    @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5 이하여야 합니다.")
    private Integer rating;

    /**
     * 후기 (선택 사항)
     */
    private String review;

    public FinishReadingRequest() {
    }

    public LocalDate getReadingFinishedDate() {
        return readingFinishedDate;
    }

    public void setReadingFinishedDate(LocalDate readingFinishedDate) {
        this.readingFinishedDate = readingFinishedDate;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }
}






