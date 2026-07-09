package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config.PuAuthApisHolder;
import it.gov.pagopa.pu.auth.controller.generated.AuthnApi;
import it.gov.pagopa.pu.auth.dto.generated.AccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuAuthnClientTest {
    @Mock
    private PuAuthApisHolder puAuthApisHolderMock;
    @Mock
    private AuthnApi authnApiMock;

    private PuAuthnClient puAuthnClient;

    @BeforeEach
    void setUp() {
        puAuthnClient = new PuAuthnClient(puAuthApisHolderMock);
    }

    @AfterEach
    void verifyNoMoreInteractions(){
        Mockito.verifyNoMoreInteractions(
          puAuthApisHolderMock
        );
    }

    @Test
    void whenGetOperatorInfoThenInvokeWithAccessToken(){
        // Given
        AccessToken expectedResult = new AccessToken();

        String clientId = "clientId";
        String grantType = "grantType";
        String scope = "scope";
        String subjectToken = "subjectToken";
        String subjectIssuer = "subjectIssuer";
        String subjectTokenType = "subjectTokenType";
        String clientSecret = "clientSecret";

        when(puAuthApisHolderMock.getAuthnApi(null))
                .thenReturn(authnApiMock);
        when(authnApiMock.postToken(clientId, grantType, scope, subjectToken, subjectIssuer, subjectTokenType, clientSecret, null))
                .thenReturn(expectedResult);

        // When
        AccessToken result = puAuthnClient.postToken(clientId, grantType, scope, subjectToken, subjectIssuer, subjectTokenType, clientSecret);

        // Then
        Assertions.assertSame(expectedResult, result);
    }
}
