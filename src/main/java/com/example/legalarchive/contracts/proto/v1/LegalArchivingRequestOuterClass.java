package eu.ecb.desp.protobuf.legal_archiving;

import eu.ecb.desp.protobuf.legal_archiving.LegalArchivingAdditionalDataOuterClass.LegalArchivingAdditionalData;
import eu.ecb.desp.protobuf.legal_archiving.LegalArchivingDataOuterClass.LegalArchivingData;
import eu.ecb.desp.protobuf.legal_archiving.SignatureDataOuterClass.SignatureData;
import eu.ecb.desp.protobuf.legal_archiving.SignatureParamsOuterClass.SignatureParams;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simulated generated protobuf container for the top-level legal-archiving request message.
 */
public final class LegalArchivingRequestOuterClass {

    private LegalArchivingRequestOuterClass() {
    }

    /**
     * Top-level message published to Kafka for legal archiving.
     */
    public static final class LegalArchivingRequest {

        @JsonProperty("lea_signature_data")
        private final SignatureData leaSignatureData;

        @JsonProperty("signature_params")
        private final SignatureParams signatureParams;

        @JsonProperty("legal_core_data")
        private final LegalArchivingData legalCoreData;

        @JsonProperty("lea_additional_data")
        private final LegalArchivingAdditionalData leaAdditionalData;

        private LegalArchivingRequest(Builder builder) {
            this.leaSignatureData = builder.leaSignatureData;
            this.signatureParams = builder.signatureParams;
            this.legalCoreData = builder.legalCoreData;
            this.leaAdditionalData = builder.leaAdditionalData;
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Serializes the simulated protobuf message as bytes.
         *
         * @return the serialized payload
         */
        public byte[] toByteArray() {
            return SimulatedProtoSupport.toByteArray(this);
        }

        /**
         * Renders the simulated protobuf payload as JSON for debugging and tests.
         *
         * @return the JSON representation of the message
         */
        public String toJson() {
            return SimulatedProtoSupport.toJson(this);
        }

        public SignatureData getLeaSignatureData() {
            return leaSignatureData;
        }

        public SignatureParams getSignatureParams() {
            return signatureParams;
        }

        public LegalArchivingData getLegalCoreData() {
            return legalCoreData;
        }

        public LegalArchivingAdditionalData getLeaAdditionalData() {
            return leaAdditionalData;
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            private SignatureData leaSignatureData;
            private SignatureParams signatureParams;
            private LegalArchivingData legalCoreData;
            private LegalArchivingAdditionalData leaAdditionalData;

            public Builder setLeaSignatureData(SignatureData leaSignatureData) {
                this.leaSignatureData = leaSignatureData;
                return this;
            }

            public Builder setSignatureParams(SignatureParams signatureParams) {
                this.signatureParams = signatureParams;
                return this;
            }

            public Builder setLegalCoreData(LegalArchivingData legalCoreData) {
                this.legalCoreData = legalCoreData;
                return this;
            }

            public Builder setLeaAdditionalData(LegalArchivingAdditionalData leaAdditionalData) {
                this.leaAdditionalData = leaAdditionalData;
                return this;
            }

            public LegalArchivingRequest build() {
                return new LegalArchivingRequest(this);
            }
        }
    }
}
