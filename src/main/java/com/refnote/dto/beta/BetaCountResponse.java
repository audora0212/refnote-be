package com.refnote.dto.beta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class BetaCountResponse {
    private Long count;
}
