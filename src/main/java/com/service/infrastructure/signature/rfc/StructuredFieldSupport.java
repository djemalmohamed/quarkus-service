package com.service.infrastructure.signature.rfc;

/**
 * Tiny helpers for the narrow subset of Structured Fields syntax enforced by this project.
 */
public final class StructuredFieldSupport {

    private StructuredFieldSupport() {
    }

    /**
     * Detects whether a header value contains multiple top-level dictionary members.
     *
     * <p>This is sufficient for the project profile because the validation runtime only accepts
     * one dictionary member in {@code Content-Digest}, {@code Signature-Input}, and
     * {@code Signature}. Commas inside quoted strings or byte sequences are ignored.</p>
     *
     * @param value raw header value
     * @return {@code true} when a top-level comma is present
     */
    public static boolean hasTopLevelComma(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        boolean inQuotedString = false;
        boolean escaping = false;
        boolean inByteSequence = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }

            if (inQuotedString) {
                if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inQuotedString = false;
                }
                continue;
            }

            if (current == '"') {
                inQuotedString = true;
                continue;
            }

            if (current == ':') {
                inByteSequence = !inByteSequence;
                continue;
            }

            if (!inByteSequence && current == ',') {
                return true;
            }
        }
        return false;
    }
}
