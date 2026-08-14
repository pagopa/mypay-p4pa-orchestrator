import java.util.*
import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.XmlReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  id("org.springframework.boot") version "4.1.0"
  id("io.spring.dependency-management") version "1.1.7"
  jacoco
  id("org.sonarqube") version "7.3.1.8318"
  id("com.github.ben-manes.versions") version "0.54.0"
  id("org.openapi.generator") version "7.23.0"
  id("org.ajoberstar.grgit") version "5.3.2"
  id("com.gorylenko.gradle-git-properties") version "4.0.1"
  id("com.github.jk1.dependency-license-report") version "3.1.4"
}

group = "it.gov.pagopa.mypay2pu"
version = "0.0.1"
description = "mypay-p4pa-orchestrator"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
  compileClasspath {
    resolutionStrategy.activateDependencyLocking()
  }
}

licenseReport {
  renderers =
    arrayOf(XmlReportRenderer("third-party-libs.xml", "Back-End Libraries"))
  outputDir = "$projectDir/dependency-licenses"
  filters = arrayOf(SpdxLicenseBundleNormalizer())
}
tasks.classes {
  finalizedBy(tasks.generateLicenseReport)
}

repositories {
  mavenCentral()
}

val springDocOpenApiVersion = "3.0.3"
val openApiToolsVersion = "0.2.10"
val micrometerVersion = "1.7.0"
val httpClientVersion = "5.6.1"
val httpCoreVersion = "5.4.2"
val kafkaAppender = "0.2.0-RC2"
val lz4JavaVersion = "1.11.1"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  implementation("org.springframework.data:spring-data-commons")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springDocOpenApiVersion")
  implementation("io.micrometer:micrometer-tracing-bridge-otel:$micrometerVersion")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("org.openapitools:jackson-databind-nullable:$openApiToolsVersion")
  implementation("org.apache.httpcomponents.client5:httpclient5:$httpClientVersion")
  implementation("org.apache.httpcomponents.core5:httpcore5:$httpCoreVersion")
  implementation("com.github.danielwegener:logback-kafka-appender:$kafkaAppender") {
    exclude(group = "org.lz4", module = "lz4-java")
  }
  implementation("at.yawk.lz4:lz4-java:$lz4JavaVersion")

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")

  //	Testing
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.projectlombok:lombok")
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
  mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}
tasks {
  jar {
      from("${rootProject.projectDir}") {
          include("LICENSE.md")
          into("META-INF")
      }
  }
  test {
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    testLogging.events = setOf(TestLogEvent.FAILED)
    testLogging.exceptionFormat = TestExceptionFormat.FULL
  }
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
  }
}

val projectInfo = mapOf(
  "artifactId" to project.name,
  "version" to project.version
)

tasks {
  val processResources by getting(ProcessResources::class) {
    filesMatching("**/application.yml") {
      expand(projectInfo)
    }
  }
  processResources.dependsOn("dependenciesBuild")
}

tasks.compileJava {
  dependsOn("dependenciesBuild")
}

tasks.register("dependenciesBuild") {
  group = "AutomaticallyGeneratedCode"
  description = "grouping all together automatically generate code tasks"

  dependsOn(
    "openApiGenerate",
    "openApiGenerate_P4PAAUTH",
    "openApiGenerate_P4PAMIGRATION",
    "openApiGenerate_MYPAYP4PAEXTRACTOR"
  )
}

configure<SourceSetContainer> {
  named("main") {
    java.srcDir("$projectDir/build/generated/src/main/java")
  }
}

springBoot {
  buildInfo()
  mainClass.value("it.gov.pagopa.mypay2pu.orchestrator.MyPayPuOrchestratorApplication")
}

openApiGenerate {
  generatorName.set("spring")
  inputSpec.set("$rootDir/openapi/mypay-p4pa-orchestrator.openapi.yaml")
  outputDir.set("$projectDir/build/generated")
  apiPackage.set("it.gov.pagopa.mypay2pu.orchestrator.controller.generated")
  modelPackage.set("it.gov.pagopa.mypay2pu.orchestrator.dto.generated")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8",
      "requestMappingMode" to "api_interface",
      "useSpringBoot4" to "true",
      "useJackson3" to "true",
      "interfaceOnly" to "true",
      "useTags" to "true",
      "useBeanValidation" to "true",
      "generateConstructorWithAllArgs" to "true",
      "generatedConstructorWithRequiredArgs" to "true",
      "enumPropertyNaming" to "original",
      "additionalModelTypeAnnotations" to "@lombok.experimental.SuperBuilder(toBuilder = true)"
    )
  )
}

