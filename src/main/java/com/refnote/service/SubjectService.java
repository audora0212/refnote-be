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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public SubjectResponse createSubject(SubjectCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        if (subjectRepository.existsByUserIdAndName(userId, request.getName())) {
            throw ApiException.conflict("이미 존재하는 과목명입니다: " + request.getName());
        }

        Subject subject = Subject.builder()
                .user(user)
                .name(request.getName())
                .color(request.getColor())
                .build();

        subject = subjectRepository.save(subject);
        log.info("과목 생성 완료 - subjectId: {}, userId: {}", subject.getId(), userId);
        return SubjectResponse.from(subject, 0);
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> getSubjects(Long userId) {
        List<Subject> subjects = subjectRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        return subjects.stream()
                .map(subject -> {
                    int docCount = documentRepository.countBySubjectId(subject.getId());
                    return SubjectResponse.from(subject, docCount);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public SubjectResponse updateSubject(Long subjectId, SubjectUpdateRequest request, Long userId) {
        Subject subject = findSubjectAndVerifyOwner(subjectId, userId);

        if (subjectRepository.existsByUserIdAndNameAndIdNot(userId, request.getName(), subjectId)) {
            throw ApiException.conflict("이미 존재하는 과목명입니다: " + request.getName());
        }

        subject.setName(request.getName());
        subject.setColor(request.getColor());
        subject = subjectRepository.save(subject);

        int docCount = documentRepository.countBySubjectId(subject.getId());
        log.info("과목 수정 완료 - subjectId: {}, userId: {}", subjectId, userId);
        return SubjectResponse.from(subject, docCount);
    }

    @Transactional
    public void deleteSubject(Long subjectId, Long userId) {
        Subject subject = findSubjectAndVerifyOwner(subjectId, userId);
        subjectRepository.delete(subject);
        log.info("과목 삭제 완료 - subjectId: {}, userId: {}", subjectId, userId);
    }

    private Subject findSubjectAndVerifyOwner(Long subjectId, Long userId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("과목을 찾을 수 없습니다."));

        if (!subject.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 과목에 대한 접근 권한이 없습니다.");
        }

        return subject;
    }
}
