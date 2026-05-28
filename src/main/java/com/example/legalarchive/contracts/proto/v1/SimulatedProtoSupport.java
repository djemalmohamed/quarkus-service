package eu.ecb.desp.protobuf.legal_archiving;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.google.protobuf.ByteString;
import java.io.IOException;

final class SimulatedProtoSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .registerModule(new SimpleModule()
                    .addSerializer(ByteString.class, new JsonSerializer<>() {
                        @Override
                        public void serialize(
                                ByteString value,
                                JsonGenerator generator,
                                SerializerProvider serializers) throws IOException {
                            generator.writeBinary(value.toByteArray());
                        }
                    }));

    private SimulatedProtoSupport() {
    }

    static byte[] toByteArray(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize simulated protobuf payload", exception);
        }
    }

    static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to render simulated protobuf payload as JSON", exception);
        }
    }
}
