# Global News Engine

Global News Engine is a Spring Boot service that turns [GDELT 2.0](https://www.gdeltproject.org/) data into a queryable article model. It periodically discovers and imports GDELT event, mention, and GKG files, stages their rows in PostgreSQL, normalizes and deduplicates article URLs, and exposes the resulting articles and signals through a REST API.

The current implementation covers the first part of the intended analysis pipeline:

```text
GDELT files -> raw data -> staging data -> articles -> REST API
                                                |
                                                +-> event, mention, and GKG signals
```

Story clustering, topics, themes, embeddings, and LLM-generated summaries are planned but are not part of the application yet.

## Technology

- Java 21
- Spring Boot 4
- Spring Modulith
- PostgreSQL
- Flyway
- Maven Wrapper
- Docker Compose for the local database

## Prerequisites

- JDK 21
- Docker with Docker Compose

You do not need to install Maven separately; the repository includes the Maven Wrapper.

## Run locally

Start PostgreSQL:

```powershell
docker compose up -d
```

Run the application on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, use:

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. Flyway applies the database migrations at startup. Spring Boot uses the PostgreSQL service defined in `compose.yaml` (`gne`/`gne` on `localhost:5432`).

GDELT ingestion is enabled by default. After startup, the service begins downloading current GDELT data and continues polling in the background. Internet access is therefore required for live ingestion.

Stop the database with:

```powershell
docker compose down
```

Add `-v` only when you intentionally want to remove the local database volume as well.

## REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/articles?offset=0&limit=20` | List the latest articles |
| `GET` | `/articles/{id}` | Get an article and its GDELT signals |
| `GET` | `/articles/domains/top?limit=10` | List the most frequent article domains |
| `GET` | `/articles/themes/top?limit=10` | List the most frequent GDELT themes |
| `GET` | `/articles/extraction/health` | Show article extraction health and errors |

Example:

```powershell
Invoke-RestMethod http://localhost:8080/articles?limit=5
```

A ready-to-use Postman collection and local environment are available in [`docs/postman`](docs/postman).

## Tests

Run the unit and integration test suite with PostgreSQL running:

```powershell
docker compose up -d
.\mvnw.cmd verify
```

On macOS or Linux, replace `.\mvnw.cmd` with `./mvnw`.

The PostgreSQL integration test connects to `localhost:5432`, creates a temporary schema, and removes it when the test completes. Most other tests use the test configuration in `src/test/resources` and H2.

## Configuration

Runtime defaults are defined in [`src/main/resources/application.properties`](src/main/resources/application.properties). The main job settings are:

| Prefix | Purpose |
|---|---|
| `gdelt.ingestion.*` | GDELT discovery, download, and polling |
| `gdelt.staging.*` | Transformation from raw rows into staging tables |
| `articles.*` | Article extraction, normalization, and batching |

Each job can be disabled with its corresponding `*.enabled=false` property. Spring Boot properties can be overridden with command-line arguments, environment variables, or an external configuration file. For example:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--gdelt.ingestion.enabled=false --gdelt.staging.enabled=false --articles.enabled=false"
```

## Project layout

```text
src/main/java/.../gdelt       GDELT discovery, raw import, parsing, and staging
src/main/java/.../articles    URL normalization, extraction, queries, and REST API
src/main/resources/db         Flyway database migrations
docs/postman                  Postman collection and local environment
docs/tickets                  Local implementation tickets
docs/analysis                 Supporting technical analysis
```

## Further documentation

- [`docs/global_event_analysis_platform_architecture.md`](docs/global_event_analysis_platform_architecture.md) — target architecture and product direction
- [`docs/gdelt_events_mentions_gkg_data_model_overview.md`](docs/gdelt_events_mentions_gkg_data_model_overview.md) — GDELT data model overview
- [`docs/articles.md`](docs/articles.md) — article model, normalization, and extraction behavior
- [`docs/operations.md`](docs/operations.md) — operational queries for import status and failures
- [`docs/tickets/README.md`](docs/tickets/README.md) — local ticket workflow
