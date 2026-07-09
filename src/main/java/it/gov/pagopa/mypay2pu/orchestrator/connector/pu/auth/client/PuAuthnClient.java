package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.config.PuAuthApisHolder;
import it.gov.pagopa.pu.auth.dto.generated.AccessToken;
import org.springframework.stereotype.Service;

@Service
public class PuAuthnClient {

    private final PuAuthApisHolder puAuthApisHolder;

    public PuAuthnClient(PuAuthApisHolder puAuthApisHolder) {
        this.puAuthApisHolder = puAuthApisHolder;
    }

    public AccessToken postToken(String clientId, String grantType, String scope, String subjectToken, String subjectIssuer, String subjectTokenType, String clientSecret) {
        return puAuthApisHolder.getAuthnApi(null)
                .postToken(clientId, grantType, scope, subjectToken, subjectIssuer, subjectTokenType, clientSecret, null);
    }

}
