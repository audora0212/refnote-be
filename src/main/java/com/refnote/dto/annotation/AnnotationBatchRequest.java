package com.refnote.dto.annotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AnnotationBatchRequest {

    @NotEmpty(message = "주석 목록은 비어있을 수 없습니다.")
    @Valid
    private List<AnnotationCreateRequest> annotations;
}
