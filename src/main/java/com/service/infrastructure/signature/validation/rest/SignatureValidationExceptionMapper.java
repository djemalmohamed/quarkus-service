package com.service.infrastructure.signature.validation.rest;

import com.service.infrastructure.signature.validation.error.SignatureValidationException;
import com.service.infrastructure.signature.validation.model.SignatureValidationCode;
import com.service.infrastructure.signature.validation.model.ValidationIssue;
import com.service.infrastructure.signature.validation.model.ValidationReport;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Map;

/**
 * Maps signature validation failures to the agreed HTTP status codes for inbound requests.
 */
@Provider
public class SignatureValidationExceptionMapper implements ExceptionMapper<SignatureValidationException> {

    /**
     * Converts a validation exception into the agreed JSON error payload and HTTP status code.
     *
     * @param exception signature validation exception raised by the inbound filter
     * @return HTTP response returned to the caller
     */
    @Override
    public Response toResponse(SignatureValidationException exception) {
        ValidationReport report = exception.report();
        return Response.status(resolveStatus(report == null ? List.of() : report.issues()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "valid", false,
                        "policy", report == null ? "unknown" : report.policy(),
                        "mode", report == null ? "FAIL_FAST" : report.mode(),
                        "issues", report == null ? List.of() : report.issues()
                ))
                .build();
    }

    /**
     * Resolves the HTTP status code corresponding to the collected validation issues.
     *
     * @param issues validation issues collected during the engine execution
     * @return HTTP status agreed for the failure category
     */
    private Response.Status resolveStatus(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return Response.Status.BAD_REQUEST;
        }

        boolean hasUnauthorized = false;
        for (ValidationIssue issue : issues) {
            switch (issue.code()) {
                case SIG_INTERNAL_ERROR, DISABLED -> {
                    return Response.Status.INTERNAL_SERVER_ERROR;
                }
                case SIG_MISSING_HEADER,
                        SIG_INVALID_FORMAT,
                        SIG_MISSING_KEY_ID,
                        SIG_MISSING_DIGEST,
                        SIG_INVALID_DIGEST,
                        SIG_UNSUPPORTED_DIGEST,
                        SIG_UNSUPPORTED_ALGORITHM,
                        SIG_MISSING_COMPONENT,
                        SIG_MISSING_COMPONENT_VALUE -> {
                    return Response.Status.BAD_REQUEST;
                }
                case SIG_UNKNOWN_KEY,
                        SIG_INVALID_SIGNATURE,
                        SIG_INVALID_CERTIFICATE,
                        SIG_UNTRUSTED_CERTIFICATE,
                        SIG_REVOKED_CERTIFICATE -> hasUnauthorized = true;
                case VALID -> {
                }
            }
        }
        return hasUnauthorized ? Response.Status.UNAUTHORIZED : Response.Status.BAD_REQUEST;
    }
}
