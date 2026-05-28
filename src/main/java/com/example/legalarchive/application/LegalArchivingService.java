package com.example.legalarchive.application;

import com.example.legalarchive.application.model.LegalArchivingEvent;
import com.example.legalarchive.application.port.out.LegalArchivingPort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * Default application service orchestrating legal archiving through an outbound port.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingService implements LegalArchivingUseCase {

    private final LegalArchivingPort legalArchivingPort;

    @Override
    public Uni<Void> archive(LegalArchivingEvent event) {
        return legalArchivingPort.archive(event);
    }
}
