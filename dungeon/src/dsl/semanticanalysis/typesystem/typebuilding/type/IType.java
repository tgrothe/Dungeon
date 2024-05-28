package dsl.semanticanalysis.typesystem.typebuilding.type;

import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
public interface IType {
  enum Kind {
    Basic,
    Aggregate,
    AggregateAdapted,
    FunctionType,
    SetType,
    ListType,
    MapType,
    EnumType
  }

  // for neo4j
  long getId();

  /**
   * Getter for the type name
   *
   * @return the name of the type
   */
  String getName();

  /** */
  Kind getTypeKind();
}
