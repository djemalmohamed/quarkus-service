package eu.ecb.desp.protobuf.legal_archiving;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulated generated protobuf container for resolved signature components.
 */
public final class SignatureParamsOuterClass {

    private SignatureParamsOuterClass() {
    }

    /**
     * Repeated collection of parameters derived from the covered components of {@code Signature-Input}.
     */
    public static final class SignatureParams {

        @JsonProperty("signature_parameter")
        private final List<SignatureParam> signatureParameter;

        private SignatureParams(Builder builder) {
            this.signatureParameter = List.copyOf(builder.signatureParameter);
        }

        /**
         * @return a builder for the simulated protobuf message
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        public List<SignatureParam> getSignatureParameterList() {
            return signatureParameter;
        }

        /**
         * Builder matching the API style of generated protobuf Java classes.
         */
        public static final class Builder {

            private final List<SignatureParam> signatureParameter = new ArrayList<>();

            public Builder addSignatureParameter(SignatureParam signatureParam) {
                this.signatureParameter.add(signatureParam);
                return this;
            }

            public boolean hasEntries() {
                return !signatureParameter.isEmpty();
            }

            public SignatureParams build() {
                return new SignatureParams(this);
            }
        }

        /**
         * Name/value representation of a covered HTTP component resolved from the request or response.
         */
        public static final class SignatureParam {

            @JsonProperty("signature_param_key")
            private final String signatureParamKey;

            @JsonProperty("signature_param_value")
            private final String signatureParamValue;

            private SignatureParam(Builder builder) {
                this.signatureParamKey = builder.signatureParamKey;
                this.signatureParamValue = builder.signatureParamValue;
            }

            /**
             * @return a builder for the simulated protobuf message
             */
            public static Builder newBuilder() {
                return new Builder();
            }

            /**
             * @return the parameter key generated from the covered component name
             */
            public String getSignatureParamKey() {
                return signatureParamKey;
            }

            /**
             * @return the resolved parameter value generated from the covered component value
             */
            public String getSignatureParamValue() {
                return signatureParamValue;
            }

            /**
             * Builder matching the API style of generated protobuf Java classes.
             */
            public static final class Builder {

                private String signatureParamKey;
                private String signatureParamValue;

                /**
                 * @param signatureParamKey the covered component name
                 * @return the current builder
                 */
                public Builder setSignatureParamKey(String signatureParamKey) {
                    this.signatureParamKey = signatureParamKey;
                    return this;
                }

                /**
                 * @param signatureParamValue the resolved covered component value
                 * @return the current builder
                 */
                public Builder setSignatureParamValue(String signatureParamValue) {
                    this.signatureParamValue = signatureParamValue;
                    return this;
                }

                /**
                 * @return the simulated generated protobuf message
                 */
                public SignatureParam build() {
                    return new SignatureParam(this);
                }
            }
        }
    }
}
