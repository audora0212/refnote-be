package com.refnote.service;

import com.refnote.dto.subject.SubjectCreateRequest;
import com.refnote.dto.subject.SubjectResponse;
import com.refnote.dto.subject.SubjectUpdateRequest;
import com.refnote.entity.Subject;
import com.refnote.entity.User;
import com.refnote.exception.ApiException;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.SubjectRepository;
import com.refnote.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private SubjectService subjectService;

    private User testUser;
    private Subject testSubject;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@test.com").nickname("tester").build();
        testSubject = Subject.builder()
                .id(1L)
                .user(testUser)
                .name("운영체제")
                .color("#e8a830")
                .documents(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("과목 생성 성공")
    void createSubject_success() {
        SubjectCreateRequest request = new SubjectCreateRequest();
        ReflectionTestUtils.setField(request, "name", "운영체제");
        ReflectionTestUtils.setField(request, "color", "#e8a830");

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(subjectRepository.existsByUserIdAndName(1L, "운영체제")).willReturn(false);
        given(subjectRepository.save(any(Subject.class))).willAnswer(inv -> {
            Subject s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            ReflectionTestUtils.setField(s, "documents", new java.util.ArrayList<>());
            return s;
        });

        SubjectResponse response = subjectService.createSubject(request, 1L);

        assertThat(response.getName()).isEqualTo("운영체제");
        assertThat(response.getColor()).isEqualTo("#e8a830");
        assertThat(response.getDocumentCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 유저의 중복 과목명 생성 시 409 예외")
    void createSubject_duplicateName_throwsConflict() {
        SubjectCreateRequest request = new SubjectCreateRequest();
        ReflectionTestUtils.setField(request, "name", "운영체제");

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(subjectRepository.existsByUserIdAndName(1L, "운영체제")).willReturn(true);

        assertThatThrownBy(() -> subjectService.createSubject(request, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("타인의 과목 수정 시 403 예외")
    void updateSubject_otherUser_throwsForbidden() {
        User otherUser = User.builder().id(2L).build();
        Subject otherSubject = Subject.builder().id(2L).user(otherUser).name("미적분").build();

        SubjectUpdateRequest request = new SubjectUpdateRequest();
        ReflectionTestUtils.setField(request, "name", "해킹시도");

        given(subjectRepository.findById(2L)).willReturn(Optional.of(otherSubject));

        assertThatThrownBy(() -> subjectService.updateSubject(2L, request, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("타인의 과목 삭제 시 403 예외")
    void deleteSubject_otherUser_throwsForbidden() {
        User otherUser = User.builder().id(2L).build();
        Subject otherSubject = Subject.builder().id(2L).user(otherUser).name("미적분").build();

        given(subjectRepository.findById(2L)).willReturn(Optional.of(otherSubject));

        assertThatThrownBy(() -> subjectService.deleteSubject(2L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("존재하지 않는 과목 조회 시 404 예외")
    void updateSubject_notFound_throwsNotFound() {
        SubjectUpdateRequest request = new SubjectUpdateRequest();
        ReflectionTestUtils.setField(request, "name", "없는과목");

        given(subjectRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.updateSubject(999L, request, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
