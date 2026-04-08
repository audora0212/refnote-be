package com.refnote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refnote.config.JwtProvider;
import com.refnote.dto.subject.SubjectCreateRequest;
import com.refnote.dto.subject.SubjectUpdateRequest;
import com.refnote.entity.User;
import com.refnote.repository.SubjectRepository;
import com.refnote.repository.UserRepository;
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
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@test.com")
                .password(passwordEncoder.encode("password"))
                .nickname("tester")
                .build();
        testUser = userRepository.save(testUser);
        accessToken = jwtProvider.generateAccessToken(testUser.getId(), testUser.getEmail());
    }

    @Test
    @DisplayName("과목 CRUD 전체 흐름 (생성 -> 조회 -> 수정 -> 삭제)")
    void subjectCrudFlow() throws Exception {
        SubjectCreateRequest createReq = new SubjectCreateRequest();
        ReflectionTestUtils.setField(createReq, "name", "운영체제");
        ReflectionTestUtils.setField(createReq, "color", "#e8a830");

        String createResult = mockMvc.perform(post("/api/subjects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("운영체제"))
                .andExpect(jsonPath("$.data.color").value("#e8a830"))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andReturn().getResponse().getContentAsString();

        Long subjectId = objectMapper.readTree(createResult).path("data").path("id").asLong();

        mockMvc.perform(get("/api/subjects")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subjects", hasSize(1)))
                .andExpect(jsonPath("$.data.subjects[0].name").value("운영체제"));

        SubjectUpdateRequest updateReq = new SubjectUpdateRequest();
        ReflectionTestUtils.setField(updateReq, "name", "미적분학");
        ReflectionTestUtils.setField(updateReq, "color", "#4ecdc4");

        mockMvc.perform(put("/api/subjects/" + subjectId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("미적분학"))
                .andExpect(jsonPath("$.data.color").value("#4ecdc4"));

        mockMvc.perform(delete("/api/subjects/" + subjectId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/subjects")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjects", hasSize(0)));
    }

    @Test
    @DisplayName("인증 없이 접근 시 403")
    void accessWithoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/subjects"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("중복 과목명 생성 시 409")
    void duplicateSubjectName_returns409() throws Exception {
        SubjectCreateRequest req = new SubjectCreateRequest();
        ReflectionTestUtils.setField(req, "name", "데이터베이스");
        ReflectionTestUtils.setField(req, "color", "#e8a830");

        mockMvc.perform(post("/api/subjects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/subjects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }
}
