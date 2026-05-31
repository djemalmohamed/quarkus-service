package com.service.application.legalarchiving.policy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalArchivingPolicyTest {

    @Test
    void shouldSkipWhenDirectionIsDisabled() {
        LegalArchivingPolicy policy = new LegalArchivingPolicy(
                new LegalArchivingPolicy.RuleSet(false, Map.of(
                        "POST", new LegalArchivingPolicy.MethodRule(LegalArchivingPolicy.MethodDecision.ARCHIVE, Set.of())
                )),
                new LegalArchivingPolicy.RuleSet(true, Map.of())
        );

        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/v1/payments")));
    }

    @Test
    void shouldSkipWhenMethodIsNotConfigured() {
        LegalArchivingPolicy policy = new LegalArchivingPolicy(
                new LegalArchivingPolicy.RuleSet(true, Map.of(
                        "POST", new LegalArchivingPolicy.MethodRule(LegalArchivingPolicy.MethodDecision.ARCHIVE, Set.of())
                )),
                new LegalArchivingPolicy.RuleSet(true, Map.of())
        );

        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("GET", "/v1/payments")));
    }

    @Test
    void shouldInvertSkipDecisionForConfiguredOverridePath() {
        LegalArchivingPolicy policy = new LegalArchivingPolicy(
                new LegalArchivingPolicy.RuleSet(true, Map.of(
                        "GET", new LegalArchivingPolicy.MethodRule(LegalArchivingPolicy.MethodDecision.SKIP, Set.of("/toto"))
                )),
                new LegalArchivingPolicy.RuleSet(true, Map.of())
        );

        assertTrue(policy.shouldArchive(ArchiveDecisionContext.inbound("GET", "/toto")));
        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("GET", "/other")));
    }

    @Test
    void shouldInvertArchiveDecisionForConfiguredOverridePath() {
        LegalArchivingPolicy policy = new LegalArchivingPolicy(
                new LegalArchivingPolicy.RuleSet(true, Map.of(
                        "POST", new LegalArchivingPolicy.MethodRule(LegalArchivingPolicy.MethodDecision.ARCHIVE, Set.of("/lookups/deans"))
                )),
                new LegalArchivingPolicy.RuleSet(true, Map.of())
        );

        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/lookups/deans")));
        assertTrue(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/v1/payments")));
    }

    @Test
    void shouldResolveOutboundOperationWithNormalizedHostAndPath() {
        LegalArchivingPolicy policy = new LegalArchivingPolicy(
                new LegalArchivingPolicy.RuleSet(true, Map.of()),
                new LegalArchivingPolicy.RuleSet(true, Map.of())
        );

        assertEquals(
                "POST partner.example.com/payments",
                policy.resolveOperation(ArchiveDecisionContext.outbound("post", "Partner.Example.com", "payments"))
        );
        assertEquals(
                "GET /",
                policy.resolveOperation(ArchiveDecisionContext.inbound("get", ""))
        );
    }
}
