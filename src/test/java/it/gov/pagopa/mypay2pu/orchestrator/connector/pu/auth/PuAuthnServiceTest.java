package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.service.PuAuthAccessTokenRetriever;
import it.gov.pagopa.pu.auth.dto.generated.AccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PuAuthnServiceTest {

    @Mock
    private PuAuthAccessTokenRetriever accessTokenRetrieverMock;

    private PuAuthnService puAuthnService;

    @BeforeEach
    void init(){
        puAuthnService = new PuAuthnServiceImpl(accessTokenRetrieverMock);
    }

    @AfterEach
    void verifyNoMoreInteractions(){
        Mockito.verifyNoMoreInteractions(
                accessTokenRetrieverMock
        );
    }

    @Test
    void whenGetAccessTokenThenInvokeAccessTokenRetriever(){
        // Given
        String expectedResult = "TOKEN";
        Mockito.when(accessTokenRetrieverMock.getAccessToken())
                .thenReturn(AccessToken.builder().accessToken(expectedResult).tokenType("TOKENTYPE").expiresIn(0).build());

        // When
        String result = puAuthnService.getAccessToken();

        // Then
        Assertions.assertSame(expectedResult, result);
    }

}
