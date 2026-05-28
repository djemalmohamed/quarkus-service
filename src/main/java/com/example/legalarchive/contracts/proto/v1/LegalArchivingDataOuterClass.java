package eu.ecb.desp.protobuf.legal_archiving;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;

/**
 * Simulated generated protobuf container for the archived HTTP body section.
 */
public final class LegalArchivingDataOuterClass {

    private LegalArchivingDataOuterClass() {
    }

    /**
     * Business payload section carrying the original HTTP body bytes.
     */
    public static final class LegalArchivingData {

        @JsonProperty("payload")
        private final ByteString payload;

        private LegalArchivingData(Builder builder) {
            this.payload = builder.payload;
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * @return the archived HTTP payload as generated protobuf {@link ByteString}
         */
        public ByteString getPayload() {
            return payload;
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            private ByteString payload;

            /**
             * Sets the archived HTTP payload using the generated protobuf API shape.
             *
             * @param payload the archived HTTP payload
             * @return the current builder
             */
            public Builder setPayload(ByteString payload) {
                this.payload = payload;
                return this;
            }

            /**
             * @return the simulated generated protobuf message
             */
            public LegalArchivingData build() {
                return new LegalArchivingData(this);
            }
        }
    }
}
