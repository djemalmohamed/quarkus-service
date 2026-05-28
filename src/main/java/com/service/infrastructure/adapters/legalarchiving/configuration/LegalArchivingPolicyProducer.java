package com.service.infrastructure.adapters.legalarchiving.configuration;

import com.service.application.legalarchiving.policy.LegalArchivingPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Builds the application legal-archiving policy from infrastructure configuration properties.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LegalArchivingPolicyProducer {

    private final LegalArchivingProducerConfiguration producerConfiguration;
    private final LegalArchivingFeatureConfig featureConfig;

    /**
     * Produces the immutable application policy used by inbound and outbound adapters.
     *
     * @return the application policy derived from Quarkus configuration
     */
    @Produces
    public LegalArchivingPolicy produce() {
        return new LegalArchivingPolicy(
                inboundRules(),
                outboundRules());
    }

    /**
     * Builds the inbound rule set by combining the feature flag and method-specific policies.
     *
     * @return the effective inbound rule set
     */
    private LegalArchivingPolicy.RuleSet inboundRules() {
        return new LegalArchivingPolicy.RuleSet(
                producerConfiguration.enabled(),
                mapMethodRules(featureConfig.inboundPolicy().methods()));
    }

    /**
     * Builds the outbound rule set by combining the feature flag and method-specific policies.
     *
     * @return the effective outbound rule set
     */
    private LegalArchivingPolicy.RuleSet outboundRules() {
        return new LegalArchivingPolicy.RuleSet(
                producerConfiguration.enabled(),
                mapMethodRules(featureConfig.outboundPolicy().methods()));
    }

    /**
     * Converts infrastructure method-policy mappings to the immutable application representation.
     *
     * @param configuredRules the configured method-specific rules
     * @return the immutable application rule map
     */
    private Map<String, LegalArchivingPolicy.MethodRule> mapMethodRules(
            Map<String, LegalArchivingFeatureConfig.MethodPolicy> configuredRules) {
        if (null == configuredRules || configuredRules.isEmpty()) {
            return Map.of();
        }

        return configuredRules.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> normalizeMethod(entry.getKey()),
                        entry -> toMethodRule(entry.getValue())));
    }

    /**
     * Converts one infrastructure method policy to the application method rule.
     *
     * @param methodPolicy the configured infrastructure method policy
     * @return the application method rule
     */
    private LegalArchivingPolicy.MethodRule toMethodRule(
            LegalArchivingFeatureConfig.MethodPolicy methodPolicy) {
        return new LegalArchivingPolicy.MethodRule(
                mapDecision(methodPolicy.decision()),
                normalizePaths(methodPolicy.overridePaths()));
    }

    /**
     * Maps infrastructure enum values to the application policy enum.
     *
     * @param decision the configured infrastructure decision
     * @return the corresponding application decision
     */
    private LegalArchivingPolicy.MethodDecision mapDecision(
            LegalArchivingFeatureConfig.MethodDecision decision) {
        return LegalArchivingPolicy.MethodDecision.valueOf(decision.name());
    }

    /**
     * Normalizes configured override paths to slash-prefixed values.
     *
     * @param configuredPaths the configured override-paths list
     * @return the normalized path set
     */
    private Set<String> normalizePaths(Optional<List<String>> configuredPaths) {
        return configuredPaths.orElseGet(List::of).stream()
                .map(String::trim)
                .filter(path -> !path.isBlank())
                .map(this::normalizePath)
                .collect(Collectors.toSet());
    }

    /**
     * Normalizes an HTTP method to uppercase.
     *
     * @param method the raw method
     * @return the normalized method
     */
    private String normalizeMethod(String method) {
        return null == method ? "" : method.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes a path to a slash-prefixed comparison form.
     *
     * @param path the raw path or path prefix
     * @return the normalized path value
     */
    private String normalizePath(String path) {
        if (null == path || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
