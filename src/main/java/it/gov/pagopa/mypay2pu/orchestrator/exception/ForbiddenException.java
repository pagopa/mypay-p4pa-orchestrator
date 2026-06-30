package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class ForbiddenException extends BaseBusinessException {
  public ForbiddenException(String code, String message) {
    super(code, message);
  }
}
