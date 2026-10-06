package it.gov.pagopa.mypay2pu.orchestrator.dao;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.utils.SqlLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrationDagDependencyDaoTest {
  private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
  private final SqlLoader sqlLoader = new SqlLoader();
  private final MigrationDagDependencyDao dao = new MigrationDagDependencyDao(jdbcTemplate, sqlLoader);

  @Test
  void bindsIncludedTypesAndPreservesDatabaseOrder() {
    List<MigrationFileType> includedTypes = List.of(
      MigrationFileType.fromValue("ORGANIZATIONS"),
      MigrationFileType.fromValue("DEBT_POSITIONS_TYPE")
    );
    List<MigrationDagDependency> databaseRows = List.of(
      new MigrationDagDependency(MigrationFileType.fromValue("ORGANIZATIONS"), "ROOT", 0, 2),
      new MigrationDagDependency(MigrationFileType.fromValue("DEBT_POSITIONS_TYPE"), "ORGANIZATIONS", 1, 1)
    );
    when(jdbcTemplate.query(
      eq(sqlLoader.load("migration-dag-dependency/find-dag.sql")),
      any(MapSqlParameterSource.class),
      ArgumentMatchers.<RowMapper<MigrationDagDependency>>any()
    )).thenReturn(databaseRows);

    List<MigrationDagDependency> result = dao.findDag(includedTypes);

    assertEquals(databaseRows, result);
    ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbcTemplate).query(
      eq(sqlLoader.load("migration-dag-dependency/find-dag.sql")),
      parameters.capture(),
      ArgumentMatchers.<RowMapper<MigrationDagDependency>>any()
    );
    assertEquals(
      List.of("ORGANIZATIONS", "DEBT_POSITIONS_TYPE"),
      parameters.getValue().getValue("fileTypesToInclude")
    );
  }

  @Test
  void rejectsAnEmptyFileTypeFilter() {
    assertThrows(IllegalArgumentException.class, () -> dao.findDag(List.of()));
  }
}
