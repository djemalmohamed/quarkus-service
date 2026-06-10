package com.service.application.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.port.out.LegalArchivingPort;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalArchivingServiceTest {

    @Test
    void shouldDelegateArchivingToOutboundPort() {
        LegalArchivingPort port = mock(LegalArchivingPort.class);
        LegalArchivingService service = new LegalArchivingService(port);
        LegalArchivingEvent event = new LegalArchivingEvent(
                "event-1",
                "POST /v1/payments",
                "INBOUND",
                "REQUEST",
                "POST",
                "/v1/payments",
                new byte[]{1},
                "sig1=:Ag==:",
                "sig1=(\"@method\")",
                java.util.List.of()
        );
        Uni<Void> completion = Uni.createFrom().voidItem();
        when(port.archive(event)).thenReturn(completion);

        Uni<Void> result = service.archive(event);

        assertSame(completion, result);
        verify(port).archive(event);
    }
}
