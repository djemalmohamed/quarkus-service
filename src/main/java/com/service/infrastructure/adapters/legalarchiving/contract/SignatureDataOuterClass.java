package com.service.infrastructure.adapters.legalarchiving.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;

/**
 * Simulated generated protobuf container for HTTP Message Signature header values.
 */
public final class SignatureDataOuterClass {

    private SignatureDataOuterClass() {
    }

    /**
     * Signature block storing the raw {@code Signature} and {@code Signature-Input} values.
     */
    public static final class SignatureData {

        @JsonProperty("signature")
        private final ByteString signature;

        @JsonProperty("signature_input")
        private final String signatureInput;

        private SignatureData(Builder builder) {
            this.signature = builder.signature;
            this.signatureInput = builder.signatureInput;
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * @return the raw HTTP {@code Signature} header as generated protobuf {@link ByteString}
         */
        public ByteString getSignature() {
            return signature;
        }

        /**
         * @return the raw HTTP {@code Signature-Input} header value
         */
        public String getSignatureInput() {
            return signatureInput;
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            private ByteString signature;
            private String signatureInput;

            /**
             * Sets the raw HTTP {@code Signature} header bytes using the generated protobuf API shape.
             *
             * @param signature the signature bytes
             * @return the current builder
             */
            public Builder setSignature(ByteString signature) {
                this.signature = signature;
                return this;
            }

            /**
             * Sets the raw HTTP {@code Signature-Input} header value.
             *
             * @param signatureInput the signature input field
             * @return the current builder
             */
            public Builder setSignatureInput(String signatureInput) {
                this.signatureInput = signatureInput;
                return this;
            }

            /**
             * @return the simulated generated protobuf message
             */
            public SignatureData build() {
                return new SignatureData(this);
            }
        }
    }
}
