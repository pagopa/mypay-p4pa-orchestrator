package it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.config;

import it.gov.pagopa.mypay2pu.orchestrator.connector.BaseApiHolderTest;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config.PuAuthApiClientConfig;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config.PuAuthApisHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.util.DefaultUriBuilderFactory;

@ExtendWith(MockitoExtension.class)
class ExtractorApisHolderTest extends BaseApiHolderTest {
    @Mock
    private RestTemplateBuilder restTemplateBuilderMock;

    private PuAuthApisHolder puAuthApisHolder;
    private PuAuthApiClientConfig apiClientConfig;

    @BeforeEach
    void setUp() {
        Mockito.when(restTemplateBuilderMock.build()).thenReturn(restTemplateMock);
        Mockito.when(restTemplateMock.getUriTemplateHandler()).thenReturn(new DefaultUriBuilderFactory());

        apiClientConfig = PuAuthApiClientConfig.builder()
                .baseUrl("http://example.com")
                .maxAttempts(3)
                .build();

        puAuthApisHolder = new PuAuthApisHolder(apiClientConfig, restTemplateBuilderMock);
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
                accessToken -> puAuthApisHolder.getAuthnApi(accessToken)
                        .getUserInfo(),
                new ParameterizedTypeReference<>() {}
        );
    }

    @Test
    void whenGetAuthnApiThenAuthenticationShouldBeSetInThreadSafeMode() throws InterruptedException {
        assertAuthenticationShouldBeSetInThreadSafeMode(
                accessToken -> puAuthApisHolder.getAuthnApi(accessToken)
                        .getUserInfo(),
                new ParameterizedTypeReference<>() {},
                puAuthApisHolder::unload);
    }
}
