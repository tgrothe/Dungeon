package dsl.semanticanalysis.typesystem.typebuilding.type;

import dsl.semanticanalysis.scope.IScope;
import dsl.semanticanalysis.symbol.ScopedSymbol;
import dsl.semanticanalysis.typesystem.typebuilding.TypeBuilder;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Transient;

import java.lang.reflect.Field;
import java.util.HashMap;

public class AggregateType extends ScopedSymbol implements IType {
  public static String NAME_SYMBOL_NAME = "$NAME$";

  @Transient
  protected Class<?> originType;
  @Transient
  private HashMap<String, Field> typeMemberToField;

  /**
   * Constructor
   *
   * @param name Name of this type
   * @param parentScope parent scope of this type
   */
  public AggregateType(String name, IScope parentScope) {
    super(name, parentScope, null);
    originType = null;
    typeMemberToField = new HashMap<>();
  }

  /**
   * Constructor
   *
   * @param name Name of this type
   * @param parentScope parent scope of this type
   * @param originType the origin java class of this {@link AggregateType}, if it was generated by
   *     the {@link TypeBuilder}
   */
  public AggregateType(String name, IScope parentScope, Class<?> originType) {
    super(name, parentScope, null);
    this.originType = originType;
    typeMemberToField = new HashMap<>();
  }

  public void setTypeMemberToField(HashMap<String, Field> typeMemberToField) {
    this.typeMemberToField = typeMemberToField;
  }

  public HashMap<String, Field> getTypeMemberToField() {
    return this.typeMemberToField;
  }

  @Override
  public Kind getTypeKind() {
    return Kind.Aggregate;
  }

  /**
   * @return the origin java class (or null, if it was not set)
   */
  public Class<?> getOriginType() {
    return this.originType;
  }
}
