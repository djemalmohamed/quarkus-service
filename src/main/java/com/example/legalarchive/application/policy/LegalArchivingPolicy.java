package com.example.legalarchive.application.policy;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Application policy responsible for deciding whether an HTTP interaction must be archived.
 */
public class LegalArchivingPolicy {

    private final RuleSet inbound;
    private final RuleSet outbound;

    /**
     * Creates the legal-archiving policy for inbound and outbound interactions.
     *
     * @param inbound inbound archiving rules
     * @param outbound outbound archiving rules
     */
    public LegalArchivingPolicy(RuleSet inbound, RuleSet outbound) {
        this.inbound = Objects.requireNonNull(inbound, "inbound must not be null");
        this.outbound = Objects.requireNonNull(outbound, "outbound must not be null");
    }

    /**
     * Determines whether the given interaction must be archived.
     *
     * @param context the neutral decision context built by infrastructure
     * @return {@code true} when legal archiving must be triggered
     */
    public boolean shouldArchive(ArchiveDecisionContext context) {
        RuleSet rules = rulesFor(context);
        if (!rules.enabled()) {
            return false;
        }

        MethodRule methodRule = rules.methodRules().get(normalizeMethod(context.method()));
        if (null == methodRule) {
            return false;
        }

        if (methodRule.overridePaths().contains(normalizePath(context.path()))) {
            return !methodRule.archivesByDefault();
        }

        return methodRule.archivesByDefault();
    }

    /**
     * Derives a stable operation name from the neutral application decision context.
     *
     * @param context the interaction to describe
     * @return the derived operation name
     */
    public String resolveOperation(ArchiveDecisionContext context) {
        String method = normalizeMethod(context.method());
        String path = normalizePath(context.path());
        if (context.direction() == ArchiveDecisionContext.Direction.OUTBOUND) {
            String host = normalizeHost(context.host());
            if (!host.isBlank()) {
                return method + " " + host + path;
            }
        }
        return method + " " + path;
    }

    /**
     * Selects the rule set matching the interaction direction.
     *
     * @param context the current decision context
     * @return the rule set to evaluate
     */
    private RuleSet rulesFor(ArchiveDecisionContext context) {
        return context.direction() == ArchiveDecisionContext.Direction.OUTBOUND ? outbound : inbound;
    }

    /**
     * Normalizes an HTTP method to the uppercase comparison form used by the application policy.
     *
     * @param method the raw HTTP method
     * @return the normalized method
     */
    private String normalizeMethod(String method) {
        return null == method ? "" : method.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes a host for case-insensitive operation naming.
     *
     * @param host the raw host
     * @return the normalized host
     */
    private String normalizeHost(String host) {
        return null == host ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes a path to a slash-prefixed comparison form.
     *
     * @param path the raw path
     * @return the normalized path
     */
    private String normalizePath(String path) {
        if (null == path || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Rule set used by the application layer for one HTTP direction.
     *
     * @param enabled whether this direction is eligible for legal archiving
     * @param methodRules method-specific legal-archiving rules
     */
    public record RuleSet(
            boolean enabled,
            Map<String, MethodRule> methodRules) {

        /**
         * Normalizes method-rule mappings to immutable collections.
         *
         * @param enabled whether this direction is eligible for legal archiving
         * @param methodRules method-specific legal-archiving rules
         */
        public RuleSet {
            methodRules = null == methodRules ? Map.of() : Map.copyOf(methodRules);
        }
    }

    /**
     * One method-specific decision plus the exact paths whose result must be inverted.
     *
     * @param decision the base decision for the method
     * @param overridePaths exact paths whose result must invert the base decision
     */
    public record MethodRule(
            MethodDecision decision,
            Set<String> overridePaths) {

        /**
         * Normalizes the configured override set to an immutable collection.
         *
         * @param decision the base decision for the method
         * @param overridePaths exact paths whose result must invert the base decision
         */
        public MethodRule {
            Objects.requireNonNull(decision, "decision must not be null");
            overridePaths = null == overridePaths ? Set.of() : Set.copyOf(overridePaths);
        }

        /**
         * @return {@code true} when the configured method archives requests by default
         */
        public boolean archivesByDefault() {
            return decision == MethodDecision.ARCHIVE;
        }
    }

    /**
     * Base decision applied to one configured HTTP method.
     */
    public enum MethodDecision {
        ARCHIVE,
        SKIP
    }
}
