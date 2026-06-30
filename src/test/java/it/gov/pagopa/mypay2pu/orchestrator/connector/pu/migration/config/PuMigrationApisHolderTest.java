package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config;

import it.gov.pagopa.mypay2pu.orchestrator.connector.BaseApiHolderTest;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.util.DefaultUriBuilderFactory;

@ExtendWith(MockitoExtension.class)
class PuMigrationApisHolderTest extends BaseApiHolderTest {
    @Mock
    private RestTemplateBuilder restTemplateBuilderMock;

    private PuMigrationApisHolder puMigrationApisHolder;
    private PuMigrationApiClientConfig apiClientConfig;

    @BeforeEach
    void setUp() {
        Mockito.when(restTemplateBuilderMock.build()).thenReturn(restTemplateMock);
        Mockito.when(restTemplateMock.getUriTemplateHandler()).thenReturn(new DefaultUriBuilderFactory());

        apiClientConfig = PuMigrationApiClientConfig.builder()
                .baseUrl("http://example.com")
                .maxAttempts(3)
                .build();

        puMigrationApisHolder = new PuMigrationApisHolder(apiClientConfig, restTemplateBuilderMock);
    }

    @AfterEach
    void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(
                restTemplateBuilderMock,
                restTemplateMock
        );
    }

    @Test
    void testRetryConfiguration() {
        assertRetry(apiClientConfig,
                accessToken -> puMigrationApisHolder.getMigrationFileApi(accessToken)
                        .uploadMigrationFile("IPACODE", MigrationFileTypeEnum.ORGANIZATIONS, new ByteArrayResource(new byte[0])),
                new ParameterizedTypeReference<>() {}
        );
    }

    @Test
    void whenGetAuthnApiThenAuthenticationShouldBeSetInThreadSafeMode() throws InterruptedException {
        assertAuthenticationShouldBeSetInThreadSafeMode(
                accessToken -> puMigrationApisHolder.getMigrationFileApi(accessToken)
                  .uploadMigrationFile("IPACODE", MigrationFileTypeEnum.ORGANIZATIONS, new ByteArrayResource(new byte[0])),
                new ParameterizedTypeReference<>() {},
                puMigrationApisHolder::unload);
    }
}
