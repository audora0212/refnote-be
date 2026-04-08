package com.refnote.dto.session;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionUpdateRequest {

    @NotNull(message = "현재 페이지는 필수입니다.")
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    private Integer currentPage;

    @NotNull(message = "스크롤 위치는 필수입니다.")
    private Double scrollPosition;
}
