package eu.ecb.desp.protobuf.legal_archiving;

/**
 * Simulated generated protobuf container for the contract extension section.
 */
public final class LegalArchivingAdditionalDataOuterClass {

    private LegalArchivingAdditionalDataOuterClass() {
    }

    /**
     * Empty message aligned with the current business contract.
     */
    public static final class LegalArchivingAdditionalData {

        private LegalArchivingAdditionalData(Builder builder) {
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            public LegalArchivingAdditionalData build() {
                return new LegalArchivingAdditionalData(this);
            }
        }
    }
}
