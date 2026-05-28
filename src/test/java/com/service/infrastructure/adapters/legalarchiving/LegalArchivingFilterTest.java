package com.service.infrastructure.adapters.legalarchiving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.port.in.LegalArchivingInPort;
import com.service.infrastructure.signature.SignatureContextKeys;
import com.service.infrastructure.signature.validation.model.SignatureData;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalArchivingFilterTest {

    @Mock
    private LegalArchivingInPort legalArchivingInPort;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ContainerResponseContext responseContext;

    private final Map<String, Object> properties = new HashMap<>();
    private LegalArchivingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LegalArchivingFilter(
                new ArchivePayloadSerializer(new ObjectMapper()),
                new LegalArchivingEventMapper(),
                legalArchivingInPort
        );

        when(requestContext.getProperty(any())).thenAnswer(invocation -> properties.get(invocation.getArgument(0)));
        when(legalArchivingInPort.archive(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void shouldArchiveResponseAndPropagateRequestIdHeader() {
        properties.put(LegalArchivingContextKeys.ARCHIVE_ENABLED, Boolean.TRUE);
        properties.put(LegalArchivingContextKeys.OPERATION_ID, "req-1");
        properties.put(LegalArchivingContextKeys.OPERATION, "POST /v1/payments");
        properties.put(
                SignatureContextKeys.RESPONSE_SIGNATURE_DATA,
                new SignatureData(new byte[]{9}, "sig1=(\"@status\")", Map.of("@status", "202"))
        );

        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(headers);
        when(responseContext.getEntity()).thenReturn(Map.of("status", "RECEIVED"));

        filter.filter(requestContext, responseContext);

        ArgumentCaptor<LegalArchivingEvent> eventCaptor = ArgumentCaptor.forClass(LegalArchivingEvent.class);
        verify(legalArchivingInPort).archive(eventCaptor.capture());

        LegalArchivingEvent event = eventCaptor.getValue();
        assertEquals("RESPONSE", event.phase());
        assertEquals("req-1", headers.getFirst("Request-Id"));
        assertEquals("{\"status\":\"RECEIVED\"}", new String(event.payload(), StandardCharsets.UTF_8));
        assertEquals("@status", event.signatureComponents().get(0).key());
    }

    @Test
    void shouldIgnoreResponseWhenArchivingWasNotEnabled() {
        filter.filter(requestContext, responseContext);

        verifyNoInteractions(legalArchivingInPort);
    }

    @Test
    void shouldKeepExistingResponseRequestIdAndSwallowArchiveFailure() {
        properties.put(LegalArchivingContextKeys.ARCHIVE_ENABLED, Boolean.TRUE);
        properties.put(LegalArchivingContextKeys.OPERATION_ID, "req-1");
        properties.put(LegalArchivingContextKeys.OPERATION, "POST /v1/payments");

        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putSingle("Request-Id", "existing-request-id");
        when(responseContext.getHeaders()).thenReturn(headers);
        when(responseContext.getEntity()).thenReturn("failure-body");
        when(legalArchivingInPort.archive(any())).thenReturn(Uni.createFrom().failure(new IllegalStateException("boom")));

        assertDoesNotThrow(() -> filter.filter(requestContext, responseContext));
        assertEquals("existing-request-id", headers.getFirst("Request-Id"));
    }
}
