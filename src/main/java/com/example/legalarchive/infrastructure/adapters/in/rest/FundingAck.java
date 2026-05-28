package com.example.legalarchive.infrastructure.adapters.in.rest;

import java.math.BigDecimal;

/**
 * Acknowledgement returned by the funding test endpoint.
 *
 * @param status the high-level processing status
 * @param uetr the unique end-to-end transaction reference received from the caller
 * @param amount the amount received from the caller
 */
public record FundingAck(
        String status,
        String uetr,
        BigDecimal amount) {
}
