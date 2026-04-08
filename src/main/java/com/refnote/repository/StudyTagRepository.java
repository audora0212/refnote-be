package com.refnote.repository;

import com.refnote.entity.StudyTag;
import com.refnote.entity.TagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudyTagRepository extends JpaRepository<StudyTag, Long> {

    List<StudyTag> findAllByUserIdAndDocumentId(Long userId, Long documentId);

    List<StudyTag> findAllByUserIdAndDocumentIdAndTagType(Long userId, Long documentId, TagType tagType);

    boolean existsByUserIdAndBlockIdAndTagType(Long userId, Long blockId, TagType tagType);

    int countByUserIdAndDocumentId(Long userId, Long documentId);

    @Query("SELECT st.tagType, COUNT(st) FROM StudyTag st " +
            "WHERE st.user.id = :userId AND st.document.id = :documentId " +
            "GROUP BY st.tagType")
    List<Object[]> countByTagType(@Param("userId") Long userId, @Param("documentId") Long documentId);

    int countByDocumentId(Long documentId);

    void deleteAllByDocumentId(Long documentId);
}
