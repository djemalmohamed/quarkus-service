# quarkus-service

Service Quarkus autonome qui regroupe dans un même repo :

- la validation et la génération de signatures HTTP Message Signatures
- l'exposition d'API REST métier
- le HTTP outbound du gateway
- l'intégration legal archiving

## Architecture

Le projet suit une structure hexagonale unique sous la racine Java `com.service` :

- `application/payment`
- `application/legalarchiving`
- `application/port/in`
- `application/port/out`
- `domain/payment`
- `infrastructure/adapters/rest/payment`
- `infrastructure/adapters/http`
- `infrastructure/adapters/legalarchiving`
- `infrastructure/signature`
- `infrastructure/configuration`

Le contrat protobuf simulé utilisé par l'adapter legal archiving est exposé séparément sous
`com.service.infrastructure.adapters.legalarchiving.contract`, pour rester proche d'une vraie lib générée.

## Intégration legal archiving

- inbound request : déclenché depuis le filtre de validation signature, qui prépare déjà `SignatureData`
- inbound response : archivé par le filtre legal archiving côté réponse
- outbound request / response : archivé depuis l'adapter HTTP sortant

## Exécution locale

Un `docker-compose.yml` est fourni pour démarrer Kafka et Kafka UI :

```bash
docker compose up -d
```

## Validation locale

Commandes déjà vérifiées :

```bash
mvn -q -DskipTests compile
mvn -q test
```
