package com.opspilot.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

class GlobalExceptionHandlerTest {
    @Test
    void databaseTransactionFailureReturnsSanitizedServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleDependencyUnavailable(
                new CannotCreateTransactionException("sensitive database connection detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).isEqualTo("A required service is temporarily unavailable");
        assertThat(response.getBody().message()).doesNotContain("sensitive database connection detail");
    }
}
