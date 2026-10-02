# mypay-p4pa-orchestrator

This application belong to the **Migration Toolkit** project, which is intended to migrate data from **MyPay4** to  **Piattaforma Unitaria** product.

See [p4pa-doc](https://github.com/pagopa/p4pa-doc) for further documentation on Piattaforma Unitaria.

## 🧱 Role

* Coordinate export and import operations of MyPay4 data, orchestrating the execution of the following applications:;
  * [mypay-p4pa-extractor](https://github.com/pagopa/mypay-p4pa-extractor)
  * [p4pa-migration](https://github.com/pagopa/p4pa-migration)

## 🌐 APIs
See [OpenAPI](openapi/generated.openapi.json), exposed through the following path:
* `/swagger-ui/index.html`

### 📌 Relevant APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/orchestrate/migrate` | Start an `EXPORT`, `TRANSFER` or `ALL` migration |

### 📌 Common HTTP status returned:
* `202`: Migration orchestration accepted
* `400`: Invalid request
* `404`: Requested resource not found
* `409`: Conflict with an existing operation

## 🔎 Monitoring
See available actuator endpoints through the following path:
* `/actuator`

### 📌 Relevant endpoints
* Health (provide an accessToken to see details): `/actuator/health`
  * Liveness: `/actuator/health/liveness`
  * Readiness: `/actuator/health/readiness`
* Metrics: `/actuator/metrics`
  * Prometheus: `/actuator/prometheus`

Further endpoints are exposed through the JMX console.

## ✏️ Logging
See [log configured pattern](/src/main/resources/logback-spring.xml).

## 🔗 Dependencies

### 🗄️ Resources
* PostgreSQL

### 🧩 Microservices
* [mypay-p4pa-extractor](https://github.com/pagopa/mypay-p4pa-extractor)
  * To extract data from MyPay4 sources producing the zip files to import on Piattaforma Unitaria;

### 🌍 External
* [p4pa-migration](https://github.com/pagopa/p4pa-migration):
  * To import data on Piattaforma Unitaria through the exposed APIs;

## 🔧 Configuration

See [application.yml](src/main/resources/application.yml) for each configurable property.

### 📌 Relevant configurations

#### 🌐 Application Server
| ENV         | DESCRIPTION                       | DEFAULT |
|-------------|-----------------------------------|---------|
| SERVER_PORT | Application server listening port | 8080    |

#### ✏️ Logging
| ENV                                   | DESCRIPTION                                                                                                                                            | DEFAULT |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| LOG_LEVEL_ROOT                        | Base level                                                                                                                                             | INFO    |
| LOG_LEVEL_PAGOPA                      | Base level of custom classes                                                                                                                           | INFO    |
| LOG_LEVEL_SPRING                      | Level applied to Spring framework                                                                                                                      | INFO    |
| LOG_LEVEL_SPRING_BOOT_AVAILABILITY    | To print availability events                                                                                                                           | DEBUG   |
| LOGGING_LEVEL_API_REQUEST_EXCEPTION   | Level applied to APIs exception                                                                                                                        | INFO    |
| LOG_LEVEL_PERFORMANCE_LOG             | Level applied to [PerformanceLog](https://raw.githubusercontent.com/pagopa/p4pa-doc/refs/heads/main/reference/technical-docs/Logging.pdf)              | INFO    |
| LOG_LEVEL_PERFORMANCE_LOG_API_REQUEST | Level applied to [API Performance Log](https://raw.githubusercontent.com/pagopa/p4pa-doc/refs/heads/main/reference/technical-docs/Logging.pdf)         | INFO    |
| LOG_LEVEL_PERFORMANCE_LOG_REST_INVOKE | Level applied to [REST invoke Performance Log](https://raw.githubusercontent.com/pagopa/p4pa-doc/refs/heads/main/reference/technical-docs/Logging.pdf) | INFO    |


#### 🗄️ Orchestrator database
| ENV                      | DESCRIPTION                            | DEFAULT |
|--------------------------|----------------------------------------|---------|
| ORCHESTRATOR_DB_HOST     | PostgreSQL host (required)             |         |
| ORCHESTRATOR_DB_PORT     | PostgreSQL port                        | 5432    |
| ORCHESTRATOR_DB_NAME     | Database name (required)               |         |
| ORCHESTRATOR_DB_USER     | Database username (required)           |         |
| ORCHESTRATOR_DB_PASSWORD | Database password (required)           |         |

#### 🔁 Integrations

##### 🔗 REST
| ENV                                               | DESCRIPTION                               | DEFAULT |
|---------------------------------------------------|-------------------------------------------|---------|
| DEFAULT_REST_CONNECTION_POOL_SIZE                 | Default connection pool size              | 10      |
| DEFAULT_REST_CONNECTION_POOL_SIZE_PER_ROUTE       | Default connection pool size per route    | 5       |
| DEFAULT_REST_CONNECTION_POOL_TIME_TO_LIVE_MINUTES | Default connection pool TTL (minutes)     | 10      |
| DEFAULT_REST_TIMEOUT_CONNECT_MILLIS               | Default connection timeout (milliseconds) | 120000  |
| DEFAULT_REST_TIMEOUT_READ_MILLIS                  | Default read timeout (milliseconds)       | 120000  |

##### 🧩 Microservices
| ENV                             | DESCRIPTION                                 | DEFAULT |
|---------------------------------|---------------------------------------------|---------|
| EXTRACTOR_BASE_URL              | Extractor microservice URL                  |         |
| EXTRACTOR_MAX_ATTEMPTS          | Extractor API max attempts                  | 3       |
| EXTRACTOR_WAIT_TIME_MILLIS      | Extractor retry waiting time (milliseconds) | 500     |
| EXTRACTOR_PRINT_BODY_WHEN_ERROR | To print body when an error occurs          | true    |
| EXTRACTOR_API_TIMEOUT_SECONDS   | Phase 2 extraction wait limit (seconds)     | 3600    |


##### 🌍 External services
| ENV                                | DESCRIPTION                                    | DEFAULT                     |
|------------------------------------|------------------------------------------------|-----------------------------|
| PU_BASE_URL                        | PU base URL                                    |                             |
| PU_AUTH_BASE_URL                   | PU Auth service URL                            | ${PU_BASE_URL}/pu/auth      |
| PU_AUTH_MAX_ATTEMPTS               | PU Auth API max attempts                       | 3                           |
| PU_AUTH_WAIT_TIME_MILLIS           | PU Auth retry waiting time (milliseconds)      | 500                         |
| PU_AUTH_PRINT_BODY_WHEN_ERROR      | To print body when an error occurs             | true                        |
| PU_MIGRATION_BASE_URL              | PU Migration service URL                       | ${PU_BASE_URL}/pu/migration |
| PU_MIGRATION_MAX_ATTEMPTS          | PU Migration API max attempts                  | 3                           |
| PU_MIGRATION_WAIT_TIME_MILLIS      | PU Migration retry waiting time (milliseconds) | 500                         |
| PU_MIGRATION_PRINT_BODY_WHEN_ERROR | To print body when an error occurs             | true                        |

| ENV                               | DESCRIPTION                                      | DEFAULT |
|-----------------------------------|--------------------------------------------------|---------|
| UPLOAD_P4PA_TIMEOUT_SECONDS       | Phase 2 upload timeout (seconds)                 | 600     |
| POLLING_WORKFLOW_BASE_URL         | PU workflow status polling base URL (required)   |         |
| POLLING_WORKFLOW_INTERVAL_SECONDS | Workflow polling interval (seconds)              | 30      |
| POLLING_WORKFLOW_MAX_ATTEMPTS     | Maximum workflow polling attempts (12h at 30s)  | 1440    |

#### 💼 Business logic
| ENV                           | DESCRIPTION                                                | DEFAULT              |
|-------------------------------|------------------------------------------------------------|----------------------|
| DAG_CONFIG_PATH               | Path to the Phase 2 DAG YAML file                          | /app/config/dag.yaml |
| ASYNC_CORE_POOL_SIZE          | Extraction coordinator core thread pool size              | 4                    |
| ASYNC_MAX_POOL_SIZE           | Extraction coordinator maximum thread pool size           | 8                    |
| ASYNC_QUEUE_CAPACITY          | Extraction coordinator task queue capacity                | 100                  |
| STORAGE_PATH_EXTRACTOR_OUTPUT | Local path to ZIP files produced by Extractor (required)  |                      |
| MULTIPART_MAX_FILE_SIZE       | Maximum incoming multipart file size in bytes (50 MiB)    | 52428800             |


#### 🔑 keys
| ENV                   | DESCRIPTION                                                                         | DEFAULT |
|-----------------------|-------------------------------------------------------------------------------------|---------|
| PU_AUTH_CLIENT_ID     | client_id used on M2M authentication to get a technical access token towards PU     |         |
| PU_AUTH_CLIENT_SECRET | client_secret used on M2M authentication to get a technical access token towards PU |         |

## 🛠️ Getting Started

### 📝 Prerequisites

Ensure the following tools are installed on your machine:

1. **Java 21+**
2. **Gradle** (or use the Gradle wrapper included in the repository)
3. **Docker** (to build and run on an isolated environment, optional)

### 🔐 Write Locks

```sh
./gradlew dependencies --write-locks
```

### ⚙️ Build

```sh
./gradlew clean build
```

### 🧪 Test

#### 📌 JUnit
```sh
./gradlew test
```

### 🚀 Run local

```sh
./gradlew bootRun
```

### 🐳 Build & run through Docker
```sh
docker build -t <APP_NAME> .
docker run --env-file <ENV_FILE> <APP_NAME>
```

### ⚖️ Generate dependencies licenses
```sh
./gradlew generateLicenseReport
```
