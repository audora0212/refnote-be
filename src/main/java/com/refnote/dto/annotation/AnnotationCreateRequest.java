package com.refnote.dto.annotation;

import com.refnote.entity.Annotation.AnnotationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnnotationCreateRequest {

    @NotNull(message = "페이지 번호는 필수입니다.")
    private Integer pageNumber;

    @NotNull(message = "주석 타입은 필수입니다.")
    private AnnotationType type;

    @NotBlank(message = "주석 데이터는 필수입니다.")
    private String data;

    private String color;

    private Integer thickness;
}
