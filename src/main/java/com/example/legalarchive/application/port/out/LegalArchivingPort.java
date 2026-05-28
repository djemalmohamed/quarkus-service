package com.example.legalarchive.application.port.out;

import com.example.legalarchive.application.model.LegalArchivingEvent;
import io.smallrye.mutiny.Uni;

/**
 * Outbound port used by the application layer to persist a legal-archiving event.
 */
public interface LegalArchivingPort {

    /**
     * Archives the supplied legal message through the configured outbound adapter.
     *
     * @param event the archive event to emit
     * @return a reactive completion signal for the archival request
     */
    Uni<Void> archive(LegalArchivingEvent event);
}
