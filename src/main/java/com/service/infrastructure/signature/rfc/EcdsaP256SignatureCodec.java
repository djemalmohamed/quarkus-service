package com.service.infrastructure.signature.rfc;

import com.service.infrastructure.signature.shared.error.SignatureInfrastructureException;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Converts ECDSA P-256 signatures between Java DER encoding and RFC 9421 wire encoding.
 */
@ApplicationScoped
public class EcdsaP256SignatureCodec {

    private static final int COORDINATE_SIZE = 32;
    private static final int WIRE_SIGNATURE_SIZE = COORDINATE_SIZE * 2;

    public byte[] toWireSignature(byte[] derSignature) {
        try {
            return parseDerSignature(derSignature).toWireSignature();
        } catch (Exception error) {
            throw new SignatureInfrastructureException("Unable to encode ECDSA signature for HTTP Signature", error);
        }
    }

    public byte[] toDerSignature(byte[] wireSignature) {
        if (wireSignature == null || wireSignature.length != WIRE_SIGNATURE_SIZE) {
            throw new SignatureInfrastructureException("ECDSA P-256 HTTP Signature value must be 64 bytes");
        }

        SignatureParts parts = SignatureParts.fromWireSignature(wireSignature);
        byte[] firstInteger = encodeDerInteger(parts.r());
        byte[] secondInteger = encodeDerInteger(parts.s());
        byte[] sequence = writeBytes(firstInteger, secondInteger);
        return writeBytes(new byte[]{0x30}, derLength(sequence.length), sequence);
    }

    private SignatureParts parseDerSignature(byte[] derSignature) {
        DerReader reader = new DerReader(derSignature);
        byte[] sequence = reader.readSequence();
        if (reader.hasRemaining()) {
            throw new IllegalArgumentException("Unexpected trailing DER data");
        }

        DerReader sequenceReader = new DerReader(sequence);
        byte[] r = sequenceReader.readInteger();
        byte[] s = sequenceReader.readInteger();
        if (sequenceReader.hasRemaining()) {
            throw new IllegalArgumentException("Unexpected trailing ECDSA sequence data");
        }

        return new SignatureParts(normalizeCoordinate(r), normalizeCoordinate(s));
    }

    private byte[] normalizeCoordinate(byte[] derInteger) {
        byte[] unsigned = stripLeadingZeroes(derInteger);
        if (unsigned.length > COORDINATE_SIZE) {
            throw new IllegalArgumentException("ECDSA coordinate is larger than P-256");
        }
        return leftPad(unsigned, COORDINATE_SIZE);
    }

    private byte[] encodeDerInteger(byte[] coordinate) {
        byte[] unsigned = stripLeadingZeroes(coordinate);
        if (unsigned.length == 0) {
            unsigned = new byte[]{0};
        }
        if ((unsigned[0] & 0x80) != 0) {
            unsigned = writeBytes(new byte[]{0}, unsigned);
        }
        return writeBytes(new byte[]{0x02}, derLength(unsigned.length), unsigned);
    }

    private byte[] stripLeadingZeroes(byte[] value) {
        int firstNonZero = 0;
        while (firstNonZero < value.length && value[firstNonZero] == 0) {
            firstNonZero++;
        }
        return Arrays.copyOfRange(value, firstNonZero, value.length);
    }

    private byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }
        if (length <= 0xFF) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
    }

    private byte[] leftPad(byte[] value, int size) {
        byte[] padded = new byte[size];
        if (value.length == 0) {
            return padded;
        }
        System.arraycopy(value, 0, padded, size - value.length, value.length);
        return padded;
    }

    private byte[] writeBytes(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.writeBytes(array);
        }
        return output.toByteArray();
    }

    private record SignatureParts(byte[] r, byte[] s) {

        private static SignatureParts fromWireSignature(byte[] wireSignature) {
            return new SignatureParts(
                    Arrays.copyOfRange(wireSignature, 0, COORDINATE_SIZE),
                    Arrays.copyOfRange(wireSignature, COORDINATE_SIZE, WIRE_SIGNATURE_SIZE)
            );
        }

        private byte[] toWireSignature() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(WIRE_SIGNATURE_SIZE);
            output.writeBytes(r);
            output.writeBytes(s);
            return output.toByteArray();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SignatureParts that)) {
                return false;
            }
            return Arrays.equals(r, that.r) && Arrays.equals(s, that.s);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(r) + Arrays.hashCode(s);
        }

        @Override
        public String toString() {
            return "SignatureParts{rLength=" + (r == null ? 0 : r.length)
                    + ", sLength=" + (s == null ? 0 : s.length)
                    + '}';
        }
    }

    private static final class DerReader {
        private final byte[] data;
        private int cursor;

        private DerReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        private byte[] readSequence() {
            return readTaggedValue(0x30);
        }

        private byte[] readInteger() {
            byte[] value = readTaggedValue(0x02);
            if (value.length == 0 || new BigInteger(value).signum() < 0) {
                throw new IllegalArgumentException("Invalid ECDSA integer");
            }
            return value;
        }

        private byte[] readTaggedValue(int expectedTag) {
            if (cursor >= data.length || (data[cursor++] & 0xFF) != expectedTag) {
                throw new IllegalArgumentException("Unexpected DER tag");
            }
            int length = readLength();
            if (length < 0 || cursor + length > data.length) {
                throw new IllegalArgumentException("Invalid DER length");
            }
            byte[] value = Arrays.copyOfRange(data, cursor, cursor + length);
            cursor += length;
            return value;
        }

        private int readLength() {
            if (cursor >= data.length) {
                throw new IllegalArgumentException("Missing DER length");
            }
            int first = data[cursor++] & 0xFF;
            if (first < 0x80) {
                return first;
            }
            int count = first & 0x7F;
            if (count == 0 || count > 2 || cursor + count > data.length) {
                throw new IllegalArgumentException("Unsupported DER length");
            }
            int length = 0;
            for (int index = 0; index < count; index++) {
                length = (length << 8) | (data[cursor++] & 0xFF);
            }
            return length;
        }

        private boolean hasRemaining() {
            return cursor < data.length;
        }
    }
}
