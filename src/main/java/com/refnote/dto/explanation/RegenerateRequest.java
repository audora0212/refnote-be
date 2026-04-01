package com.refnote.dto.explanation;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegenerateRequest {

    @NotBlank(message = "모드는 필수입니다.")
    private String mode; // SIMPLE, DETAILED, EXAMPLE, DIAGRAM
}
