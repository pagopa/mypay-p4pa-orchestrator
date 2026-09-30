package it.gov.pagopa.mypay2pu.orchestrator;

import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, addFilters = false)
@TestPropertySource(properties = {
  "logging.level.org.springdoc.core.utils.SpringDocAnnotationsUtils=OFF",
  "springdoc.api-docs.enabled=true",
  "springdoc.swagger-ui.enabled=false",
  "springdoc.writer-with-default-pretty-printer=true"
})
class OpenApiGeneratorTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrchestrateMigrationService orchestrateMigrationService;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(orchestrateMigrationService);
  }

  @Test
  void generatedOpenApiMatchesVersionedSpecification() throws Exception {
    MvcResult result = mockMvc.perform(
        get("/v3/api-docs")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
      ).andExpect(status().isOk())
      .andReturn();

    String openApiResult = result.getResponse().getContentAsString()
      .replace("\r", "");

    Assertions.assertTrue(openApiResult.startsWith("{\n  \"openapi\" : \"3."));

    Path openApiGeneratedPath = Path.of("openapi/generated.openapi.json");
    if (Boolean.getBoolean("openapi.update")) {
      Files.writeString(
        openApiGeneratedPath,
        openApiResult,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      );
      return;
    }

    Assertions.assertTrue(
      Files.exists(openApiGeneratedPath),
      "Missing versioned OpenAPI snapshot; run generateOpenApiSpec to create it"
    );
    String storedOpenApi = Files.readString(openApiGeneratedPath, StandardCharsets.UTF_8);
    JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(storedOpenApi, openApiResult);
  }
}
