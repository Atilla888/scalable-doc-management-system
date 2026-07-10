package de.hof.dms.cmis;

import de.hof.dms.exception.ApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders CMIS faults as the Browser binding's JSON error body
 * ({@code {"exception": "...", "message": "..."}}) with the matching HTTP
 * status. Scoped to {@link CmisController} and given highest precedence so it
 * wins over the global REST handler for {@link ApiException} raised by reused
 * services during a CMIS request.
 */
@RestControllerAdvice(assignableTypes = CmisController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CmisExceptionHandler {

    /** Renders a {@link CmisException} as the CMIS Browser-binding JSON error body. */
    @ExceptionHandler(CmisException.class)
    public ResponseEntity<Map<String, Object>> handleCmis(CmisException ex) {
        return body(ex.getFault(), ex.getMessage());
    }

    /** Translates an {@link ApiException} from a reused service into the equivalent CMIS fault response. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        CmisException translated = CmisException.fromApi(ex);
        return body(translated.getFault(), translated.getMessage());
    }

    /** Builds the CMIS Browser-binding JSON error response for the given fault and message. */
    private ResponseEntity<Map<String, Object>> body(CmisFault fault, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exception", fault.exceptionName());
        payload.put("message", message == null ? "" : message);
        return ResponseEntity.status(fault.status()).body(payload);
    }
}
