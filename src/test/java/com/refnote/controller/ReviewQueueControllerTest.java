package com.refnote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refnote.config.JwtProvider;
import com.refnote.entity.*;
import com.refnote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private PdfBlockRepository pdfBlockRepository;
    @Autowired
    private ReviewQueueItemRepository reviewQueueItemRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private User testUser;
    private Document testDocument;
    private PdfBlock testBlock;
    private Subject testSubject;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("review-test@test.com")
                .password(passwordEncoder.encode("password"))
                .nickname("reviewer")
                .build());
        accessToken = jwtProvider.generateAccessToken(testUser.getId(), testUser.getEmail());

        testSubject = subjectRepository.save(Subject.builder()
                .user(testUser)
                .name("운영체제")
                .color("#e8a830")
                .build());

        testDocument = documentRepository.save(Document.builder()
                .user(testUser)
                .title("테스트문서")
                .subject(testSubject)
                .status(Document.DocumentStatus.READY)
                .build());

        testBlock = pdfBlockRepository.save(PdfBlock.builder()
                .document(testDocument)
                .pageNumber(1)
                .blockOrder(1)
                .content("테스트 블록 내용")
                .build());

        reviewQueueItemRepository.save(ReviewQueueItem.builder()
                .user(testUser)
                .document(testDocument)
                .block(testBlock)
                .sourceType(SourceType.TAG)
                .sourceId(1L)
                .title("[CONFUSED] 테스트 블록 내용")
                .priority(5)
                .build());

        reviewQueueItemRepository.save(ReviewQueueItem.builder()
                .user(testUser)
                .document(testDocument)
                .block(testBlock)
                .sourceType(SourceType.NOTE)
                .sourceId(2L)
                .title("노트 메모 내용")
                .priority(1)
                .build());
    }

    @Test
    @DisplayName("복습 큐 조회 - 페이지네이션")
    void getReviewQueue_pagination() throws Exception {
        mockMvc.perform(get("/api/review-queue")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(2));
    }

    @Test
    @DisplayName("복습 큐 요약 조회")
    void getSummary() throws Exception {
        mockMvc.perform(get("/api/review-queue/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPending").value(2))
                .andExpect(jsonPath("$.data.bySource.TAG").value(1))
                .andExpect(jsonPath("$.data.bySource.NOTE").value(1));
    }

    @Test
    @DisplayName("복습 완료 처리")
    void markReviewed() throws Exception {
        ReviewQueueItem item = reviewQueueItemRepository.findAll().get(0);

        mockMvc.perform(put("/api/review-queue/" + item.getId() + "/review")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        ReviewQueueItem updated = reviewQueueItemRepository.findById(item.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo(ReviewStatus.REVIEWED);
        org.assertj.core.api.Assertions.assertThat(updated.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("복습 건너뛰기 처리")
    void dismissItem() throws Exception {
        ReviewQueueItem item = reviewQueueItemRepository.findAll().get(0);

        mockMvc.perform(put("/api/review-queue/" + item.getId() + "/dismiss")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        ReviewQueueItem updated = reviewQueueItemRepository.findById(item.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo(ReviewStatus.DISMISSED);
    }
}
