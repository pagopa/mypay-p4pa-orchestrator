package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth;

public interface PuAuthnService {
    String getAccessToken();
    String getAccessToken(String orgIpaCode);
}
