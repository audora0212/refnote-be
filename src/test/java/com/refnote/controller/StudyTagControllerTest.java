package com.refnote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refnote.config.JwtProvider;
import com.refnote.dto.studytag.TagCreateRequest;
import com.refnote.entity.*;
import com.refnote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudyTagControllerTest {

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
    private JwtProvider jwtProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private User testUser;
    private Document testDocument;
    private PdfBlock testBlock;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("tag-test@test.com")
                .password(passwordEncoder.encode("password"))
                .nickname("tagger")
                .build());
        accessToken = jwtProvider.generateAccessToken(testUser.getId(), testUser.getEmail());

        testDocument = documentRepository.save(Document.builder()
                .user(testUser)
                .title("테스트문서")
                .status(Document.DocumentStatus.READY)
                .build());

        testBlock = pdfBlockRepository.save(PdfBlock.builder()
                .document(testDocument)
                .pageNumber(1)
                .blockOrder(1)
                .content("프로세스 상태 전이 다이어그램은 프로세스의 생명주기를 보여준다.")
                .build());
    }

    @Test
    @DisplayName("태그 추가 후 복습 큐 자동 생성 확인 (end-to-end)")
    void createTag_thenReviewQueueCreated() throws Exception {
        TagCreateRequest request = new TagCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", testBlock.getId());
        ReflectionTestUtils.setField(request, "tagType", TagType.CONFUSED);

        mockMvc.perform(post("/api/documents/" + testDocument.getId() + "/tags")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tagType").value("CONFUSED"))
                .andExpect(jsonPath("$.data.blockContent").isNotEmpty());

        long queueCount = reviewQueueItemRepository.countByUserIdAndStatus(testUser.getId(), ReviewStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(queueCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("태그 조회 시 summary 필드 검증")
    void getTags_includesSummary() throws Exception {
        TagCreateRequest req1 = new TagCreateRequest();
        ReflectionTestUtils.setField(req1, "blockId", testBlock.getId());
        ReflectionTestUtils.setField(req1, "tagType", TagType.EXAM);

        mockMvc.perform(post("/api/documents/" + testDocument.getId() + "/tags")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/documents/" + testDocument.getId() + "/tags")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags", hasSize(1)))
                .andExpect(jsonPath("$.data.summary.EXAM").value(1))
                .andExpect(jsonPath("$.data.summary.IMPORTANT").value(0))
                .andExpect(jsonPath("$.data.summary.CONFUSED").value(0));
    }
}
