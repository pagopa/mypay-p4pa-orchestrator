package it.gov.pagopa.mypay2pu.orchestrator.exception;

public class EvidenceException extends BaseBusinessException {

  public EvidenceException(String code, String message) {
    super(code, message);
  }

  public EvidenceException(String code, String message, Throwable cause) {
    super(code, message, cause);
  }
}
