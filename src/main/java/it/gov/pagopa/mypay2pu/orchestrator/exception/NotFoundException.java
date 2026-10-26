package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class NotFoundException extends BaseBusinessException {
  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
