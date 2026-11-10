package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.orchestrator.model.BaseEntity;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Utilities;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.util.StringUtils;

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
    String traceId = Utilities.getTraceId();
    entity.setUpdateTraceId(StringUtils.hasText(traceId) ? traceId : "-");
  }
}
