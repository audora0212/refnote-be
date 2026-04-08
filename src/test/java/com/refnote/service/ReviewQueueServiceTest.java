package com.refnote.service;

import com.refnote.entity.*;
import com.refnote.repository.ChatMessageRepository;
import com.refnote.repository.ReviewQueueItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    @Mock
    private ReviewQueueItemRepository reviewQueueItemRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ReviewQueueService reviewQueueService;

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
    @DisplayName("CONFUSED 태그 시 우선순위 5")
    void onTagCreated_confused_priority5() {
        StudyTag tag = StudyTag.builder()
                .id(1L).user(testUser).document(testDocument).block(testBlock).tagType(TagType.CONFUSED).build();

        given(reviewQueueItemRepository.findByUserIdAndSourceTypeAndSourceId(1L, SourceType.TAG, 1L))
                .willReturn(Optional.empty());
        given(reviewQueueItemRepository.save(any(ReviewQueueItem.class))).willAnswer(inv -> inv.getArgument(0));

        reviewQueueService.onTagCreated(tag);

        ArgumentCaptor<ReviewQueueItem> captor = ArgumentCaptor.forClass(ReviewQueueItem.class);
        verify(reviewQueueItemRepository).save(captor.capture());

        assertThat(captor.getValue().getPriority()).isEqualTo(5);
        assertThat(captor.getValue().getSourceType()).isEqualTo(SourceType.TAG);
    }

    @Test
    @DisplayName("EXAM 태그 시 우선순위 4")
    void onTagCreated_exam_priority4() {
        assertThat(reviewQueueService.calculateTagPriority(TagType.EXAM)).isEqualTo(4);
    }

    @Test
    @DisplayName("IMPORTANT 태그 시 우선순위 3")
    void onTagCreated_important_priority3() {
        assertThat(reviewQueueService.calculateTagPriority(TagType.IMPORTANT)).isEqualTo(3);
    }

    @Test
    @DisplayName("MEMORIZE 태그 시 우선순위 2")
    void onTagCreated_memorize_priority2() {
        assertThat(reviewQueueService.calculateTagPriority(TagType.MEMORIZE)).isEqualTo(2);
    }

    @Test
    @DisplayName("REVIEW 태그 시 우선순위 1")
    void onTagCreated_review_priority1() {
        assertThat(reviewQueueService.calculateTagPriority(TagType.REVIEW)).isEqualTo(1);
    }

    @Test
    @DisplayName("질문 2회 미만이면 큐에 추가되지 않음")
    void onQuestionAsked_lessThan2_noQueueItem() {
        ChatMessage message = ChatMessage.builder()
                .id(1L).document(testDocument).user(testUser)
                .role(ChatMessage.MessageRole.USER).content("질문")
                .relatedBlockIds("1,2").build();

        given(chatMessageRepository.countByDocumentIdAndUserIdAndRole(1L, 1L, ChatMessage.MessageRole.USER))
                .willReturn(1L);

        reviewQueueService.onQuestionAsked(message);

        verify(reviewQueueItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("질문 2회 이상이면 큐에 추가")
    void onQuestionAsked_2OrMore_addsToQueue() {
        ChatMessage message = ChatMessage.builder()
                .id(1L).document(testDocument).user(testUser)
                .role(ChatMessage.MessageRole.USER).content("질문 내용")
                .relatedBlockIds("1,2").build();

        given(chatMessageRepository.countByDocumentIdAndUserIdAndRole(1L, 1L, ChatMessage.MessageRole.USER))
                .willReturn(3L);
        given(reviewQueueItemRepository.findByUserIdAndSourceTypeAndSourceId(1L, SourceType.QUESTION, 1L))
                .willReturn(Optional.empty());
        given(reviewQueueItemRepository.save(any(ReviewQueueItem.class))).willAnswer(inv -> inv.getArgument(0));

        reviewQueueService.onQuestionAsked(message);

        ArgumentCaptor<ReviewQueueItem> captor = ArgumentCaptor.forClass(ReviewQueueItem.class);
        verify(reviewQueueItemRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(3);
        assertThat(captor.getValue().getSourceType()).isEqualTo(SourceType.QUESTION);
    }

    @Test
    @DisplayName("동일 블록+출처 조합이면 기존 항목 업데이트")
    void onTagCreated_existingItem_updates() {
        StudyTag tag = StudyTag.builder()
                .id(2L).user(testUser).document(testDocument).block(testBlock).tagType(TagType.CONFUSED).build();

        ReviewQueueItem existingItem = ReviewQueueItem.builder()
                .id(10L).user(testUser).document(testDocument).block(testBlock)
                .sourceType(SourceType.TAG).sourceId(1L).priority(3).build();

        given(reviewQueueItemRepository.findByUserIdAndSourceTypeAndSourceId(1L, SourceType.TAG, 2L))
                .willReturn(Optional.of(existingItem));
        given(reviewQueueItemRepository.save(any(ReviewQueueItem.class))).willAnswer(inv -> inv.getArgument(0));

        reviewQueueService.onTagCreated(tag);

        ArgumentCaptor<ReviewQueueItem> captor = ArgumentCaptor.forClass(ReviewQueueItem.class);
        verify(reviewQueueItemRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getPriority()).isEqualTo(5);
    }

    @Test
    @DisplayName("relatedBlockIds가 null이면 큐에 추가하지 않음")
    void onQuestionAsked_noBlocks_noQueueItem() {
        ChatMessage message = ChatMessage.builder()
                .id(1L).document(testDocument).user(testUser)
                .role(ChatMessage.MessageRole.USER).content("질문")
                .relatedBlockIds(null).build();

        reviewQueueService.onQuestionAsked(message);

        verify(reviewQueueItemRepository, never()).save(any());
    }
}
