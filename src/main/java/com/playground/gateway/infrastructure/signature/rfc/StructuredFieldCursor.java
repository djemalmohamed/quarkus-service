package com.playground.gateway.infrastructure.signature.rfc;

import com.playground.gateway.infrastructure.signature.shared.error.SignatureInfrastructureException;

import java.util.Base64;

/**
 * Small cursor helper used by the local RFC parsing code.
 *
 * <p>The signature module only needs a narrow subset of structured field parsing for
 * {@code Signature} and {@code Signature-Input}. This cursor keeps the parsing logic explicit and
 * sequential without falling back to ad-hoc {@code split}/{@code substring} chains.</p>
 */
final class StructuredFieldCursor {

    private final String input;
    private final String headerName;
    private int index;

    StructuredFieldCursor(String input, String headerName) {
        this.input = input == null ? "" : input;
        this.headerName = headerName;
    }

    int position() {
        return index;
    }

    boolean hasMore() {
        return index < input.length();
    }

    void skipOptionalWhitespace() {
        while (hasMore() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    boolean consumeIf(char expected) {
        if (hasMore() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    void expect(char expected, String context) {
        if (!consumeIf(expected)) {
            throw error("Expected '" + expected + "' " + context);
        }
    }

    char current() {
        if (!hasMore()) {
            throw error("Unexpected end of " + headerName + " header");
        }
        return input.charAt(index);
    }

    String readToken(String context) {
        if (!hasMore() || !isTokenChar(input.charAt(index))) {
            throw error("Invalid " + context);
        }

        int start = index;
        while (hasMore() && isTokenChar(input.charAt(index))) {
            index++;
        }
        return input.substring(start, index);
    }

    String readQuotedString(String context) {
        expect('"', "for " + context);
        StringBuilder value = new StringBuilder();
        while (hasMore()) {
            char current = input.charAt(index++);
            if (current == '"') {
                return value.toString();
            }
            if (current == '\\') {
                if (!hasMore()) {
                    throw error("Unterminated escape sequence in " + context);
                }
                value.append(input.charAt(index++));
                continue;
            }
            value.append(current);
        }
        throw error("Unterminated quoted string in " + context);
    }

    String readParameterValue(String parameterName) {
        skipOptionalWhitespace();
        if (!hasMore()) {
            throw error("Missing value for parameter " + parameterName);
        }
        if (current() == '"') {
            return readQuotedString("parameter " + parameterName);
        }

        int start = index;
        while (hasMore()) {
            char current = input.charAt(index);
            if (current == ';' || Character.isWhitespace(current)) {
                break;
            }
            index++;
        }
        if (start == index) {
            throw error("Missing value for parameter " + parameterName);
        }
        return input.substring(start, index);
    }

    Long readLong(String parameterName, String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException error) {
            throw error("Invalid numeric value for " + parameterName, error);
        }
    }

    byte[] readByteSequence(String context) {
        expect(':', "to start " + context);
        int start = index;
        while (hasMore() && input.charAt(index) != ':') {
            index++;
        }
        if (!hasMore()) {
            throw error("Unterminated byte sequence in " + context);
        }
        String encoded = input.substring(start, index);
        expect(':', "to end " + context);
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw error(context + " is not valid Base64", error);
        }
    }

    void expectEnd() {
        skipOptionalWhitespace();
        if (hasMore()) {
            throw error("Unexpected trailing characters in " + headerName + " header");
        }
    }

    private boolean isTokenChar(char value) {
        return Character.isLetterOrDigit(value)
                || value == '_'
                || value == '-'
                || value == '.'
                || value == '*';
    }

    private SignatureInfrastructureException error(String message) {
        return error(message, null);
    }

    private SignatureInfrastructureException error(String message, Exception cause) {
        String detailedMessage = message + " at index " + index;
        return cause == null
                ? new SignatureInfrastructureException(detailedMessage)
                : new SignatureInfrastructureException(detailedMessage, cause);
    }
}
