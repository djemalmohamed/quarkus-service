package com.example.legalarchive.infrastructure.adapters.in.rest;

import java.math.BigDecimal;

/**
 * Test payload accepted by the funding endpoint exposed by the scaffold.
 *
 * @param uetr the unique end-to-end transaction reference
 * @param amount the funding amount to archive alongside the request
 */
public record FundingRequest(
        String uetr,
        BigDecimal amount) {
}
