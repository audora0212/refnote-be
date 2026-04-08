package com.refnote.dto.studytag;

import com.refnote.entity.TagType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TagUpdateRequest {

    @NotNull(message = "태그 유형은 필수입니다.")
    private TagType tagType;
}
