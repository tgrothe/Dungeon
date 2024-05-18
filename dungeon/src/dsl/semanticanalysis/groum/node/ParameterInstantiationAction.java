package dsl.semanticanalysis.groum.node;

import dsl.semanticanalysis.groum.GroumVisitor;
import dsl.semanticanalysis.symbol.Symbol;
import dsl.semanticanalysis.typesystem.typebuilding.type.IType;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class ParameterInstantiationAction extends ActionNode {
  @Relationship private final IType instanceType;
  @Relationship private final Symbol instanceSymbol;

  public ParameterInstantiationAction(Symbol parameterSymbol, long instanceId) {
    super(ActionType.parameterInstantiation);
    this.instanceType = parameterSymbol.getDataType();
    this.instanceSymbol = parameterSymbol;
    this.referencedInstanceId(instanceId);
    this.updateLabels();
  }

  public IType instantiatedType() {
    return this.instanceType;
  }

  public Symbol parameterSymbol() {
    return this.instanceSymbol;
  }

  @Override
  public String getLabel() {
    return this.instantiatedType().toString()
        + ":<param_init ["
        + this.referencedInstanceId()
        + "]>"
        + "(name: '"
        + this.parameterSymbol().getName()
        + "')";
  }

  public <T> T accept(GroumVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
