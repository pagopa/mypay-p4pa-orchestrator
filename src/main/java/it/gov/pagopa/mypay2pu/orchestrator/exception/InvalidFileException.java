package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class InvalidFileException extends BaseBusinessException {

  public InvalidFileException(String message) {
    this(message, null);
  }

  public InvalidFileException(String message, Throwable e) {
    super("INVALID_FILE", message, e);
  }
}
