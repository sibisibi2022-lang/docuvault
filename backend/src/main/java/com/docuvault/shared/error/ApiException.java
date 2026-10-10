package com.docuvault.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for failures the API is willing to describe to a caller.
 *
 * <p>Anything that is not an {@code ApiException} is treated as a bug and
 * reported as a bare 500 with no detail, so internal messages never leak.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
