package com.playground.gateway.infrastructure.signature.shared.service;

import com.playground.gateway.infrastructure.signature.config.SignatureConfig;
import com.playground.gateway.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.playground.gateway.infrastructure.signature.generation.model.ResolvedGenerationPolicy;
import com.playground.gateway.infrastructure.signature.rfc.model.CoveredComponent;
import com.playground.gateway.infrastructure.signature.shared.SignatureConstants;
import com.playground.gateway.infrastructure.signature.shared.error.SignatureConfigurationException;
import com.playground.gateway.infrastructure.signature.validation.model.IncomingHttpMessage;
import com.playground.gateway.infrastructure.signature.validation.model.ResolvedValidationPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves validation and generation rules from the declarative signature configuration.
 *
 * <p>Rules are matched with a human-readable endpoint expression such as {@code POST /v1/payments-instructions}
 * or {@code ALL}. The resolver converts the configured high-level fields into the concrete
 * signature components expected by the lower-level engine.</p>
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SignaturePolicyResolver {

    private List<CompiledValidationEndpoint> compiledInboundValidationEndpoints;
    private List<CompiledGenerationEndpoint> compiledInboundGenerationEndpoints;
    private List<CompiledValidationEndpoint> compiledOutboundValidationEndpoints;
    private List<CompiledGenerationEndpoint> compiledOutboundGenerationEndpoints;
    private Map<String, CompiledValidationEndpoint> compiledInboundValidationEndpointsByName;
    private Map<String, CompiledGenerationEndpoint> compiledInboundGenerationEndpointsByName;
    private Map<String, CompiledValidationEndpoint> compiledOutboundValidationEndpointsByName;
    private Map<String, CompiledGenerationEndpoint> compiledOutboundGenerationEndpointsByName;

    private final SignatureConfig config;

    private final SignatureFieldMapper fieldMapper;

    /**
     * Precompiles endpoint rules once at startup to avoid re-parsing matcher expressions on each request.
     */
    @PostConstruct
    void init() {
        compiledInboundValidationEndpoints = config.inbound().validation().endpoints().entrySet().stream()
                .map(entry -> CompiledValidationEndpoint.from(entry.getKey(), entry.getValue()))
                .toList();
        compiledInboundValidationEndpointsByName = indexValidationEndpoints(compiledInboundValidationEndpoints);

        compiledInboundGenerationEndpoints = config.inbound().generation().endpoints().entrySet().stream()
                .map(entry -> CompiledGenerationEndpoint.from(entry.getKey(), entry.getValue()))
                .toList();
        compiledInboundGenerationEndpointsByName = indexGenerationEndpoints(compiledInboundGenerationEndpoints);

        compiledOutboundValidationEndpoints = config.outbound().validation().endpoints().entrySet().stream()
                .map(entry -> CompiledValidationEndpoint.from(entry.getKey(), entry.getValue()))
                .toList();
        compiledOutboundValidationEndpointsByName = indexValidationEndpoints(compiledOutboundValidationEndpoints);

        compiledOutboundGenerationEndpoints = config.outbound().generation().endpoints().entrySet().stream()
                .map(entry -> CompiledGenerationEndpoint.from(entry.getKey(), entry.getValue()))
                .toList();
        compiledOutboundGenerationEndpointsByName = indexGenerationEndpoints(compiledOutboundGenerationEndpoints);
    }

    /**
     * Resolves the validation rule to apply to an inbound request.
     *
     * @param message inbound message being validated
     * @param requestedPolicy explicit endpoint rule name, or {@code null} to use matcher-based resolution
     * @return resolved validation policy
     */
    public ResolvedValidationPolicy resolveInboundValidation(IncomingHttpMessage message, String requestedPolicy) {
        return resolveValidation(
                compiledInboundValidationEndpoints,
                compiledInboundValidationEndpointsByName,
                message,
                requestedPolicy,
                "inbound validation"
        );
    }

    /**
     * Resolves an optional inbound validation policy for request filters.
     *
     * @param message inbound message being inspected
     * @return resolved validation policy
     */
    public Optional<ResolvedValidationPolicy> resolveInboundValidationOptional(IncomingHttpMessage message) {
        return resolveValidationOptional(compiledInboundValidationEndpoints, message);
    }

    /**
     * Resolves the generation rule to apply to an inbound synchronous response.
     *
     * @param requestedPolicy explicit endpoint rule name, or {@code null} to use matcher-based resolution
     * @param message outbound view of the HTTP response being signed
     * @return resolved generation policy when one applies
     */
    public Optional<ResolvedGenerationPolicy> resolveInboundGeneration(String requestedPolicy, OutgoingHttpMessage message) {
        return resolveGeneration(
                compiledInboundGenerationEndpoints,
                compiledInboundGenerationEndpointsByName,
                requestedPolicy,
                null,
                message
        );
    }

    /**
     * Resolves the generation rule to apply to an outbound HTTP client request.
     *
     * @param requestedPolicy explicit endpoint rule name, or {@code null} to use auto-resolution
     * @param targetName optional logical target name used by the output adapter
     * @param message outbound message being signed
     * @return resolved generation policy when one applies
     */
    public Optional<ResolvedGenerationPolicy> resolveOutboundGeneration(String requestedPolicy, String targetName, OutgoingHttpMessage message) {
        return resolveGeneration(
                compiledOutboundGenerationEndpoints,
                compiledOutboundGenerationEndpointsByName,
                requestedPolicy,
                targetName,
                message
        );
    }

    /**
     * Resolves the validation rule to apply to a synchronous downstream response.
     *
     * @param message inbound view of the downstream response being validated
     * @param requestedPolicy explicit endpoint rule name, or {@code null} to use matcher-based resolution
     * @return resolved validation policy
     */
    public ResolvedValidationPolicy resolveOutboundValidation(IncomingHttpMessage message, String requestedPolicy) {
        return resolveValidation(
                compiledOutboundValidationEndpoints,
                compiledOutboundValidationEndpointsByName,
                message,
                requestedPolicy,
                "outbound validation"
        );
    }

    /**
     * Resolves an optional outbound validation policy for synchronous downstream responses.
     *
     * @param message inbound view of the downstream response being inspected
     * @return resolved validation policy when one applies
     */
    public Optional<ResolvedValidationPolicy> resolveOutboundValidationOptional(IncomingHttpMessage message) {
        return resolveValidationOptional(compiledOutboundValidationEndpoints, message);
    }

    /**
     * Builds a by-name lookup map for compiled validation endpoints.
     *
     * @param endpoints compiled validation endpoints
     * @return immutable map keyed by endpoint name
     */
    private Map<String, CompiledValidationEndpoint> indexValidationEndpoints(List<CompiledValidationEndpoint> endpoints) {
        Map<String, CompiledValidationEndpoint> indexedEndpoints = new LinkedHashMap<>();
        endpoints.forEach(endpoint -> indexedEndpoints.put(endpoint.name(), endpoint));
        return Map.copyOf(indexedEndpoints);
    }

    /**
     * Builds a by-name lookup map for compiled generation endpoints.
     *
     * @param endpoints compiled generation endpoints
     * @return immutable map keyed by endpoint name
     */
    private Map<String, CompiledGenerationEndpoint> indexGenerationEndpoints(List<CompiledGenerationEndpoint> endpoints) {
        Map<String, CompiledGenerationEndpoint> indexedEndpoints = new LinkedHashMap<>();
        endpoints.forEach(endpoint -> indexedEndpoints.put(endpoint.name(), endpoint));
        return Map.copyOf(indexedEndpoints);
    }

    /**
     * Translates one configured validation endpoint into the internal resolved form.
     *
     * @param endpoint endpoint configuration
     * @param message inbound message
     * @return resolved validation policy
     */
    private ResolvedValidationPolicy toValidationPolicy(CompiledValidationEndpoint endpoint, IncomingHttpMessage message) {
        List<String> fields = endpoint.fields();
        return new ResolvedValidationPolicy(
                endpoint.name(),
                endpoint.failFast(),
                endpoint.bodyRequired(),
                fieldMapper.requiredHeaders(fields),
                fieldMapper.validationComponents(fields, message),
                endpoint.signatureAlgorithm(),
                endpoint.digestAlgorithm(),
                endpoint.requireDigest(),
                endpoint.requireCreated(),
                endpoint.requireExpires(),
                endpoint.maxSignatureAge(),
                endpoint.clockSkew(),
                endpoint.requireNonce(),
                endpoint.nonceCheckMode(),
                endpoint.rejectOnFailure()
        );
    }

    /**
     * Translates one configured generation endpoint into the internal resolved form.
     *
     * @param endpoint endpoint configuration
     * @param message outbound message
     * @return resolved generation policy
     */
    private ResolvedGenerationPolicy toGenerationPolicy(CompiledGenerationEndpoint endpoint, OutgoingHttpMessage message) {
        List<String> fields = endpoint.fields();
        return new ResolvedGenerationPolicy(
                endpoint.name(),
                endpoint.signatureLabel(),
                endpoint.keyId(),
                endpoint.algorithm(),
                endpoint.digestAlgorithm(),
                fieldMapper.generationComponents(fields, message),
                endpoint.requireDigest(),
                endpoint.includeCreated(),
                endpoint.includeExpires(),
                endpoint.expiresIn(),
                endpoint.includeNonce(),
                endpoint.nonceLength()
        );
    }

    private record CompiledValidationEndpoint(
            String name,
            boolean enabled,
            boolean catchAll,
            String method,
            Pattern pathPattern,
            List<String> fields,
            boolean failFast,
            boolean bodyRequired,
            String signatureAlgorithm,
            String digestAlgorithm,
            boolean requireDigest,
            boolean requireCreated,
            boolean requireExpires,
            java.time.Duration maxSignatureAge,
            java.time.Duration clockSkew,
            boolean requireNonce,
            String nonceCheckMode,
            boolean rejectOnFailure
    ) {
        /**
         * Precompiles one declarative validation rule into a runtime-friendly representation.
         *
         * @param name configured endpoint name
         * @param endpoint raw endpoint configuration
         * @return compiled endpoint ready for fast runtime matching
         */
        static CompiledValidationEndpoint from(String name, SignatureConfig.ValidationEndpointConfig endpoint) {
            String matcher = endpoint.matcher();
            boolean catchAll = matcher == null
                    || matcher.isBlank()
                    || SignatureConstants.MATCHER_ALL.equalsIgnoreCase(matcher.trim())
                    || SignatureConstants.MATCHER_ALL.equalsIgnoreCase(name);
            MatcherParts matcherParts = catchAll ? new MatcherParts(null, null) : MatcherParts.parse(matcher);
            List<String> fields = List.copyOf(endpoint.fields());
            return new CompiledValidationEndpoint(
                    name,
                    endpoint.enabled(),
                    catchAll,
                    matcherParts.method(),
                    matcherParts.pathPattern(),
                    fields,
                    endpoint.failFast(),
                    requiresBodyAccessStatic(fields),
                    normalizeStatic(endpoint.algorithm()),
                    normalizeDigestStatic(endpoint.digestAlgorithm()),
                    requiresDigestStatic(fields),
                    endpoint.requireCreated(),
                    endpoint.requireExpires(),
                    endpoint.maxSignatureAge(),
                    endpoint.clockSkew(),
                    endpoint.requireNonce(),
                    endpoint.nonceCheckMode(),
                    endpoint.rejectOnFailure()
            );
        }

        /**
         * Checks whether the current inbound request matches this compiled endpoint.
         *
         * @param actualMethod inbound HTTP method
         * @param actualPath inbound request path
         * @return {@code true} when the endpoint applies to the request
         */
        boolean matches(String actualMethod, String actualPath) {
            if (catchAll) {
                return true;
            }
            String safeMethod = actualMethod == null ? "" : actualMethod;
            String safePath = actualPath == null ? "" : actualPath;
            return method.equalsIgnoreCase(safeMethod) && pathPattern.matcher(safePath).matches();
        }
    }

    private record CompiledGenerationEndpoint(
            String name,
            boolean enabled,
            boolean catchAll,
            String targetName,
            String method,
            Pattern pathPattern,
            List<String> fields,
            String signatureLabel,
            String keyId,
            String algorithm,
            String digestAlgorithm,
            boolean requireDigest,
            boolean includeCreated,
            boolean includeExpires,
            java.time.Duration expiresIn,
            boolean includeNonce,
            int nonceLength
    ) {
        /**
         * Precompiles one declarative generation rule into a runtime-friendly representation.
         *
         * @param name configured endpoint name
         * @param endpoint raw endpoint configuration
         * @return compiled endpoint ready for fast runtime matching
         */
        static CompiledGenerationEndpoint from(String name, SignatureConfig.GenerationEndpointConfig endpoint) {
            String matcher = endpoint.matcher();
            boolean catchAll = matcher == null
                    || matcher.isBlank()
                    || SignatureConstants.MATCHER_ALL.equalsIgnoreCase(matcher.trim())
                    || SignatureConstants.MATCHER_ALL.equalsIgnoreCase(name);
            MatcherParts matcherParts = catchAll ? new MatcherParts(null, null) : MatcherParts.parse(matcher);
            List<String> fields = List.copyOf(endpoint.fields());
            return new CompiledGenerationEndpoint(
                    name,
                    endpoint.enabled(),
                    catchAll,
                    endpoint.targetName().orElse(null),
                    matcherParts.method(),
                    matcherParts.pathPattern(),
                    fields,
                    endpoint.signatureLabel(),
                    endpoint.keyId(),
                    normalizeStatic(endpoint.algorithm()),
                    normalizeDigestStatic(endpoint.digestAlgorithm()),
                    requiresDigestStatic(fields),
                    endpoint.includeCreated(),
                    endpoint.includeExpires(),
                    endpoint.expiresIn(),
                    endpoint.includeNonce(),
                    endpoint.nonceLength()
            );
        }

        /**
         * Checks whether the current outbound request matches this compiled endpoint.
         *
         * @param actualMethod outbound HTTP method
         * @param actualPath outbound request path
         * @return {@code true} when the endpoint applies to the request
         */
        boolean matches(String actualMethod, String actualPath) {
            if (catchAll) {
                return true;
            }
            String safeMethod = actualMethod == null ? "" : actualMethod;
            String safePath = actualPath == null ? "" : actualPath;
            return method.equalsIgnoreCase(safeMethod) && pathPattern.matcher(safePath).matches();
        }

        /**
         * Checks whether the rule is explicitly bound to the current logical target name.
         *
         * @param actualTargetName logical target name resolved by the HTTP adapter
         * @return {@code true} when the target name matches
         */
        boolean matchesTarget(String actualTargetName) {
            return targetName != null && targetName.equals(actualTargetName);
        }
    }

    /**
     * Resolves one validation family using either an explicit rule name or matcher-based resolution.
     *
     * @param endpoints compiled endpoints for one validation family
     * @param endpointsByName direct lookup by configured rule name
     * @param message inbound message being validated
     * @param requestedPolicy explicit configured rule name, or {@code null}
     * @param familyDescription human-readable family name used in configuration errors
     * @return resolved validation policy
     */
    private ResolvedValidationPolicy resolveValidation(
            List<CompiledValidationEndpoint> endpoints,
            Map<String, CompiledValidationEndpoint> endpointsByName,
            IncomingHttpMessage message,
            String requestedPolicy,
            String familyDescription
    ) {
        if (requestedPolicy != null && !requestedPolicy.isBlank()) {
            CompiledValidationEndpoint endpoint = endpointsByName.get(requestedPolicy);
            if (endpoint == null || !endpoint.enabled()) {
                throw new SignatureConfigurationException("Unknown or disabled " + familyDescription + " endpoint: " + requestedPolicy);
            }
            return toValidationPolicy(endpoint, message);
        }

        return resolveValidationOptional(endpoints, message)
                .orElseThrow(() -> new SignatureConfigurationException("No enabled " + familyDescription + " endpoint is configured"));
    }

    /**
     * Resolves one validation family through matcher-based lookup only.
     *
     * @param endpoints compiled endpoints for one validation family
     * @param message inbound message being inspected
     * @return resolved validation policy when one applies
     */
    private Optional<ResolvedValidationPolicy> resolveValidationOptional(
            List<CompiledValidationEndpoint> endpoints,
            IncomingHttpMessage message
    ) {
        Optional<CompiledValidationEndpoint> matched = endpoints.stream()
                .filter(CompiledValidationEndpoint::enabled)
                .filter(endpoint -> !endpoint.catchAll() && endpoint.matches(message.method(), message.path()))
                .findFirst();
        if (matched.isPresent()) {
            return Optional.of(toValidationPolicy(matched.get(), message));
        }

        return endpoints.stream()
                .filter(CompiledValidationEndpoint::enabled)
                .filter(CompiledValidationEndpoint::catchAll)
                .findFirst()
                .map(endpoint -> toValidationPolicy(endpoint, message));
    }

    /**
     * Resolves one generation family using either an explicit rule name or matcher-based resolution.
     *
     * @param endpoints compiled endpoints for one generation family
     * @param endpointsByName direct lookup by configured rule name
     * @param requestedPolicy explicit configured rule name, or {@code null}
     * @param targetName optional logical target name used by outbound adapters
     * @param message outbound message being signed
     * @return resolved generation policy when one applies
     */
    private Optional<ResolvedGenerationPolicy> resolveGeneration(
            List<CompiledGenerationEndpoint> endpoints,
            Map<String, CompiledGenerationEndpoint> endpointsByName,
            String requestedPolicy,
            String targetName,
            OutgoingHttpMessage message
    ) {
        if (requestedPolicy != null && !requestedPolicy.isBlank()) {
            CompiledGenerationEndpoint endpoint = endpointsByName.get(requestedPolicy);
            if (endpoint == null || !endpoint.enabled()) {
                throw new SignatureConfigurationException("Unknown or disabled generation endpoint: " + requestedPolicy);
            }
            return Optional.of(toGenerationPolicy(endpoint, message));
        }

        Optional<CompiledGenerationEndpoint> matched = endpoints.stream()
                .filter(CompiledGenerationEndpoint::enabled)
                .filter(endpoint -> !endpoint.catchAll())
                .filter(endpoint -> endpoint.matchesTarget(targetName)
                        || endpoint.matches(message.method(), message.uri() == null ? null : message.uri().getPath()))
                .findFirst();
        if (matched.isPresent()) {
            return Optional.of(toGenerationPolicy(matched.get(), message));
        }

        return endpoints.stream()
                .filter(CompiledGenerationEndpoint::enabled)
                .filter(CompiledGenerationEndpoint::catchAll)
                .findFirst()
                .map(endpoint -> toGenerationPolicy(endpoint, message));
    }

    private record MatcherParts(String method, Pattern pathPattern) {
        /**
         * Parses a human-readable matcher such as {@code POST /v1/payments-instructions} or {@code ALL}.
         *
         * @param matcher configured matcher expression
         * @return parsed matcher parts
         */
        static MatcherParts parse(String matcher) {
            String trimmed = matcher == null ? SignatureConstants.MATCHER_ALL : matcher.trim();
            int separator = trimmed.indexOf(' ');
            if (separator < 0) {
                return new MatcherParts(SignatureConstants.MATCHER_ALL, compilePattern(trimmed));
            }
            return new MatcherParts(
                    trimmed.substring(0, separator).trim(),
                    compilePattern(trimmed.substring(separator + 1).trim())
            );
        }

        /**
         * Compiles a simple wildcard path expression into a regex.
         *
         * @param pattern configured wildcard path
         * @return compiled regex pattern
         */
        private static Pattern compilePattern(String pattern) {
            String[] segments = pattern.split("\\*", -1);
            StringBuilder regex = new StringBuilder();
            for (int index = 0; index < segments.length; index++) {
                regex.append(Pattern.quote(segments[index]));
                if (index < segments.length - 1) {
                    regex.append(".*");
                }
            }
            return Pattern.compile(regex.toString());
        }
    }

    /**
     * Determines whether a compiled rule requires digest handling.
     *
     * @param fields configured field declarations
     * @return {@code true} when digest validation or generation is mandatory
     */
    private static boolean requiresDigestStatic(List<String> fields) {
        return fields.stream()
                .map(CoveredComponent::fromConfiguredField)
                .anyMatch(component -> component != null
                        && !component.requestComponent()
                        && SignatureConstants.HEADER_CONTENT_DIGEST.equals(component.name()));
    }

    /**
     * Determines whether a compiled rule requires the raw body bytes to be materialized.
     *
     * @param fields configured field declarations
     * @return {@code true} when body access is required at runtime
     */
    private static boolean requiresBodyAccessStatic(List<String> fields) {
        return requiresDigestStatic(fields);
    }

    /**
     * Normalizes configured values to a stable lowercase representation.
     *
     * @param value raw configured value
     * @return normalized value
     */
    private static String normalizeStatic(String value) {
        return SignatureConstants.normalize(value);
    }

    /**
     * Normalizes digest aliases exposed in configuration.
     *
     * @param value raw configured digest algorithm
     * @return canonical digest algorithm identifier
     */
    private static String normalizeDigestStatic(String value) {
        return switch (normalizeStatic(value)) {
            case "sha256" -> SignatureConstants.DIGEST_SHA_256;
            case "sha512" -> SignatureConstants.DIGEST_SHA_512;
            default -> normalizeStatic(value);
        };
    }
}
