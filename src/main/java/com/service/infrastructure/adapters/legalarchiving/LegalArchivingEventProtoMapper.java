package com.service.infrastructure.adapters.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.google.protobuf.ByteString;
import com.service.infrastructure.adapters.legalarchiving.contract.LegalArchivingAdditionalDataOuterClass;
import com.service.infrastructure.adapters.legalarchiving.contract.LegalArchivingDataOuterClass;
import com.service.infrastructure.adapters.legalarchiving.contract.LegalArchivingRequestOuterClass;
import com.service.infrastructure.adapters.legalarchiving.contract.SignatureDataOuterClass;
import com.service.infrastructure.adapters.legalarchiving.contract.SignatureParamsOuterClass;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;

/**
 * Maps the domain legal-archiving event to the simulated protobuf contract sent by the adapter.
 */
@ApplicationScoped
public class LegalArchivingEventProtoMapper {

    /**
     * Converts the supplied domain event to the simulated protobuf contract.
     *
     * @param event the domain event to convert
     * @return the protobuf-shaped message expected by the transport adapter
     */
    public LegalArchivingRequestOuterClass.LegalArchivingRequest toProto(LegalArchivingEvent event) {
        LegalArchivingRequestOuterClass.LegalArchivingRequest.Builder builder =
                LegalArchivingRequestOuterClass.LegalArchivingRequest.newBuilder()
                        .setLeaAdditionalData(LegalArchivingAdditionalDataOuterClass.LegalArchivingAdditionalData
                                .newBuilder()
                                .build());

        if (event.hasPayload()) {
            builder.setLegalCoreData(LegalArchivingDataOuterClass.LegalArchivingData.newBuilder()
                    .setPayload(ByteString.copyFrom(event.payload()))
                    .build());
        }

        if (event.hasSignatureData()) {
            SignatureDataOuterClass.SignatureData.Builder signatureBuilder =
                    SignatureDataOuterClass.SignatureData.newBuilder();
            if (null != event.signature()) {
                signatureBuilder.setSignature(ByteString.copyFrom(event.signature(), StandardCharsets.UTF_8));
            }
            if (null != event.signatureInput()) {
                signatureBuilder.setSignatureInput(event.signatureInput());
            }
            builder.setLeaSignatureData(signatureBuilder.build());
        }

        if (!event.signatureParameters().isEmpty()) {
            SignatureParamsOuterClass.SignatureParams.Builder paramsBuilder =
                    SignatureParamsOuterClass.SignatureParams.newBuilder();
            for (LegalArchivingEvent.SignatureParameter parameter : event.signatureParameters()) {
                paramsBuilder.addSignatureParameter(SignatureParamsOuterClass.SignatureParams.SignatureParam.newBuilder()
                        .setSignatureParamKey(parameter.key())
                        .setSignatureParamValue(parameter.value())
                        .build());
            }
            builder.setSignatureParams(paramsBuilder.build());
        }

        return builder.build();
    }
}
