package com.service.infrastructure.adapters.legalarchiving.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        private final String signature;

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
         * @return the raw HTTP {@code Signature} header value
         */
        public String getSignature() {
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

            private String signature;
            private String signatureInput;

            /**
             * Sets the raw HTTP {@code Signature} header value.
             *
             * @param signature the signature header value
             * @return the current builder
             */
            public Builder setSignature(String signature) {
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
