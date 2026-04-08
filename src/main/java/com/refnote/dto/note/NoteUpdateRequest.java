package com.refnote.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NoteUpdateRequest {

    @NotBlank(message = "노트 내용은 필수입니다.")
    @Size(max = 10000, message = "노트 내용은 10000자 이내여야 합니다.")
    private String content;
}
