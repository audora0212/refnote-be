package com.refnote.controller;

import com.refnote.dto.common.ApiResponse;
import com.refnote.dto.review.*;
import com.refnote.entity.ReviewStatus;
import com.refnote.service.ReviewQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review-queue")
@RequiredArgsConstructor
public class ReviewQueueController {

    private final ReviewQueueService reviewQueueService;

    @GetMapping
    public ResponseEntity<ApiResponse<ReviewQueueResponse>> getReviewQueue(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ReviewQueueResponse response = reviewQueueService.getReviewQueue(userId, subjectId, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReviewQueueSummaryResponse>> getSummary(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ReviewQueueSummaryResponse response = reviewQueueService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Void>> markReviewed(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        reviewQueueService.markReviewed(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        reviewQueueService.dismiss(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DashboardResponse response = reviewQueueService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/loop-summary")
    public ResponseEntity<ApiResponse<LoopSummaryResponse>> getLoopSummary(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LoopSummaryResponse response = reviewQueueService.getLoopSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/quiz")
    public ResponseEntity<ApiResponse<QuizResponse>> getQuiz(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        QuizResponse response = reviewQueueService.generateQuiz(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}/confused")
    public ResponseEntity<ApiResponse<Void>> markConfused(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        reviewQueueService.markConfused(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/defer")
    public ResponseEntity<ApiResponse<Void>> defer(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        reviewQueueService.defer(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
