package com.streamhub.platform.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final boolean success = false;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();
    private final List<FieldError> fieldErrors;

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
