package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.service.AuthAccessTokenRetriever;
import org.springframework.stereotype.Service;

@Service
public class PuAuthnServiceImpl implements PuAuthnService {

    private final AuthAccessTokenRetriever accessTokenRetriever;

    public PuAuthnServiceImpl(AuthAccessTokenRetriever accessTokenRetriever) {
        this.accessTokenRetriever = accessTokenRetriever;
    }

    @Override
    public String getAccessToken() {
        return accessTokenRetriever.getAccessToken(null)
                .getAccessToken();
    }

    @Override
    public String getAccessToken(String orgIpaCode) {
        return accessTokenRetriever.getAccessToken(orgIpaCode)
                .getAccessToken();
    }
}
