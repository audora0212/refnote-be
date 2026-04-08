package com.refnote.service;

import com.refnote.dto.studytag.TagCreateRequest;
import com.refnote.dto.studytag.TagResponse;
import com.refnote.dto.studytag.TagUpdateRequest;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudyTagServiceTest {

    @Mock
    private StudyTagRepository studyTagRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PdfBlockRepository pdfBlockRepository;
    @Mock
    private ReviewQueueService reviewQueueService;

    @InjectMocks
    private StudyTagService studyTagService;

    private User testUser;
    private Document testDocument;
    private PdfBlock testBlock;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@test.com").nickname("tester").build();
        testDocument = Document.builder().id(1L).user(testUser).title("테스트문서").build();
        testBlock = PdfBlock.builder().id(1L).document(testDocument).pageNumber(1).blockOrder(1).content("테스트 블록 내용").build();
    }

    @Test
    @DisplayName("태그 생성 성공")
    void createTag_success() {
        TagCreateRequest request = new TagCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", 1L);
        ReflectionTestUtils.setField(request, "tagType", TagType.EXAM);

        StudyTag savedTag = StudyTag.builder()
                .id(1L).user(testUser).document(testDocument).block(testBlock).tagType(TagType.EXAM).build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(pdfBlockRepository.findById(1L)).willReturn(Optional.of(testBlock));
        given(studyTagRepository.existsByUserIdAndBlockIdAndTagType(1L, 1L, TagType.EXAM)).willReturn(false);
        given(studyTagRepository.save(any(StudyTag.class))).willReturn(savedTag);

        TagResponse response = studyTagService.createTag(1L, request, 1L);

        assertThat(response.getTagType()).isEqualTo("EXAM");
        verify(reviewQueueService).onTagCreated(savedTag);
    }

    @Test
    @DisplayName("중복 태그 방지 - 같은 블록+같은 타입이면 409 예외")
    void createTag_duplicate_throwsConflict() {
        TagCreateRequest request = new TagCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", 1L);
        ReflectionTestUtils.setField(request, "tagType", TagType.EXAM);

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(pdfBlockRepository.findById(1L)).willReturn(Optional.of(testBlock));
        given(studyTagRepository.existsByUserIdAndBlockIdAndTagType(1L, 1L, TagType.EXAM)).willReturn(true);

        assertThatThrownBy(() -> studyTagService.createTag(1L, request, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("태그 유형 변경 성공")
    void updateTag_success() {
        TagUpdateRequest request = new TagUpdateRequest();
        ReflectionTestUtils.setField(request, "tagType", TagType.IMPORTANT);

        StudyTag existingTag = StudyTag.builder()
                .id(1L).user(testUser).document(testDocument).block(testBlock).tagType(TagType.EXAM).build();

        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(studyTagRepository.findById(1L)).willReturn(Optional.of(existingTag));
        given(studyTagRepository.existsByUserIdAndBlockIdAndTagType(1L, 1L, TagType.IMPORTANT)).willReturn(false);
        given(studyTagRepository.save(any(StudyTag.class))).willReturn(existingTag);

        TagResponse response = studyTagService.updateTag(1L, 1L, request, 1L);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("타인의 문서에 태그 추가 시 403 예외")
    void createTag_otherUserDocument_throwsForbidden() {
        User otherUser = User.builder().id(2L).build();
        Document otherDocument = Document.builder().id(2L).user(otherUser).title("타인문서").build();

        TagCreateRequest request = new TagCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", 1L);
        ReflectionTestUtils.setField(request, "tagType", TagType.EXAM);

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(2L)).willReturn(Optional.of(otherDocument));

        assertThatThrownBy(() -> studyTagService.createTag(2L, request, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }
}
