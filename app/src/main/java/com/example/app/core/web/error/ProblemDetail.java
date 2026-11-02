package com.example.app.core.web.error;

import com.example.app.api.model.ErrorResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * The two formats of error responses, and how to choose between them.
 *
 * <ul>
 *   <li>Default: {@code {status, error, message}}</li>
 *   <li>RFC 9457 Problem Details: only when the client explicitly asks for {@code application/problem+json}
 *       (not for {@code Accept: *}{@code /*})</li>
 * </ul>
 *
 * @param type a URI for the problem type; always {@code about:blank}
 * @param title the reason phrase of the status
 * @param status the HTTP status
 * @param detail the message for the client
 * @param instance the request URI, or null
 */
public record ProblemDetail(String type, String title, int status, String detail, String instance) {

    /** The media type of RFC 9457 bodies. */
    public static final String MEDIA_TYPE = "application/problem+json";

    /**
     * Creates an RFC 9457 body with {@code type} {@code about:blank} and the reason phrase of {@code status} as {@code
     * title}.
     *
     * @param status the HTTP status
     * @param detail the message for the client
     * @param instance the request URI, or null
     * @return the body
     */
    public static ProblemDetail of(int status, String detail, URI instance) {
        Response.Status statusType = Response.Status.fromStatusCode(status);
        return new ProblemDetail(
                "about:blank",
                statusType != null ? statusType.getReasonPhrase() : "Error",
                status,
                detail,
                instance != null ? instance.toString() : null);
    }

    /**
     * Tells whether an {@code Accept} header names {@code application/problem+json} explicitly. A wildcard does not
     * count.
     *
     * @param accept the raw header value, or null
     * @return true if Problem Details were asked for
     */
    public static boolean requested(String accept) {
        if (accept == null) {
            return false;
        }
        for (String value : accept.split(",")) {
            if (value.split(";")[0].trim().equalsIgnoreCase(MEDIA_TYPE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether the request asks for Problem Details, like {@link #requested(String)}, from the parsed headers an
     * exception mapper sees.
     *
     * @param headers the request headers
     * @return true if Problem Details were asked for
     */
    public static boolean requested(HttpHeaders headers) {
        return headers.getAcceptableMediaTypes().stream()
                .anyMatch(type -> "application".equalsIgnoreCase(type.getType())
                        && "problem+json".equalsIgnoreCase(type.getSubtype()));
    }

    /**
     * Builds the error body in the format the client asked for.
     *
     * @param status the HTTP status
     * @param code the error code of the default format
     * @param message the message for the client
     * @param problemJson true for RFC 9457, false for the default format
     * @param instance the request URI, or null
     * @return a {@code ProblemDetail} or an {@code ErrorResponse}
     */
    public static Object body(int status, String code, String message, boolean problemJson, URI instance) {
        if (problemJson) {
            return of(status, message, instance);
        }
        ErrorResponse body = new ErrorResponse();
        body.setStatus(status);
        body.setError(code);
        body.setMessage(message);
        return body;
    }

    /**
     * Returns the content type matching {@link #body}.
     *
     * @param problemJson true for RFC 9457
     * @return {@code application/problem+json} or {@code application/json}
     */
    public static String contentType(boolean problemJson) {
        return problemJson ? MEDIA_TYPE : MediaType.APPLICATION_JSON;
    }
}
