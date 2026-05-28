package com.playground.gateway.infrastructure.signature.validation.model;

/**
 * One validation problem detected while processing an inbound signature.
 *
 * <p>Issues are intentionally small and stable so they can be surfaced in API responses, logs
 * and observability pipelines without exposing internal implementation details.</p>
 */
public record ValidationIssue(
        SignatureValidationCode code,
        String message,
        String field
) {
}
