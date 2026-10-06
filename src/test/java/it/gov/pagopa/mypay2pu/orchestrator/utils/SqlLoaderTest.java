package it.gov.pagopa.mypay2pu.orchestrator.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlLoaderTest {
  private final SqlLoader sqlLoader = new SqlLoader();

  @Test
  void loadsAndCachesSqlResource() {
    String firstLoad = sqlLoader.load("migration-dag/find-dag.sql");

    assertNotNull(firstLoad);
    assertTrue(firstLoad.contains("WITH RECURSIVE tree"));
    assertSame(firstLoad, sqlLoader.load("migration-dag/find-dag.sql"));
  }

  @Test
  void rejectsBlankAbsoluteAndTraversingPaths() {
    assertThrows(IllegalArgumentException.class, () -> sqlLoader.load(" "));
    assertThrows(IllegalArgumentException.class, () -> sqlLoader.load("/etc/passwd"));
    assertThrows(IllegalArgumentException.class, () -> sqlLoader.load("C:/windows/path.sql"));
    assertThrows(IllegalArgumentException.class, () -> sqlLoader.load("../../application.yml"));
  }

  @Test
  void reportsMissingResource() {
    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> sqlLoader.load("migration-dag/missing.sql")
    );

    assertTrue(exception.getMessage().contains("db/migration-dag-dependency/missing.sql"));
  }
}
