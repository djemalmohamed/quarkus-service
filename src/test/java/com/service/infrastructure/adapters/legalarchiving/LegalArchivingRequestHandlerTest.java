package com.service.infrastructure.adapters.legalarchiving;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.service.application.port.in.LegalArchivingInPort;
import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import com.service.infrastructure.adapters.legalarchiving.ArchivePayloadSerializer;
import com.service.infrastructure.signature.SignatureContextKeys;
import com.service.infrastructure.signature.validation.model.SignatureData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalArchivingRequestHandlerTest {

    @Mock
    private LegalArchivingPolicy legalArchivingPolicy;

    @Mock
    private LegalArchivingInPort legalArchivingUseCase;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private UriInfo uriInfo;

    private final Map<String, Object> properties = new HashMap<>();
    private LegalArchivingRequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LegalArchivingRequestHandler(
                legalArchivingPolicy,
                new ArchivePayloadSerializer(new ObjectMapper()),
                new LegalArchivingEventMapper(),
                legalArchivingUseCase);

        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://example.test/v1/fundings"));
        when(requestContext.getMethod()).thenReturn("POST");
        when(requestContext.getProperty(any())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(requestContext).setProperty(any(), any());
        when(legalArchivingUseCase.archive(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void shouldArchiveInboundRequestUsingPreparedSignatureData() {
        when(legalArchivingPolicy.shouldArchive(any())).thenReturn(true);
        when(legalArchivingPolicy.resolveOperation(any())).thenReturn("POST /v1/fundings");
        when(requestContext.getHeaderString("Request-Id")).thenReturn("req-1");

        byte[] body = "{\"uetr\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
        var componentValues = new LinkedHashMap<String, String>();
        componentValues.put("@method", "POST");
        componentValues.put("request-id", "req-1");
        properties.put(
                SignatureContextKeys.REQUEST_SIGNATURE_DATA,
                new SignatureData(
                        "sig1=:AQID:".getBytes(StandardCharsets.UTF_8),
                        "sig1=(\"@method\" \"request-id\")",
                        componentValues));

        handler.handle(requestContext, body);

        ArgumentCaptor<LegalArchivingEvent> eventCaptor = ArgumentCaptor.forClass(LegalArchivingEvent.class);
        verify(legalArchivingUseCase).archive(eventCaptor.capture());

        LegalArchivingEvent event = eventCaptor.getValue();
        assertEquals("req-1", event.eventId());
        assertEquals("POST /v1/fundings", event.operation());
        assertEquals("INBOUND", event.direction());
        assertEquals("REQUEST", event.phase());
        assertArrayEquals(body, event.payload());
        assertEquals(2, event.signatureComponents().size());
        assertEquals(Boolean.TRUE, properties.get(LegalArchivingContextKeys.ARCHIVE_ENABLED));
        assertEquals("req-1", properties.get(LegalArchivingContextKeys.OPERATION_ID));
        assertEquals("POST /v1/fundings", properties.get(LegalArchivingContextKeys.OPERATION));
    }

    @Test
    void shouldGenerateRequestIdWhenMissingAndArchiveRequestsWithoutEntity() {
        when(legalArchivingPolicy.shouldArchive(any())).thenReturn(true);
        when(legalArchivingPolicy.resolveOperation(any())).thenReturn("POST /v1/fundings");
        when(requestContext.getHeaderString("Request-Id")).thenReturn(null);

        handler.handle(requestContext, null);

        ArgumentCaptor<LegalArchivingEvent> eventCaptor = ArgumentCaptor.forClass(LegalArchivingEvent.class);
        verify(legalArchivingUseCase).archive(eventCaptor.capture());

        assertNotNull(eventCaptor.getValue().eventId());
        assertEquals(0, eventCaptor.getValue().payload().length);
        assertFalse(eventCaptor.getValue().hasPayload());
    }

    @Test
    void shouldIgnoreInboundRequestWhenPolicySkipsArchiving() {
        when(legalArchivingPolicy.shouldArchive(any())).thenReturn(false);

        handler.handle(requestContext, new byte[]{1});

        verifyNoInteractions(legalArchivingUseCase);
    }
}
