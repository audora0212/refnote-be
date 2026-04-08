package com.refnote.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class QuizResponse {

    private String type;       // "OX" or "FILL_BLANK"
    private String question;
    private String answer;
    private String explanation;
    private Long sourceBlockId;
}
