package com.refnote.dto.beta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class BetaSignupResponse {
    private String message;
    private Long totalCount;
}
