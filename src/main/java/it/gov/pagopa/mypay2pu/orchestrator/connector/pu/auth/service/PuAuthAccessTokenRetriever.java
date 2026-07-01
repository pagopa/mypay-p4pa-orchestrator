package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.service;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.client.PuAuthnClient;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import it.gov.pagopa.pu.auth.dto.generated.AccessToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PuAuthAccessTokenRetriever {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String SCOPE = "openid";

  private final String clientId;
  private final String clientSecret;

  private final PuAuthnClient puAuthnClient;

    private final Map<String, Pair<LocalDateTime, AccessToken>> clientId2accessTokensMap = new ConcurrentHashMap<>();

    public PuAuthAccessTokenRetriever(
            @Value("${rest.pu.auth.post-token.client-id}")
            String clientId,
            @Value("${rest.pu.auth.post-token.client-secret}")
            String clientSecret,

            PuAuthnClient puAuthnClient) {
      this.clientId = clientId;
      this.clientSecret = clientSecret;
      this.puAuthnClient = puAuthnClient;
    }

    public AccessToken getAccessToken() {
        return clientId2accessTokensMap.compute(clientId, (k, v) -> {
            if (v == null || LocalDateTime.now(Constants.ZONEID).isAfter(v.getLeft())) {
                log.info("M2M AccessToken with clientId[{}] expired, refreshing", clientId);
                LocalDateTime tokenRequestDateTime = LocalDateTime.now(Constants.ZONEID);
                AccessToken accessToken = puAuthnClient.postToken(clientId, GRANT_TYPE, SCOPE, null, null, null, clientSecret);
                LocalDateTime expiration = tokenRequestDateTime.plusSeconds(accessToken.getExpiresIn() - 5L); // setting some seconds to avoid too strict expiration
                return Pair.of(expiration, accessToken);
            } else {
                return v;
            }
        }).getRight();
    }
}
