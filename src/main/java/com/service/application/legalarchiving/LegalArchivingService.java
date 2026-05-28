package com.service.application.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import com.service.application.port.in.LegalArchivingInPort;
import com.service.application.port.out.LegalArchivingPort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * Default application service orchestrating legal archiving through an outbound port.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingService implements LegalArchivingInPort {

    private final LegalArchivingPort legalArchivingPort;

    @Override
    public Uni<Void> archive(LegalArchivingEvent event) {
        return legalArchivingPort.archive(event);
    }
}
