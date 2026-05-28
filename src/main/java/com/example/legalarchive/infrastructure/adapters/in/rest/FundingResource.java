package com.example.legalarchive.infrastructure.adapters.in.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Single test controller exposed by the scaffold to simulate an archived funding request.
 */
@Path("/v1/fundings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FundingResource {

    /**
     * Receives a funding request and returns an acknowledgement while the inbound request is archived.
     *
     * @param payload the funding payload to archive
     * @return an accepted response acknowledging the funding request
     */
    @POST
    public Response create(FundingRequest payload) {
        return Response.accepted(new FundingAck(
                "RECEIVED",
                payload.uetr(),
                payload.amount()))
                .build();
    }
}
