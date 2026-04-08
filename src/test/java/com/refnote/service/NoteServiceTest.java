package com.refnote.service;

import com.refnote.dto.note.NoteCreateRequest;
import com.refnote.dto.note.NoteResponse;
import com.refnote.entity.*;
import com.refnote.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PdfBlockRepository pdfBlockRepository;
    @Mock
    private ReviewQueueService reviewQueueService;

    @InjectMocks
    private NoteService noteService;

    private User testUser;
    private Document testDocument;
    private PdfBlock testBlock;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@test.com").nickname("tester").build();
        testDocument = Document.builder().id(1L).user(testUser).title("테스트문서").build();
        testBlock = PdfBlock.builder().id(1L).document(testDocument).pageNumber(1).blockOrder(1).content("블록내용").build();
    }

    @Test
    @DisplayName("노트 생성 시 blockId 있으면 ReviewQueue 연동 호출")
    void createNote_withBlock_callsReviewQueue() {
        NoteCreateRequest request = new NoteCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", 1L);
        ReflectionTestUtils.setField(request, "content", "시험에 나올 내용");

        Note savedNote = Note.builder()
                .id(1L).user(testUser).document(testDocument).block(testBlock).content("시험에 나올 내용").build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(pdfBlockRepository.findById(1L)).willReturn(Optional.of(testBlock));
        given(noteRepository.save(any(Note.class))).willReturn(savedNote);

        NoteResponse response = noteService.createNote(1L, request, 1L);

        assertThat(response.getContent()).isEqualTo("시험에 나올 내용");
        verify(reviewQueueService).onNoteCreated(savedNote);
    }

    @Test
    @DisplayName("노트 생성 시 blockId null이면 ReviewQueue 호출하지 않음")
    void createNote_withoutBlock_doesNotCallReviewQueue() {
        NoteCreateRequest request = new NoteCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", null);
        ReflectionTestUtils.setField(request, "content", "문서 전체 메모");

        Note savedNote = Note.builder()
                .id(1L).user(testUser).document(testDocument).block(null).content("문서 전체 메모").build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(noteRepository.save(any(Note.class))).willReturn(savedNote);

        NoteResponse response = noteService.createNote(1L, request, 1L);

        assertThat(response.getContent()).isEqualTo("문서 전체 메모");
        assertThat(response.getBlockId()).isNull();
        verify(reviewQueueService, never()).onNoteCreated(any());
    }

    @Test
    @DisplayName("노트 생성 시 blockId가 문서에 속하지 않으면 예외")
    void createNote_blockNotInDocument_throwsBadRequest() {
        Document otherDoc = Document.builder().id(2L).user(testUser).title("다른문서").build();
        PdfBlock otherBlock = PdfBlock.builder().id(2L).document(otherDoc).pageNumber(1).blockOrder(1).build();

        NoteCreateRequest request = new NoteCreateRequest();
        ReflectionTestUtils.setField(request, "blockId", 2L);
        ReflectionTestUtils.setField(request, "content", "잘못된 블록");

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(documentRepository.findById(1L)).willReturn(Optional.of(testDocument));
        given(pdfBlockRepository.findById(2L)).willReturn(Optional.of(otherBlock));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> noteService.createNote(1L, request, 1L))
                .isInstanceOf(com.refnote.exception.ApiException.class);
    }
}
