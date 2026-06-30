package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class ConflictException extends BaseBusinessException {
  public ConflictException(String code, String message) {
    super(code, message);
  }
}
