package it.gov.pagopa.mypay2pu.orchestrator.dao;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.utils.SqlLoader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MigrationDagDependencyDao {
  private static final String DAG_SQL_RESOURCE = "migration-dag-dependency/find-dag.sql";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final SqlLoader sqlLoader;
  private final RowMapper<MigrationDagDependency> rowMapper =
    DataClassRowMapper.newInstance(MigrationDagDependency.class);

  public MigrationDagDependencyDao(
    @Qualifier("orchestratorNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
    SqlLoader sqlLoader
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.sqlLoader = sqlLoader;
  }

  public List<MigrationDagDependency> findDag(Collection<MigrationFileType> fileTypesToInclude) {
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }

    List<String> fileTypeValues = fileTypesToInclude.stream()
      .map(Objects::requireNonNull)
      .map(MigrationFileType::getValue)
      .collect(Collectors.toList());
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("fileTypesToInclude", fileTypeValues);

    return jdbcTemplate.query(sqlLoader.load(DAG_SQL_RESOURCE), parameters, rowMapper);
  }
}
