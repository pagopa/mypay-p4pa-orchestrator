package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDag;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Builds and prunes the in-memory DAG from the persisted dependency rows.
 */
@Component
public class MigrationDagEngine {
  private static final String ROOT = "ROOT";

  /**
   * Reconstructs the hierarchy reachable from ROOT, topologically orders it, and
   * returns only requested file types while preserving each node's persisted parents.
   *
   * @param dependencies all dependency rows returned by the repository
   * @param fileTypesToInclude the functional file types to keep in the resulting DAG
   */
  public MigrationDag build(
    Collection<MigrationDagDependency> dependencies,
    Collection<MigrationFileType> fileTypesToInclude
  ) {
    Objects.requireNonNull(dependencies, "dependencies must not be null");
    Objects.requireNonNull(fileTypesToInclude, "fileTypesToInclude must not be null");
    if (fileTypesToInclude.isEmpty()) {
      throw new IllegalArgumentException("fileTypesToInclude must not be empty");
    }

    Map<String, MigrationFileType> requestedTypesByValue = new HashMap<>();
    for (MigrationFileType fileType : fileTypesToInclude) {
      MigrationFileType requestedType = Objects.requireNonNull(
        fileType,
        "fileTypesToInclude must not contain null"
      );
      requestedTypesByValue.put(requestedType.getValue(), requestedType);
    }

    Map<String, List<MigrationDagDependency>> rowsByParent = new HashMap<>();
    boolean hasRootRow = false;
    for (MigrationDagDependency dependency : dependencies) {
      Objects.requireNonNull(dependency, "dependencies must not contain null");
      String parent = dependency.getDependsOn();
      if (ROOT.equals(dependency.getFileType()) && ROOT.equals(parent)) {
        hasRootRow = true;
      }
      if (parent != null) {
        rowsByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(dependency);
      }
    }

    if (!hasRootRow) {
      return new MigrationDag(List.of());
    }

    Map<String, MutableNode> reachableNodes = new HashMap<>();
    Map<String, Set<String>> childrenByParent = new HashMap<>();
    Set<String> visitedParents = new HashSet<>();
    ArrayDeque<String> pendingParents = new ArrayDeque<>();
    pendingParents.add(ROOT);

    while (!pendingParents.isEmpty()) {
      String parent = pendingParents.removeFirst();
      if (!visitedParents.add(parent)) {
        continue;
      }
      for (MigrationDagDependency dependency : rowsByParent.getOrDefault(parent, List.of())) {
        String fileType = dependency.getFileType();
        if (fileType == null || fileType.isBlank()) {
          throw new IllegalStateException("DAG dependency has a blank file_type");
        }
        if (ROOT.equals(fileType) && ROOT.equals(parent)) {
          continue;
        }
        if (ROOT.equals(fileType)) {
          throw new IllegalStateException("ROOT cannot depend on a functional file type");
        }

        MutableNode node = reachableNodes.computeIfAbsent(fileType, ignored -> new MutableNode(fileType));
        node.addParent(parent, dependency.getMaxExecutions());
        if (!ROOT.equals(parent)) {
          childrenByParent.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(fileType);
        }
        pendingParents.addLast(fileType);
      }
    }

    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, Integer> levels = new HashMap<>();
    for (MutableNode node : reachableNodes.values()) {
      long functionalParents = node.parents.stream().filter(parent -> !ROOT.equals(parent)).count();
      inDegree.put(node.fileType, Math.toIntExact(functionalParents));
      levels.put(node.fileType, node.parents.contains(ROOT) ? 1 : 0);
    }

    PriorityQueue<String> ready = new PriorityQueue<>();
    inDegree.forEach((fileType, degree) -> {
      if (degree == 0) {
        ready.add(fileType);
      }
    });

    List<MigrationDagNode> topologicalNodes = new ArrayList<>(reachableNodes.size());
    int processedNodes = 0;
    while (!ready.isEmpty()) {
      String fileType = ready.remove();
      MutableNode node = reachableNodes.get(fileType);
      int level = levels.get(fileType);
      processedNodes++;
      if (level < 1) {
        throw new IllegalStateException("DAG node is not reachable from ROOT: " + fileType);
      }
      MigrationFileType requestedType = requestedTypesByValue.get(fileType);
      if (requestedType != null) {
        topologicalNodes.add(new MigrationDagNode(
          requestedType,
          node.parents.stream().toList(),
          level,
          node.maxExecutions
        ));
      }

      for (String child : childrenByParent.getOrDefault(fileType, Set.of())) {
        levels.merge(child, level + 1, Math::max);
        int remainingParents = inDegree.compute(child, (ignored, degree) -> degree - 1);
        if (remainingParents == 0) {
          ready.add(child);
        }
      }
    }

    if (processedNodes != reachableNodes.size()) {
      throw new IllegalStateException("Cycle detected in the migration DAG reachable from ROOT");
    }

    topologicalNodes.sort(Comparator
      .comparingInt(MigrationDagNode::level)
      .thenComparing(node -> node.fileType().getValue()));
    return new MigrationDag(topologicalNodes);
  }

  private static final class MutableNode {
    private final String fileType;
    private final Set<String> parents = new TreeSet<>();
    private Integer maxExecutions;
    private boolean maxExecutionsInitialized;

    private MutableNode(String fileType) {
      this.fileType = fileType;
    }

    private void addParent(String parent, Integer rowMaxExecutions) {
      parents.add(parent);
      if (!maxExecutionsInitialized) {
        maxExecutions = rowMaxExecutions;
        maxExecutionsInitialized = true;
      } else if (!Objects.equals(maxExecutions, rowMaxExecutions)) {
        throw new IllegalStateException("Inconsistent max_executions values for file_type " + fileType);
      }
    }
  }
}
