package de.hof.dms.cmis;

import de.hof.dms.exception.ApiException;
import org.springframework.http.HttpStatus;

/** A CMIS fault carrying the fault type used to render the JSON error body. */
public class CmisException extends RuntimeException {

    private final transient CmisFault fault;

    /**
     * Creates a CMIS fault.
     *
     * @param fault   the CMIS fault type that determines the exception name and HTTP status
     * @param message human-readable detail rendered in the JSON error body
     */
    public CmisException(CmisFault fault, String message) {
        super(message);
        this.fault = fault;
    }

    /** Returns the fault type used to render the CMIS JSON error response. */
    public CmisFault getFault() {
        return fault;
    }

    /**
     * Translates an {@link ApiException} thrown by a reused REST service into the
     * equivalent CMIS fault, so the same RBAC/validation rules surface as proper
     * CMIS faults on the CMIS endpoints.
     */
    public static CmisException fromApi(ApiException ex) {
        HttpStatus status = ex.getStatus();
        CmisFault fault =
                switch (status) {
                    case FORBIDDEN -> CmisFault.PERMISSION_DENIED;
                    case NOT_FOUND -> CmisFault.OBJECT_NOT_FOUND;
                    case BAD_REQUEST -> CmisFault.INVALID_ARGUMENT;
                    case CONFLICT -> CmisFault.NAME_CONSTRAINT_VIOLATION;
                    case METHOD_NOT_ALLOWED -> CmisFault.NOT_SUPPORTED;
                    default -> CmisFault.RUNTIME;
                };
        return new CmisException(fault, ex.getMessage());
    }
}
