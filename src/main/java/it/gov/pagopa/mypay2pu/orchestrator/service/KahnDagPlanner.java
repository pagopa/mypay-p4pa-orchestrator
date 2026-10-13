package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionLevel;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionNode;
import it.gov.pagopa.mypay2pu.orchestrator.model.ExecutionPlan;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationDagDependency;
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
 * Builds an immutable ExecutionPlan using Kahn's algorithm for topological sorting
 * and deriving deterministic parallel execution levels.
 */
@Component
public class KahnDagPlanner implements DagPlanner {
  private static final String ROOT = "ROOT";

  @Override
  public ExecutionPlan buildExecutionPlan(Collection<MigrationDagDependency> dependencies) {
    Objects.requireNonNull(dependencies, "dependencies must not be null");

    DependencyIndex dependencyIndex = indexDependencies(dependencies);
    if (!dependencyIndex.hasRootRow()) {
      return new ExecutionPlan(List.of());
    }

    ReachableGraph reachableGraph = collectReachableGraph(dependencyIndex.rowsByParent());
    return planLevels(reachableGraph.nodes(), reachableGraph.childrenByParent());
  }

  private DependencyIndex indexDependencies(Collection<MigrationDagDependency> dependencies) {
    Map<String, List<MigrationDagDependency>> rowsByParent = new HashMap<>();
    boolean hasRootRow = false;
    for (MigrationDagDependency dependency : dependencies) {
      Objects.requireNonNull(dependency, "dependencies must not contain null");
      String parent = dependency.getDependsOn();
      hasRootRow |= ROOT.equals(dependency.getFileType()) && ROOT.equals(parent);
      if (parent != null) {
        rowsByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(dependency);
      }
    }
    return new DependencyIndex(rowsByParent, hasRootRow);
  }

  private ReachableGraph collectReachableGraph(Map<String, List<MigrationDagDependency>> rowsByParent) {
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
      processDependencies(
          parent,
          rowsByParent.getOrDefault(parent, List.of()),
          reachableNodes,
          childrenByParent,
          pendingParents
      );
    }
    return new ReachableGraph(reachableNodes, childrenByParent);
  }

  private void processDependencies(String parent,
                                   List<MigrationDagDependency> dependencies,
                                   Map<String, MutableNode> reachableNodes,
                                   Map<String, Set<String>> childrenByParent,
                                   ArrayDeque<String> pendingParents) {
    for (MigrationDagDependency dependency : dependencies) {
      String fileType = dependency.getFileType();
      validateDependency(parent, fileType);
      if (ROOT.equals(fileType)) {
        continue;
      }
      MutableNode node = reachableNodes.computeIfAbsent(fileType, ignored -> new MutableNode(fileType));
      node.addParent(parent, dependency.getMaxExecutions());
      addChild(childrenByParent, parent, fileType);
      pendingParents.addLast(fileType);
    }
  }

  private void validateDependency(String parent, String fileType) {
    if (fileType == null || fileType.isBlank()) {
      throw new IllegalStateException("DAG dependency has a blank File Type");
    }
    if (ROOT.equals(fileType) && ROOT.equals(parent)) {
      return;
    }
    if (ROOT.equals(fileType)) {
      throw new IllegalStateException("ROOT cannot depend on a functional File Type");
    }
  }

  private void addChild(Map<String, Set<String>> childrenByParent, String parent, String child) {
    if (!ROOT.equals(parent)) {
      childrenByParent.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(child);
    }
  }

  private ExecutionPlan planLevels(Map<String, MutableNode> reachableNodes,
                                   Map<String, Set<String>> childrenByParent) {
    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, Integer> levels = new HashMap<>();
    for (MutableNode node : reachableNodes.values()) {
      long functionalParents = node.parents.stream().filter(parent -> !ROOT.equals(parent)).count();
      inDegree.put(node.fileType, Math.toIntExact(functionalParents));
      levels.put(node.fileType, node.parents.contains(ROOT) ? 1 : 0);
    }

    PriorityQueue<String> ready = new PriorityQueue<>();
    addReadyNodes(inDegree, ready);

    Map<String, MigrationFileType> functionalTypesByValue = functionalTypesByValue();
    Map<Integer, List<ExecutionNode>> nodesByLevel = new HashMap<>();
    int processedNodes = 0;

    while (!ready.isEmpty()) {
      String fileType = ready.remove();
      MutableNode node = reachableNodes.get(fileType);
      int level = levels.get(fileType);
      processedNodes++;

      if (level < 1) {
        throw new IllegalStateException("DAG node is not reachable from ROOT: " + node.fileType);
      }

      MigrationFileType functionalType = functionalTypesByValue.get(node.fileType);
      if (functionalType != null) {
        ExecutionNode executionNode = new ExecutionNode(
            functionalType,
            node.parents.stream().toList(),
            level,
            node.maxExecutions
        );
        nodesByLevel.computeIfAbsent(level, ignored -> new ArrayList<>()).add(executionNode);
      }

      processChildren(
          level,
          childrenByParent.getOrDefault(fileType, Set.of()),
          levels,
          inDegree,
          ready
      );
    }

    ensureAllNodesProcessed(processedNodes, reachableNodes.size());

    List<ExecutionLevel> executionLevels = nodesByLevel.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> {
          List<ExecutionNode> sortedNodes = entry.getValue().stream()
              .sorted(Comparator.comparing(node -> node.fileType().getValue()))
              .toList();
          return new ExecutionLevel(entry.getKey(), sortedNodes);
        })
        .toList();

    return new ExecutionPlan(executionLevels);
  }

  private void addReadyNodes(Map<String, Integer> inDegree, PriorityQueue<String> ready) {
    inDegree.forEach((fileType, degree) -> {
      if (degree == 0) {
        ready.add(fileType);
      }
    });
  }

  private void processChildren(int level,
                               Set<String> children,
                               Map<String, Integer> levels,
                               Map<String, Integer> inDegree,
                               PriorityQueue<String> ready) {
    for (String child : children) {
      levels.merge(child, level + 1, Math::max);
      Integer currentDegree = inDegree.get(child);
      if (currentDegree == null) {
        throw new IllegalStateException("DAG child has no in-degree: " + child);
      }
      int remainingParents = currentDegree - 1;
      inDegree.put(child, remainingParents);
      if (remainingParents == 0) {
        ready.add(child);
      }
    }
  }

  private void ensureAllNodesProcessed(int processedNodes, int totalNodes) {
    if (processedNodes != totalNodes) {
      throw new IllegalStateException("Cycle detected in the migration DAG reachable from ROOT");
    }
  }

  private Map<String, MigrationFileType> functionalTypesByValue() {
    Map<String, MigrationFileType> functionalTypesByValue = new HashMap<>();
    for (MigrationFileType fileType : MigrationFileType.values()) {
      functionalTypesByValue.put(fileType.getValue(), fileType);
    }
    return functionalTypesByValue;
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
        throw new IllegalStateException("Inconsistent max_executions values for File Type " + fileType);
      }
    }
  }

  private record DependencyIndex(Map<String, List<MigrationDagDependency>> rowsByParent, boolean hasRootRow) {}

  private record ReachableGraph(Map<String, MutableNode> nodes, Map<String, Set<String>> childrenByParent) {}
}
