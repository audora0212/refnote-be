package com.refnote.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private final boolean success;
    private final String error;
    private final String message;
    private final LocalDateTime timestamp;

    public static ErrorResponse of(String error, String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
