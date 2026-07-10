package de.hof.dms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Application-wide REST exception handler that converts thrown exceptions into
 * RFC 7807 {@link ProblemDetail} responses, keeping error output consistent
 * across all controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Maps an {@link ApiException} to a problem response using its carried HTTP status. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    /** Returns 404 when a requested static resource or route cannot be found. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Returns 413 when an upload exceeds the configured maximum file size. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(413), "File exceeds the configured upload limit");
    }

    /** Catch-all that returns 500 for any otherwise unhandled exception. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
