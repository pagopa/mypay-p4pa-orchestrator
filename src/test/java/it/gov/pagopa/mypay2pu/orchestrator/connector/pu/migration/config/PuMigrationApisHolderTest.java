package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config;

import it.gov.pagopa.mypay2pu.orchestrator.connector.BaseApiHolderTest;
import it.gov.pagopa.mypay2pu.orchestrator.config.rest.HttpClientErrorJsonBodyHandler;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.pu.migration.dto.generated.CodeEnum;
import it.gov.pagopa.pu.migration.dto.generated.ErrorDTO;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.util.DefaultUriBuilderFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
class PuMigrationApisHolderTest extends BaseApiHolderTest {
    @Mock
    private RestTemplateBuilder restTemplateBuilderMock;
    @Mock
    private JsonMapper jsonMapperMock;

    private PuMigrationApisHolder puMigrationApisHolder;
    private PuMigrationApiClientConfig apiClientConfig;

    @BeforeEach
    void setUp() {
        Mockito.when(restTemplateBuilderMock.build()).thenReturn(restTemplateMock);

        apiClientConfig = PuMigrationApiClientConfig.builder()
                .baseUrl("http://example.com")
                .maxAttempts(3)
                .build();

        puMigrationApisHolder = new PuMigrationApisHolder(apiClientConfig, restTemplateBuilderMock, jsonMapperMock);
        Mockito.verify(restTemplateMock).setErrorHandler(Mockito.isA(HttpClientErrorJsonBodyHandler.class));
    }

    @AfterEach
    void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(
                restTemplateBuilderMock,
                restTemplateMock,
                jsonMapperMock
        );
    }

    @Test
    void testRetryConfiguration() {
        Mockito.when(restTemplateMock.getUriTemplateHandler()).thenReturn(new DefaultUriBuilderFactory());
        assertRetry(apiClientConfig,
                accessToken -> puMigrationApisHolder.getMigrationFileApi(accessToken)
                        .uploadMigrationFile("IPACODE", MigrationFileTypeEnum.ORGANIZATIONS, new ByteArrayResource(new byte[0])),
                new ParameterizedTypeReference<>() {}
        );
    }

    @Test
    void whenGetAuthnApiThenAuthenticationShouldBeSetInThreadSafeMode() throws InterruptedException {
        Mockito.when(restTemplateMock.getUriTemplateHandler()).thenReturn(new DefaultUriBuilderFactory());
        assertAuthenticationShouldBeSetInThreadSafeMode(
                accessToken -> puMigrationApisHolder.getMigrationFileApi(accessToken)
                  .uploadMigrationFile("IPACODE", MigrationFileTypeEnum.ORGANIZATIONS, new ByteArrayResource(new byte[0])),
                new ParameterizedTypeReference<>() {},
                puMigrationApisHolder::unload);
    }

    @Test
    void whenMigrationReturnsBadRequestThenErrorBodyIsMapped() throws Exception {
        String errorBody = "{\"code\":\"INVALID_FILE\",\"message\":\"Invalid file\",\"traceId\":\"trace-id\"}";
        ErrorDTO errorDTO = new ErrorDTO(CodeEnum.INVALID_FILE, "Invalid file", "trace-id");
        Mockito.when(jsonMapperMock.readValue(errorBody, ErrorDTO.class)).thenReturn(errorDTO);
        ResponseErrorHandler errorHandler = Mockito.mockingDetails(restTemplateMock)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals("setErrorHandler"))
                .map(invocation -> (ResponseErrorHandler) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        URI uri = URI.create("http://example.com");

        try (MockClientHttpResponse response = new MockClientHttpResponse(
                errorBody.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST
        )) {
            InvalidValueException exception = Assertions.assertThrows(
                    InvalidValueException.class,
                    () -> errorHandler.handleError(uri, HttpMethod.POST, response)
            );

            Assertions.assertEquals("INVALID_FILE", exception.getCode());
            Assertions.assertEquals("Invalid file", exception.getMessage());
        }

        Mockito.verify(jsonMapperMock).readValue(errorBody, ErrorDTO.class);
    }
}
