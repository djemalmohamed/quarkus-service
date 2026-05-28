package com.service.infrastructure.adapters.legalarchiving;

/**
 * Request-context keys owned by the legal-archiving feature.
 */
public final class LegalArchivingContextKeys {

    public static final String ARCHIVE_ENABLED = "legal-archive.enabled";
    public static final String OPERATION_ID = "legal-archive.operation-id";
    public static final String OPERATION = "legal-archive.operation";

    /**
     * Prevents instantiation of this constants holder.
     */
    private LegalArchivingContextKeys() {
    }
}
