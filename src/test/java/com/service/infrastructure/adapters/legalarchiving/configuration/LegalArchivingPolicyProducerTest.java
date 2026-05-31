package com.service.infrastructure.adapters.legalarchiving.configuration;

import com.service.application.legalarchiving.policy.ArchiveDecisionContext;
import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalArchivingPolicyProducerTest {

    @Test
    void shouldBuildPolicyFromMethodSpecificConfiguration() {
        LegalArchivingProducerConfiguration producerConfiguration = mock(LegalArchivingProducerConfiguration.class);
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.InboundPolicy inboundPolicy = mock(LegalArchivingFeatureConfig.InboundPolicy.class);
        LegalArchivingFeatureConfig.OutboundPolicy outboundPolicy = mock(LegalArchivingFeatureConfig.OutboundPolicy.class);
        LegalArchivingPolicyProducer producer = new LegalArchivingPolicyProducer(producerConfiguration, featureConfig);
        LegalArchivingFeatureConfig.MethodPolicy getPolicy =
                methodPolicy(LegalArchivingFeatureConfig.MethodDecision.SKIP, Optional.of(List.of("toto")));
        LegalArchivingFeatureConfig.MethodPolicy postPolicy =
                methodPolicy(LegalArchivingFeatureConfig.MethodDecision.ARCHIVE, Optional.of(List.of("/lookups/deans")));
        LegalArchivingFeatureConfig.MethodPolicy deletePolicy =
                methodPolicy(LegalArchivingFeatureConfig.MethodDecision.SKIP, Optional.empty());

        when(producerConfiguration.enabled()).thenReturn(true);
        when(featureConfig.inboundPolicy()).thenReturn(inboundPolicy);
        when(featureConfig.outboundPolicy()).thenReturn(outboundPolicy);
        when(inboundPolicy.methods()).thenReturn(Map.of(
                "get", getPolicy,
                "post", postPolicy
        ));
        when(outboundPolicy.methods()).thenReturn(Map.of(
                "delete", deletePolicy
        ));

        LegalArchivingPolicy policy = producer.produce();

        assertTrue(policy.shouldArchive(ArchiveDecisionContext.inbound("GET", "/toto")));
        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("GET", "/health")));
        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/lookups/deans")));
        assertTrue(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/v1/payments")));
        assertFalse(policy.shouldArchive(ArchiveDecisionContext.outbound("DELETE", "partner.example.com", "/v1/payments")));
    }

    @Test
    void shouldDisableArchivingWhenProducerConfigurationIsDisabled() {
        LegalArchivingProducerConfiguration producerConfiguration = mock(LegalArchivingProducerConfiguration.class);
        LegalArchivingFeatureConfig featureConfig = mock(LegalArchivingFeatureConfig.class);
        LegalArchivingFeatureConfig.InboundPolicy inboundPolicy = mock(LegalArchivingFeatureConfig.InboundPolicy.class);
        LegalArchivingFeatureConfig.OutboundPolicy outboundPolicy = mock(LegalArchivingFeatureConfig.OutboundPolicy.class);
        LegalArchivingPolicyProducer producer = new LegalArchivingPolicyProducer(producerConfiguration, featureConfig);
        LegalArchivingFeatureConfig.MethodPolicy postPolicy =
                methodPolicy(LegalArchivingFeatureConfig.MethodDecision.ARCHIVE, Optional.empty());

        when(producerConfiguration.enabled()).thenReturn(false);
        when(featureConfig.inboundPolicy()).thenReturn(inboundPolicy);
        when(featureConfig.outboundPolicy()).thenReturn(outboundPolicy);
        when(inboundPolicy.methods()).thenReturn(Map.of(
                "post", postPolicy
        ));
        when(outboundPolicy.methods()).thenReturn(Map.of());

        LegalArchivingPolicy policy = producer.produce();

        assertFalse(policy.shouldArchive(ArchiveDecisionContext.inbound("POST", "/v1/payments")));
    }

    private LegalArchivingFeatureConfig.MethodPolicy methodPolicy(
            LegalArchivingFeatureConfig.MethodDecision decision,
            Optional<List<String>> overridePaths) {
        LegalArchivingFeatureConfig.MethodPolicy policy = mock(LegalArchivingFeatureConfig.MethodPolicy.class);
        when(policy.decision()).thenReturn(decision);
        when(policy.overridePaths()).thenReturn(overridePaths);
        return policy;
    }
}
