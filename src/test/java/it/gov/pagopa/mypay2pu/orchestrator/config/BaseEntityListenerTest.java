package it.gov.pagopa.mypay2pu.orchestrator.config;

import it.gov.pagopa.mypay2pu.orchestrator.model.BaseEntity;
import it.gov.pagopa.mypay2pu.orchestrator.utils.UtilitiesTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BaseEntityListenerTest {

  @Mock
  private BaseEntity baseEntityMock;

  private final BaseEntityListener listener = new BaseEntityListener();

  private final String traceId = "traceId";

  @BeforeEach
  void init(){
    UtilitiesTest.setTraceId(traceId);
  }

  @AfterEach
  void verifyNoMoreInteractions(){
    Mockito.verifyNoMoreInteractions(baseEntityMock);
    UtilitiesTest.clearTraceIdContext();
  }

  @Test
  void testOnPrePersist(){
    listener.onPrePersist(baseEntityMock);

    verify(baseEntityMock).setUpdateTraceId(traceId);
  }

  @Test
  void testOnPreUpdate(){
    listener.onPreUpdate(baseEntityMock);

    verify(baseEntityMock).setUpdateTraceId(traceId);
  }
}
