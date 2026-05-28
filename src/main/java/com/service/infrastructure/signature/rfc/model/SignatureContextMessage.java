package com.service.infrastructure.signature.rfc.model;

/**
 * RFC-facing signature context composed of the current message plus its optional associated request.
 *
 * <p>This wrapper keeps request-correlation concerns such as {@code ;req} inside the signature
 * layer instead of leaking them into transport-specific request or response models.</p>
 */
public record SignatureContextMessage(
        SignatureMessage currentMessage,
        SignatureMessage associatedRequest
) {

    /**
     * Creates a signature context for a standalone message with no associated request.
     *
     * @param currentMessage message currently being signed or verified
     * @return signature context without request correlation
     */
    public static SignatureContextMessage standalone(SignatureMessage currentMessage) {
        return new SignatureContextMessage(currentMessage, null);
    }

    /**
     * Creates a signature context for a message that depends on a previously exchanged request.
     *
     * @param currentMessage message currently being signed or verified
     * @param associatedRequest request associated with the current message
     * @return signature context carrying both messages
     */
    public static SignatureContextMessage withAssociatedRequest(
            SignatureMessage currentMessage,
            SignatureMessage associatedRequest
    ) {
        return new SignatureContextMessage(currentMessage, associatedRequest);
    }

    /**
     * Reuses the current context while replacing only the current message instance.
     *
     * @param currentMessage updated current message
     * @return copied signature context preserving the associated request
     */
    public SignatureContextMessage withCurrentMessage(SignatureMessage currentMessage) {
        return new SignatureContextMessage(currentMessage, associatedRequest);
    }
}
