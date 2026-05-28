## Demo PKI Assets

This folder contains a local PKI used to simulate HTTP message signature certificates.

### Files

- `openssl.cnf`
  OpenSSL CA configuration used to issue certificates and generate the CRL.
- `demo-ca.key`
  Private key of the local demo certification authority.
- `demo-ca.crt`
  Self-signed root CA certificate trusted by the demo validation flow.
- `demo-ca.crl`
  Certificate revocation list published by the demo CA.
- `agc-signing.key`
  AGC private key used for outbound HTTP message signing.
- `agc-signing.crt`
  AGC certificate issued by the demo CA.
- `agc-signing-keystore.p12`
  PKCS12 keystore containing the AGC private key and certificate chain.
- `provider-signing.key`
  Provider private key used to simulate inbound signed requests or responses.
- `provider-signing.crt`
  Provider certificate issued by the demo CA.
- `provider-signing-keystore.p12`
  PKCS12 keystore containing the provider private key and certificate chain.
- `agc-signature-truststore.p12`
  PKCS12 truststore used by AGC to trust the demo CA and the provider certificate.

### Scope

These assets are committed only to support local development and signature-flow simulation.
They must not be reused in any real environment.
