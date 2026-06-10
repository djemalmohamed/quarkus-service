package com.service.infrastructure.adapters.legalarchiving.contract;

/**
 * Simulated generated protobuf container for the contract extension section.
 */
public final class LegalArchivingAdditionalDataOuterClass {

    private LegalArchivingAdditionalDataOuterClass() {
    }

    /**
     * Additional legal-archiving metadata aligned with the shared business contract.
     */
    public static final class LegalArchivingAdditionalData {

        @com.fasterxml.jackson.annotation.JsonProperty("http_path")
        private final String httpPath;

        @com.fasterxml.jackson.annotation.JsonProperty("http_method")
        private final String httpMethod;

        private LegalArchivingAdditionalData(Builder builder) {
            this.httpPath = builder.httpPath;
            this.httpMethod = builder.httpMethod;
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        public String getHttpPath() {
            return httpPath;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            private String httpPath;
            private String httpMethod;

            public Builder setHttpPath(String httpPath) {
                this.httpPath = httpPath;
                return this;
            }

            public Builder setHttpMethod(String httpMethod) {
                this.httpMethod = httpMethod;
                return this;
            }

            public LegalArchivingAdditionalData build() {
                return new LegalArchivingAdditionalData(this);
            }
        }
    }
}
