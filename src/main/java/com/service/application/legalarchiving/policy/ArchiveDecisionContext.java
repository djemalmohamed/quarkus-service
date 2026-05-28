package com.service.application.legalarchiving.policy;

/**
 * Application-level description of an HTTP interaction that may be subject to legal archiving.
 *
 * @param direction the interaction direction relative to the current service
 * @param method the HTTP method
 * @param path the request path
 * @param host the target host when the interaction is outbound
 */
public record ArchiveDecisionContext(
        Direction direction,
        String method,
        String path,
        String host) {

    /**
     * Creates an inbound decision context from request metadata already extracted by infrastructure.
     *
     * @param method the inbound HTTP method
     * @param path the inbound request path
     * @return the corresponding application decision context
     */
    public static ArchiveDecisionContext inbound(String method, String path) {
        return new ArchiveDecisionContext(Direction.INBOUND, method, path, "");
    }

    /**
     * Creates an outbound decision context from target metadata already extracted by infrastructure.
     *
     * @param method the outbound HTTP method
     * @param host the outbound target host
     * @param path the outbound target path
     * @return the corresponding application decision context
     */
    public static ArchiveDecisionContext outbound(String method, String host, String path) {
        return new ArchiveDecisionContext(Direction.OUTBOUND, method, path, host);
    }

    /**
     * Interaction direction relative to the current service.
     */
    public enum Direction {
        INBOUND,
        OUTBOUND
    }
}
