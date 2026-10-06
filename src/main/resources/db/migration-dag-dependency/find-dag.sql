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
    file_type,
    depends_on,
    lvl AS level,
    max_executions
FROM tree
WHERE file_type IN (:fileTypesToInclude)
ORDER BY lvl ASC, file_type ASC;
