package com.service.application.port.in;

import com.service.application.legalarchiving.model.LegalArchivingEvent;
import io.smallrye.mutiny.Uni;

/**
 * Primary inbound port used to trigger legal archiving from infrastructure adapters.
 */
public interface LegalArchivingInPort {

    /**
     * Requests archival of the supplied legal event.
     *
     * @param event the application event prepared by an inbound adapter
     * @return a reactive completion signal for the archival action
     */
    Uni<Void> archive(LegalArchivingEvent event);
}
