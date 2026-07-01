package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.service.PuAuthAccessTokenRetriever;
import org.springframework.stereotype.Service;

@Service
public class PuAuthnServiceImpl implements PuAuthnService {

    private final PuAuthAccessTokenRetriever accessTokenRetriever;

    public PuAuthnServiceImpl(PuAuthAccessTokenRetriever accessTokenRetriever) {
        this.accessTokenRetriever = accessTokenRetriever;
    }

    @Override
    public String getAccessToken() {
        return accessTokenRetriever.getAccessToken()
                .getAccessToken();
    }

}
