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
TBD

### 📌 Common HTTP status returned:
* `200`: Successful operation;
* `400`: Bad request, e.g. missing required parameters or invalid values;

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

##### 🌍 External services
| ENV                                | DESCRIPTION                                    | DEFAULT |
|------------------------------------|------------------------------------------------|---------|
| PU_MIGRATION_BASE_URL              | PU Migration service URL                       |         |
| PU_MIGRATION_MAX_ATTEMPTS          | PU Migration API max attempts                  | 3       |
| PU_MIGRATION_WAIT_TIME_MILLIS      | PU Migration retry waiting time (milliseconds) | 500     |
| PU_MIGRATION_PRINT_BODY_WHEN_ERROR | To print body when an error occurs             | true    |

#### 💼 Business logic
| ENV | DESCRIPTION | DEFAULT |
|-----|-------------|---------|
|     |             |         |

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
