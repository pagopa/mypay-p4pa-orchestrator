package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class TransferOutcomeException extends BaseBusinessException {

  public TransferOutcomeException(String code, String message) {
    super(code, message);
  }

  public TransferOutcomeException(String code, String message, Throwable cause) {
    super(code, message, cause);
  }
}
