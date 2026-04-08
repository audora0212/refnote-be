package com.refnote.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubjectCreateRequest {

    @NotBlank(message = "과목명은 필수입니다.")
    @Size(max = 100, message = "과목명은 100자 이내여야 합니다.")
    private String name;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "색상은 #RRGGBB 형식이어��� 합니다.")
    private String color;
}
