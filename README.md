# Distributed Commerce API

Local infrastructure for an event-driven polyglot microservices architecture using PostgreSQL and Apache Kafka.

## Included Services

- PostgreSQL with two application databases:
  - `order_db`
  - `inventory_db`
- Apache Kafka running in KRaft mode
- Kafka UI for topic and consumer group visualization

## Prerequisites

- Docker
- Docker Compose

## Start the Stack

```bash
docker compose up -d
```

## Stop the Stack

```bash
docker compose down
```

To remove volumes as well:

```bash
docker compose down -v
```

## Service Endpoints

- PostgreSQL: `localhost:5432`
- Kafka internal listener for containers: `kafka:9092`
- Kafka external listener for local applications: `localhost:9094`
- Kafka UI: `http://localhost:8080`

## PostgreSQL Access

Default credentials:

- Username: `postgres`
- Password: `postgres`
- Default admin database: `postgres`

Logical databases created on first startup:

- `order_db`
- `inventory_db`

Example JDBC URLs:

```text
jdbc:postgresql://localhost:5432/order_db
jdbc:postgresql://localhost:5432/inventory_db
```

## Kafka Access

Use `localhost:9094` from Java or Kotlin applications running on your machine.

Example Spring configuration:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9094
```

Use `kafka:9092` only from other containers running in the same Compose network.

## Configuration Notes

- PostgreSQL data is persisted in the `postgres_data` volume.
- Kafka data is persisted in the `kafka_data` volume.
- Kafka runs without Zookeeper using KRaft mode.
- Topic auto-creation is disabled to better match production expectations.
- Replication settings are intentionally set to `1` because this is a single-node local environment.
- Kafka UI is preconfigured to connect to the local Kafka broker.

## Files

- [`docker-compose.yml`](/Users/paidoboris/CorridaDeMotos/distributed-commerce-api/docker-compose.yml)
- [`postgres/init/01-create-databases.sh`](/Users/paidoboris/CorridaDeMotos/distributed-commerce-api/postgres/init/01-create-databases.sh)
