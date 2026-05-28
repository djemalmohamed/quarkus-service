# quarkus-service

Service Quarkus autonome qui regroupe dans un même repo :

- la validation et la génération de signatures HTTP Message Signatures
- l'exposition d'API REST métier
- le HTTP outbound du gateway
- l'intégration legal archiving

## Base technique

Ce projet a été reconstruit sans modifier les repos source existants :

- base signature / API / HTTP out reprise depuis le repo local `httpsignature`
- base legal archiving reprise depuis le scaffold `quarkus-kafka`

## Intégration legal archiving

- inbound request : déclenché depuis le filtre de validation signature, qui prépare déjà `SignatureData`
- inbound response : archivé par le filtre legal archiving côté réponse
- outbound request / response : archivé depuis l'adapter HTTP sortant

## Validation locale

Commandes déjà vérifiées :

```bash
mvn -q -DskipTests compile
mvn -q test
```
