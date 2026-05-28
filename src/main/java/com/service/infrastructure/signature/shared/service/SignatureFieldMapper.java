package com.service.infrastructure.signature.shared.service;

import com.service.infrastructure.signature.generation.model.OutgoingHttpMessage;
import com.service.infrastructure.signature.rfc.model.CoveredComponent;
import com.service.infrastructure.signature.shared.SignatureConstants;
import com.service.infrastructure.signature.validation.model.IncomingHttpMessage;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps configured RFC-aligned fields to concrete HTTP Message Signatures components.
 */
@ApplicationScoped
public class SignatureFieldMapper {

    private static final Set<String> SIGNATURE_HEADERS = Set.of(SignatureConstants.HEADER_SIGNATURE, SignatureConstants.HEADER_SIGNATURE_INPUT);
    private static final String HEADER_PREFIX = "header:";
    private static final String WILDCARD = "*";

    public List<String> requiredHeaders(List<String> configuredFields) {
        LinkedHashSet<String> headers = new LinkedHashSet<>();
        headers.add(SignatureConstants.HEADER_SIGNATURE_INPUT);
        headers.add(SignatureConstants.HEADER_SIGNATURE);

        for (String field : configuredFields) {
            CoveredComponent component = parseConfiguredField(field);
            if (component == null || component.requestComponent() || component.derived()) {
                continue;
            }
            if (WILDCARD.equals(component.name())) {
                continue;
            }
            headers.add(component.name());
        }

        return new ArrayList<>(headers);
    }

    public List<CoveredComponent> validationComponents(List<String> configuredFields, IncomingHttpMessage message) {
        LinkedHashSet<CoveredComponent> components = new LinkedHashSet<>();
        for (String field : configuredFields) {
            expandField(components, field, message.headers(), message.status());
        }
        return new ArrayList<>(components);
    }

    public List<CoveredComponent> generationComponents(List<String> configuredFields, OutgoingHttpMessage message) {
        LinkedHashSet<CoveredComponent> components = new LinkedHashSet<>();
        Map<String, List<String>> headers = message.headers().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.of(entry.getValue()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        for (String field : configuredFields) {
            expandField(components, field, headers, message.status());
        }
        return new ArrayList<>(components);
    }

    private void expandField(Set<CoveredComponent> components, String field, Map<String, List<String>> headers, Integer status) {
        CoveredComponent component = parseConfiguredField(field);
        if (component == null) {
            return;
        }

        if (WILDCARD.equals(component.name())) {
            if (component.requestComponent() || headers == null) {
                return;
            }
            headers.keySet().stream()
                    .map(CoveredComponent::fromConfiguredField)
                    .filter(entry -> entry != null && !SIGNATURE_HEADERS.contains(entry.name()))
                    .forEach(components::add);
            return;
        }

        if (SignatureConstants.COMPONENT_STATUS.equals(component.name()) && status == null) {
            return;
        }
        components.add(component);
    }

    private CoveredComponent parseConfiguredField(String field) {
        String normalized = SignatureConstants.normalize(field);
        if (normalized.isBlank()) {
            return null;
        }
        if ((HEADER_PREFIX + WILDCARD).equals(normalized)) {
            return CoveredComponent.of(WILDCARD);
        }
        if ((HEADER_PREFIX + WILDCARD + ";req").equals(normalized)) {
            return CoveredComponent.request(WILDCARD);
        }
        return CoveredComponent.fromConfiguredField(field);
    }
}
