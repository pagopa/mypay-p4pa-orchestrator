package it.gov.pagopa.mypay2pu.orchestrator.service;

import java.time.OffsetDateTime;

/**
 * The persisted UTC bounds used locally when submitting an extraction.
 */
public record ExtractionWindow(OffsetDateTime dateFrom, OffsetDateTime dateTo, boolean delta) {
}
