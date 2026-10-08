package it.gov.pagopa.mypay2pu.orchestrator.repository;

import java.util.List;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MigrationDagDependencyRepository extends Repository<MigrationDagDependency, Integer> {
  @Query(value = """
    WITH RECURSIVE tree AS (
        SELECT
            file_type,
            depends_on,
            0 AS lvl,
            max_executions
        FROM orchestrator.migration_dag_dependency
        WHERE file_type = 'ROOT'

        UNION ALL

        SELECT
            d.file_type,
            d.depends_on,
            t.lvl + 1,
            d.max_executions
        FROM orchestrator.migration_dag_dependency d
        JOIN tree t
          ON d.depends_on = t.file_type
        WHERE d.file_type <> d.depends_on
    )
    SELECT
        file_type AS "fileType",
        depends_on AS "dependsOn",
        lvl AS "lvl",
        max_executions AS "maxExecutions"
    FROM tree
    WHERE file_type = ANY(
        ARRAY[
            'ORGANIZATIONS',
            'ORG_SIL_SERVICES',
            'DEBT_POSITIONS_TYPE',
            'DEBT_POSITIONS_TYPE_ORG',
            'DEBT_POSITIONS_TYPE_ORG_OPERATORS',
            'DEBT_POSITIONS',
            'DEBT_POSITIONS_PAID',
            'PAYMENT_NOTIFICATION',
            'PAYMENTS_REPORTING',
            'TREASURY_CSV_COMPLETE',
            'ASSESSMENTS_REGISTRY',
            'ASSESSMENTS'
        ]
    )
    ORDER BY lvl, file_type
    """, nativeQuery = true)
  List<MigrationDagDependency> findDag();
}
