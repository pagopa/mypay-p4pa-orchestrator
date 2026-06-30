package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class NotAuthorizedException extends BaseBusinessException {
  public NotAuthorizedException(String code, String message) {
    super(code, message);
  }
}
