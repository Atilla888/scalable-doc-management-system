package de.hof.dms.cmis;

import org.springframework.http.HttpStatus;

/**
 * The subset of CMIS exceptions this MVP raises, each paired with the HTTP
 * status the CMIS Browser binding maps it to. The {@code exceptionName} is the
 * value placed in the {@code "exception"} field of the JSON error body.
 */
public enum CmisFault {
    OBJECT_NOT_FOUND("objectNotFound", HttpStatus.NOT_FOUND),
    PERMISSION_DENIED("permissionDenied", HttpStatus.FORBIDDEN),
    INVALID_ARGUMENT("invalidArgument", HttpStatus.BAD_REQUEST),
    NOT_SUPPORTED("notSupported", HttpStatus.METHOD_NOT_ALLOWED),
    CONSTRAINT("constraint", HttpStatus.CONFLICT),
    NAME_CONSTRAINT_VIOLATION("nameConstraintViolation", HttpStatus.CONFLICT),
    RUNTIME("runtime", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String exceptionName;
    private final HttpStatus status;

    /**
     * Binds a fault constant to its CMIS exception name and mapped HTTP status.
     *
     * @param exceptionName the CMIS exception name placed in the error body
     * @param status        the HTTP status the Browser binding maps this fault to
     */
    CmisFault(String exceptionName, HttpStatus status) {
        this.exceptionName = exceptionName;
        this.status = status;
    }

    /** Returns the CMIS exception name placed in the {@code "exception"} field of the error body. */
    public String exceptionName() {
        return exceptionName;
    }

    /** Returns the HTTP status the CMIS Browser binding maps this fault to. */
    public HttpStatus status() {
        return status;
    }
}
