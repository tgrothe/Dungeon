package dsl.semanticanalysis.groum.node;

import org.neo4j.ogm.annotation.*;

@RelationshipEntity
public class GroumEdge {
  public enum GroumEdgeType {
    EDGE_NONE,
    EDGE_TEMPORAL,
    EDGE_DATA_READ,
    EDGE_DATA_WRITE,
    EDGE_CONTROL_PARENT
  }

  @Id @GeneratedValue private Long id;

  @StartNode private final GroumNode start;
  @EndNode private final GroumNode end;
  @Property private final int idxOnStart;
  @Property private final int idxOnEnd;
  @Property private final GroumEdgeType edgeType;
  @Property private String label;

  public GroumEdge() {
    this.start = GroumNode.NONE;
    this.end = GroumNode.NONE;
    this.idxOnStart = -1;
    this.idxOnEnd = -1;
    this.edgeType = GroumEdgeType.EDGE_NONE;
    this.label = this.toString();
  }

  public GroumEdge(GroumNode start, GroumNode end, GroumEdgeType edgeType) {
    this.start = start;
    this.idxOnStart = start.outgoing().size();
    start.addOutgoing(this);

    this.end = end;
    this.idxOnEnd = end.incoming().size();
    end.addIncoming(this);

    this.edgeType = edgeType;

    this.label = this.toString();

    if (start.processedCounter() == 4 && edgeType.equals(GroumEdgeType.EDGE_DATA_READ)) {
      boolean b = true;
    }
  }

  public GroumNode start() {
    return start;
  }

  public GroumNode end() {
    return end;
  }

  public GroumEdgeType edgeType() {
    return edgeType;
  }

  @Override
  public String toString() {
    return start().getLabel() + " -[" + this.edgeType.toString() + "]-> " + end.toString();
  }
}
