package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.orchestrator.model.BaseEntity;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Utilities;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class BaseEntityListener {

  @PrePersist
  public void onPrePersist(BaseEntity entity) {
    onSave(entity);
  }

  @PreUpdate
  public void onPreUpdate(BaseEntity entity) {
    onSave(entity);
  }

  private void onSave(BaseEntity entity) {
    entity.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
    entity.setUpdateTraceId(Utilities.getTraceId());
  }
}