var targetEnv = when (Objects.requireNonNullElse(
  System.getProperty("targetBranch"),
  grgit.branch.current().name
)) {
  "uat" -> "uat"
  "main" -> "main"
  else -> "develop"
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate_P4PAAUTH") {
  group = "openapi"
  description = "description"

  generatorName.set("java")
  remoteInputSpec.set("https://raw.githubusercontent.com/pagopa/p4pa-doc/refs/heads/main/openapi/main/public/p4pa-auth.openapi.yaml")
  outputDir.set("$projectDir/build/generated")
  invokerPackage.set("it.gov.pagopa.pu.auth.generated")
  apiPackage.set("it.gov.pagopa.pu.auth.controller.generated")
  modelPackage.set("it.gov.pagopa.pu.auth.dto.generated")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "false",
      "dateLibrary" to "java8",
      "serializableModel" to "true",
      "useSpringBoot4" to "true",
      "useJackson3" to "true",
      "openApiNullable" to "false",
      "useJakartaEe" to "true",
      "useOneOfInterfaces" to "true",
      "useBeanValidation" to "true",
      "serializationLibrary" to "jackson",
      "generateSupportingFiles" to "true",
      "generateConstructorWithAllArgs" to "true",
      "generatedConstructorWithRequiredArgs" to "true",
      "enumPropertyNaming" to "original",
      "additionalModelTypeAnnotations" to "@lombok.experimental.SuperBuilder(toBuilder = true)"
    )
  )
  library.set("resttemplate")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate_P4PAMIGRATION") {
  group = "openapi"
  description = "description"

  generatorName.set("java")
  remoteInputSpec.set("https://raw.githubusercontent.com/pagopa/p4pa-doc/refs/heads/main/openapi/main/public/p4pa-migration.openapi.json")
  outputDir.set("$projectDir/build/generated")
  invokerPackage.set("it.gov.pagopa.pu.migration.generated")
  apiPackage.set("it.gov.pagopa.pu.migration.controller.generated")
  modelPackage.set("it.gov.pagopa.pu.migration.dto.generated")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "false",
      "dateLibrary" to "java8",
      "serializableModel" to "true",
      "useSpringBoot4" to "true",
      "useJackson3" to "true",
      "openApiNullable" to "false",
      "useJakartaEe" to "true",
      "useOneOfInterfaces" to "true",
      "useBeanValidation" to "true",
      "serializationLibrary" to "jackson",
      "generateSupportingFiles" to "true",
      "useAbstractionForFiles" to "true",
      "generateConstructorWithAllArgs" to "true",
      "generatedConstructorWithRequiredArgs" to "true",
      "enumPropertyNaming" to "original",
      "additionalModelTypeAnnotations" to "@lombok.experimental.SuperBuilder(toBuilder = true)"
    )
  )
  library.set("resttemplate")
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate_MYPAYP4PAEXTRACTOR") {
  group = "openapi"
  description = "description"

  generatorName.set("java")
  remoteInputSpec.set("https://raw.githubusercontent.com/pagopa/mypay-p4pa-orchestrator/refs/heads/$targetEnv/openapi/mypay-p4pa-orchestrator.openapi.yaml")
  outputDir.set("$projectDir/build/generated")
  invokerPackage.set("it.gov.pagopa.mypay2pu.extractor.generated")
  apiPackage.set("it.gov.pagopa.mypay2pu.extractor.controller.generated")
  modelPackage.set("it.gov.pagopa.mypay2pu.extractor.dto.generated")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "false",
      "dateLibrary" to "java8",
      "serializableModel" to "true",
      "useSpringBoot4" to "true",
      "useJackson3" to "true",
      "openApiNullable" to "false",
      "useJakartaEe" to "true",
      "useOneOfInterfaces" to "true",
      "useBeanValidation" to "true",
      "serializationLibrary" to "jackson",
      "generateSupportingFiles" to "true",
      "generateConstructorWithAllArgs" to "true",
      "generatedConstructorWithRequiredArgs" to "true",
      "enumPropertyNaming" to "original",
      "additionalModelTypeAnnotations" to "@lombok.experimental.SuperBuilder(toBuilder = true)"
    )
  )
  library.set("resttemplate")
}
