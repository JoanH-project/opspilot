package com.opspilot.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeQueryRequest(
        @NotBlank @Size(max = 2000) String query,
        @Min(1) @Max(10) Integer limit) {
    public int safeLimit() {
        return limit == null ? 5 : limit;
    }
}
