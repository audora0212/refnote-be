package com.refnote.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {

    @NotBlank(message = "메시지는 필수입니다.")
    private String message;

    private Long currentExplanationId;
}
