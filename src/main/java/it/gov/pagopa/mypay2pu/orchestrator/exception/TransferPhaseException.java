package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class TransferPhaseException extends IllegalStateException {

  private final String code;

  public TransferPhaseException(String code, String message) {
    super(message);
    this.code = code;
  }

  public TransferPhaseException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
