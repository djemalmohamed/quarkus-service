package com.service.application.legalarchiving;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import io.smallrye.mutiny.Uni;

/**
 * Application use case responsible for dispatching legal-archiving events.
 */
public interface LegalArchivingUseCase {

    /**
     * Requests archival of the supplied legal message.
     *
     * @param event the domain event prepared by an inbound adapter
     * @return a reactive completion signal for the archival request
     */
    Uni<Void> archive(LegalArchivingEvent event);
}
