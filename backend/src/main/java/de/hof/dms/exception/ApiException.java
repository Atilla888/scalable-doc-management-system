package de.hof.dms.exception;

import org.springframework.http.HttpStatus;

/**
 * Runtime exception carrying an HTTP status, thrown by services to signal a
 * client- or server-facing error. It is translated into an RFC 7807
 * {@code ProblemDetail} response by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Creates an exception with the HTTP status to return and a human-readable
     * detail message.
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** Returns the HTTP status this error should be reported with. */
    public HttpStatus getStatus() {
        return status;
    }
}
