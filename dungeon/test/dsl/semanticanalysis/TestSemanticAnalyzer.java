package dsl.semanticanalysis;

import dsl.annotation.DSLType;
import dsl.annotation.DSLTypeMember;
import dsl.helpers.Helpers;
import dsl.interpreter.DummyNativeFunction;
import dsl.interpreter.TestEnvironment;
import dsl.interpreter.mockecs.*;
import dsl.parser.ast.*;
import dsl.runtime.callable.ICallable;
import dsl.semanticanalysis.analyzer.SemanticAnalyzer;
import dsl.semanticanalysis.environment.GameEnvironment;
import dsl.semanticanalysis.scope.Scope;
import dsl.semanticanalysis.symbol.FunctionSymbol;
import dsl.semanticanalysis.symbol.ImportFunctionSymbol;
import dsl.semanticanalysis.symbol.ScopedSymbol;
import dsl.semanticanalysis.symbol.Symbol;
import dsl.semanticanalysis.typesystem.typebuilding.TypeBuilder;
import dsl.semanticanalysis.typesystem.typebuilding.type.*;
import dslinterop.dslnativefunction.NativePrint;
import graph.taskdependencygraph.TaskDependencyGraph;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class TestSemanticAnalyzer {
  private static final Path testLibPath = Path.of("test_resources/testlib");

  /** Test, if the name of symbols is set correctly */
  @Test
  public void testSymbolName() {
    String program =
        """
            graph g {
                A -> B
            }
            dungeon_config c {
                level_graph: g
            }
            """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForAST(ast);

    // check the name of the symbol corresponding to the taksDependencyGraph definition
    var graphDefAstNode = ast.getChild(0);
    var symbolForDotDefNode =
        symtableResult.symbolTable.getSymbolsForAstNode(graphDefAstNode).get(0);
    Assert.assertEquals("g", symbolForDotDefNode.getName());

    // check the name of the symbol corresponding to the object definition
    var objDefNode = ast.getChild(1);
    var symbolForObjDefNode = symtableResult.symbolTable.getSymbolsForAstNode(objDefNode).get(0);
    Assert.assertEquals("c", symbolForObjDefNode.getName());
  }

  @DSLType
  private record TestComponent(@DSLTypeMember TaskDependencyGraph levelGraph) {}

  /**
   * Test, if the reference to a symbol is correctly resolved and that the symbol is linked to the
   * identifier
   */
  @Test
  public void testSymbolReferenceComponent() {
    String program =
        """
            graph g {
                A -> B
            }

            entity_type c {
                test_component{
                    level_graph: g
                }
            }
            """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    TypeBuilder tb = new TypeBuilder(new TypeFactory());
    Scope scope = new Scope();
    var testComponentType = tb.createDSLTypeForJavaTypeInScope(scope, TestComponent.class);

    var env = new GameEnvironment();
    env.loadTypes(testComponentType);
    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;

    // check the name of the symbol corresponding to the taksDependencyGraph definition
    var graphDefAstNode = ast.getChild(0);
    var symbolForDotDefNode = symbolTable.getSymbolsForAstNode(graphDefAstNode).get(0);

    // check, if the stmt of the propertyDefinition references the symbol of the
    // taksDependencyGraph
    // definition
    var gameObjDefNode = ast.getChild(1);
    var componentDefNode =
        ((PrototypeDefinitionNode) gameObjDefNode).getComponentDefinitionNodes().get(0);
    var propertyDefList = componentDefNode.getChild(1);

    var firstPropertyDef = propertyDefList.getChild(0);
    var firstPropertyStmtNode = firstPropertyDef.getChild(1);
    assert (firstPropertyStmtNode.type == Node.Type.Identifier);
    var symbolForStmtNode = symbolTable.getSymbolsForAstNode(firstPropertyStmtNode).get(0);
    Assert.assertEquals("g", symbolForStmtNode.getName());
    Assert.assertEquals(symbolForDotDefNode, symbolForStmtNode);
  }

  @Test
  public void testItemTypeDeclaration() {
    String program =
        """
        item_type item_type1 {
            display_name: "MyName",
            description: "Hello, this is a description",
            texture_path: "texture/path"
        }
        """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();

    var env = new GameEnvironment();
    semanticAnalyzer.setup(env);
    var result = semanticAnalyzer.walk(ast);
    SymbolTable symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);
    Symbol itemTypeSymbol = fileScope.resolve("item_type1");
    Assert.assertNotEquals(Symbol.NULL, itemTypeSymbol);
    Assert.assertTrue(itemTypeSymbol instanceof ScopedSymbol);

    ScopedSymbol scopedItemTypeSymbol = (ScopedSymbol) itemTypeSymbol;
    Symbol displayNameSymbol = scopedItemTypeSymbol.resolve("display_name");
    Assert.assertNotEquals(Symbol.NULL, displayNameSymbol);
    Symbol descriptionSymbol = scopedItemTypeSymbol.resolve("description");
    Assert.assertNotEquals(Symbol.NULL, descriptionSymbol);
    Symbol texturePathSymbol = scopedItemTypeSymbol.resolve("texture_path");
    Assert.assertNotEquals(Symbol.NULL, texturePathSymbol);
  }

  @Test
  public void testItemTypeDeclarationBroken() {
    String program =
        """
      item_type item_type1 {
          display_name: "MyName",
          description: "Hello, this is a description",
          : "hello"
      }
      """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();

    var env = new GameEnvironment();
    semanticAnalyzer.setup(env);
    var result = semanticAnalyzer.walk(ast);
    SymbolTable symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);
    Symbol itemTypeSymbol = fileScope.resolve("item_type1");
    Assert.assertNotEquals(Symbol.NULL, itemTypeSymbol);
    Assert.assertTrue(itemTypeSymbol instanceof ScopedSymbol);

    ScopedSymbol scopedItemTypeSymbol = (ScopedSymbol) itemTypeSymbol;
    Symbol displayNameSymbol = scopedItemTypeSymbol.resolve("display_name");
    Assert.assertNotEquals(Symbol.NULL, displayNameSymbol);
    Symbol descriptionSymbol = scopedItemTypeSymbol.resolve("description");
    Assert.assertNotEquals(Symbol.NULL, descriptionSymbol);
    Assert.assertEquals(2, ((ScopedSymbol) itemTypeSymbol).getSymbols().size());
  }

  /**
   * Test, if the reference to a symbol is correctly resolved and that the symbol is linked to the
   * identifier
   */
  @Test
  public void testSymbolReference() {
    String program =
        """
            graph g {
                A -> B
            }
            dungeon_config c {
                level_graph: g
            }
            """;

    GameEnvironment env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForAST(ast);

    // check the name of the symbol corresponding to the taksDependencyGraph definition
    var graphDefAstNode = ast.getChild(0);
    var symbolForDotDefNode =
        symtableResult.symbolTable.getSymbolsForAstNode(graphDefAstNode).get(0);

    // check, if the stmt of the propertyDefinition references the symbol of the
    // taksDependencyGraph
    // definition
    var objDefNode = ast.getChild(1);
    var propertyDefList = objDefNode.getChild(2);

    var firstPropertyDef = propertyDefList.getChild(0);
    var firstPropertyStmtNode = firstPropertyDef.getChild(1);
    assert (firstPropertyStmtNode.type == Node.Type.Identifier);
    var symbolForStmtNode =
        symtableResult.symbolTable.getSymbolsForAstNode(firstPropertyStmtNode).get(0);
    Assert.assertEquals("g", symbolForStmtNode.getName());
    Assert.assertEquals(symbolForDotDefNode, symbolForStmtNode);
  }

  /** Test, if native functions are correctly setup and linked to function call */
  @Test
  public void testSetupNativeFunctions() {
    String program =
        """
            dungeon_config c {
                points: print("Hello")
            }
                    """;

    GameEnvironment env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForAST(ast);

    var printFuncDefSymbol = symtableResult.symbolTable.globalScope.resolve("print");
    Assert.assertNotNull(printFuncDefSymbol);
    Assert.assertEquals(Symbol.SymbolType.Callable, printFuncDefSymbol.getSymbolType());
    Assert.assertTrue(printFuncDefSymbol instanceof NativePrint);
  }

  /** Test, if a native function call is correctly resolved */
  @Test
  public void testResolveNativeFunction() {
    String program =
        """
            dungeon_config c {
                points: print("Hello")
            }
                    """;

    GameEnvironment env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForAST(ast);

    var printFuncDefSymbol = symtableResult.symbolTable.globalScope.resolve("print");

    var questConfig = ast.getChild(0);
    var propDefList = questConfig.getChild(2);
    var propDef = propDefList.getChild(0);
    var funcCallNode = propDef.getChild(1);

    Assert.assertEquals(Node.Type.FuncCall, funcCallNode.type);

    var symbolForFuncCallNode =
        symtableResult.symbolTable.getSymbolsForAstNode(funcCallNode).get(0);
    Assert.assertEquals(symbolForFuncCallNode, printFuncDefSymbol);
  }

  // TODO: is this even correct? should it be linked? this currently prevents
  //  multiple instances of the same datatype...

  /**
   * Test, if symbol of property of aggregate datatype is correctly linked to the symbol inside of
   * the datatype
   */
  @Test
  public void testPropertyReference() {
    String program =
        """
            graph g {
                A -> B
            }
            dungeon_config c {
                dependency_graph: g
            }
            dungeon_config d {
                dependency_graph: g
            }
                """;

    // generate symbol table
    GameEnvironment env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForAST(ast);

    // get property definition list of the object definition
    var objDefNode = ast.getChild(1);
    var propertyDefList = objDefNode.getChild(2);

    // get the first property definition of the property definition list
    var firstPropertyDef = propertyDefList.getChild(0);
    var firstPropertyIdNode = firstPropertyDef.getChild(0);
    assert (firstPropertyIdNode.type == Node.Type.Identifier);

    // resolve 'level_graph' property of quest_config type in the datatype
    var questConfigType = symtableResult.symbolTable.globalScope.resolve("dungeon_config");
    var levelGraphPropertySymbol = ((AggregateType) questConfigType).resolve("dependency_graph");
    Assert.assertNotEquals(Symbol.NULL, levelGraphPropertySymbol);

    var symbolForPropertyIdNode =
        symtableResult.symbolTable.getSymbolsForAstNode(firstPropertyDef).get(0);

    Assert.assertEquals(levelGraphPropertySymbol, symbolForPropertyIdNode);
  }

  /** Test, if a native function call is correctly resolved */
  @Test
  public void funcDef() {
    String program =
        """
            fn test_func(int param1, float param2, string param3) -> int {
                print(param1);
            }
            """;

    var ast = Helpers.getASTFromString(program);
    var env = new GameEnvironment();
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcSymbol = (FunctionSymbol) fileScope.resolve("test_func");
    Assert.assertEquals("test_func", funcSymbol.getName());

    IType functionType = (IType) fileScope.resolve("$fn(int, float, string) -> int$");
    Assert.assertEquals(functionType, funcSymbol.getDataType());
    Assert.assertEquals(ICallable.Type.UserDefined, funcSymbol.getCallableType());
    Assert.assertNotEquals(Symbol.NULL, funcSymbol.resolve("param1"));
    Assert.assertNotEquals(Symbol.NULL, funcSymbol.resolve("param2"));
    Assert.assertNotEquals(Symbol.NULL, funcSymbol.resolve("param3"));
  }

  @Test
  public void resolveParameterInFunctionBody() {
    String program =
        """
            fn test_func(int param1, float param2, string param3) -> int {
                print(param1);
            }
            """;

    var ast = Helpers.getASTFromString(program);
    var env = new GameEnvironment();
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcDefNode = ast.getChild(0);
    var stmtBlock = funcDefNode.getChild(3);
    var funcCallStmt = stmtBlock.getChild(0);
    var paramList = funcCallStmt.getChild(1);
    var firstParam = paramList.getChild(0);

    var symbolForParam1 = symtableResult.symbolTable.getSymbolsForAstNode(firstParam).get(0);
    var funcDef = fileScope.resolve("test_func");
    var parameterSymbolFromFunctionSymbol = ((FunctionSymbol) funcDef).resolve("param1");
    Assert.assertEquals(parameterSymbolFromFunctionSymbol, symbolForParam1);
  }

  @Test
  public void funcDefFuncType() {
    String program =
        """
            fn test_func_1(int param1, float param2, string param3) -> int {
                print(param1);
            }
            fn test_func_2(int param4, float param5, string param6) -> int {
                print(param4);
            }
            """;

    var ast = Helpers.getASTFromString(program);
    var env = new GameEnvironment();
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcSymbol1 = (FunctionSymbol) fileScope.resolve("test_func_1");
    var funcSymbol2 = (FunctionSymbol) fileScope.resolve("test_func_2");
    Assert.assertEquals(funcSymbol1.getDataType(), funcSymbol2.getDataType());
  }

  @Test
  public void funcTypeObjectEquality() {
    String program =
        """
        fn test_func_1(int param1, float param2, string param3) -> int {
            print(param1);
        }
        fn test_func_2(int param4, float param5, string param6) -> int {
            print(param4);
        }
        """;

    var ast = Helpers.getASTFromString(program);
    var env = new GameEnvironment();
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcSymbol1 = (FunctionSymbol) fileScope.resolve("test_func_1");
    var funcType1 = funcSymbol1.getDataType();
    var funcSymbol2 = (FunctionSymbol) fileScope.resolve("test_func_2");
    var funcType2 = funcSymbol2.getDataType();
    Assert.assertEquals(funcType1.hashCode(), funcType2.hashCode());
  }

  @Test
  public void funcTypeNativeFunction() {
    var env = new GameEnvironment();

    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();
    symbolTableParser.setup(env);

    var symbolTableParserEnvironment = symbolTableParser.getEnvironment();
    var nativePrintSymbol = symbolTableParserEnvironment.getGlobalScope().resolve("print");
    Assert.assertTrue(nativePrintSymbol.getDataType() instanceof FunctionType);
  }

  @Test
  public void removeFuncTypeRedundancy() {
    String program = """
        fn test_func_1(string param) -> int {

        }
        """;

    var env = new TestEnvironment();

    // load two dummy functions with the same semantic function type as the defined function in
    // the dsl input and check, if they are all using the same FunctionType OBJECT after setup
    // of the symbolTableParser
    var dummyFunc1 =
        new DummyNativeFunction(
            "dummyFunc1", new FunctionType(BuiltInType.intType, BuiltInType.stringType));
    var dummyFunc2 =
        new DummyNativeFunction(
            "dummyFunc2", new FunctionType(BuiltInType.intType, BuiltInType.stringType));

    env.loadFunctions(dummyFunc1, dummyFunc2);

    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();
    symbolTableParser.setup(env);

    var ast = Helpers.getASTFromString(program);
    symbolTableParser.walk(ast);

    var symbolTableParserEnvironment = symbolTableParser.getEnvironment();
    var fileScope = symbolTableParserEnvironment.getFileScope(null);

    var dummyFunc1Sym = fileScope.resolve("dummyFunc1");
    var dummyFunc2Sym = fileScope.resolve("dummyFunc2");
    var testFunc1 = fileScope.resolve("test_func_1");
    Assert.assertEquals(
        dummyFunc1Sym.getDataType().hashCode(), dummyFunc2Sym.getDataType().hashCode());
    Assert.assertEquals(dummyFunc1Sym.getDataType().hashCode(), testFunc1.getDataType().hashCode());
  }

  /** Test, if a native function call is correctly resolved in nested stmt blocks */
  @Test
  public void funcDefNestedBlocks() {
    String program =
        """
        fn test_func(int param1, float param2, string param3) -> int
        {
            {
                {
                    print(param1);
                }
            }
        }
        """;

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForAST(ast);

    FuncDefNode funcDefNode = (FuncDefNode) ast.getChild(0);
    var stmtList = funcDefNode.getStmts();
    Assert.assertEquals(1, stmtList.size());

    Node outerStmtBlock = funcDefNode.getStmtBlock();
    Node middleStmtBlock = outerStmtBlock.getChild(0);
    Node innerStmtBlock = middleStmtBlock.getChild(0);
    Node funcCallStmt = ((StmtBlockNode) innerStmtBlock).getStmts().get(0);
    var funcCallNode = (FuncCallNode) funcCallStmt;

    var funcCallSymbol = result.symbolTable.getSymbolsForAstNode(funcCallNode).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);
  }

  /** Test, if a native function call is correctly resolved in nested stmt blocks */
  @Test
  public void funcDefIfElse() {
    String program =
        """
        fn test_func(int param1, float param2, string param3) -> int
        {
            if print() {
                print();
            } else if print() {
                print();
            } else {
                print();
            }
        }
        """;

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForAST(ast);

    FuncDefNode funcDefNode = (FuncDefNode) ast.getChild(0);
    var stmtList = funcDefNode.getStmts();
    var conditionalStmt = stmtList.get(0);
    var outerCondition = ((ConditionalStmtNodeIfElse) conditionalStmt).getCondition();
    var outerConditionAsFuncCall = (FuncCallNode) outerCondition;

    var funcCallSymbol = result.symbolTable.getSymbolsForAstNode(outerConditionAsFuncCall).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);

    var ifStmt = ((ConditionalStmtNodeIfElse) conditionalStmt).getIfStmt();
    var ifStmtFuncCall = ((StmtBlockNode) ifStmt).getStmts().get(0);
    funcCallSymbol = result.symbolTable.getSymbolsForAstNode(ifStmtFuncCall).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);

    var elseIfStmt = ((ConditionalStmtNodeIfElse) conditionalStmt).getElseStmt();
    var elseIfCondition = ((ConditionalStmtNodeIfElse) elseIfStmt).getCondition();
    funcCallSymbol = result.symbolTable.getSymbolsForAstNode(elseIfCondition).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);

    var elseIfStmtBlock = ((ConditionalStmtNodeIfElse) elseIfStmt).getIfStmt();
    var elseIfStmtBlockFuncCall = ((StmtBlockNode) elseIfStmtBlock).getStmts().get(0);
    funcCallSymbol = result.symbolTable.getSymbolsForAstNode(elseIfStmtBlockFuncCall).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);

    var elseStmt = ((ConditionalStmtNodeIfElse) elseIfStmt).getElseStmt();
    var elseStmtBlockFuncCall = ((StmtBlockNode) elseStmt).getStmts().get(0);
    funcCallSymbol = result.symbolTable.getSymbolsForAstNode(elseStmtBlockFuncCall).get(0);
    Assert.assertEquals(NativePrint.func, funcCallSymbol);
  }

  @Test
  public void memberAccessSimple() {
    String program =
        """
        fn test_func(test_component2 comp)
        {
            print(comp.member1);
        }
        """;

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), TestComponent2.class);

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;

    FuncDefNode funcDefNode = (FuncDefNode) ast.getChild(0);
    FunctionSymbol functionSymbol =
        (FunctionSymbol) symbolTable.getSymbolsForAstNode(funcDefNode).get(0);

    ParamDefNode paramDefNode = (ParamDefNode) funcDefNode.getParameters().get(0);
    IdNode paramDefIdNode = (IdNode) paramDefNode.getIdNode();
    Symbol parameterSymbol = symbolTable.getSymbolsForAstNode(paramDefIdNode).get(0);

    var stmtList = funcDefNode.getStmts();
    var printStmt = stmtList.get(0);
    var printStmtFuncCall = (FuncCallNode) printStmt;
    MemberAccessNode printParameterNode =
        (MemberAccessNode) (printStmtFuncCall.getParameters().get(0));

    Assert.assertEquals(Node.Type.MemberAccess, printParameterNode.type);

    // check, whether the 'comp' identifier in print-call is linked to the symbol
    // of the function parameter
    IdNode memberAccessLhs = (IdNode) printParameterNode.getLhs();
    var symbolsForCompIdentifier = symbolTable.getSymbolsForAstNode(memberAccessLhs);
    Assert.assertEquals(1, symbolsForCompIdentifier.size());

    var symbolForCompIdentifier = symbolsForCompIdentifier.get(0);
    Assert.assertEquals(functionSymbol, symbolForCompIdentifier.getScope());
    Assert.assertEquals(parameterSymbol, symbolForCompIdentifier);

    // check, whether the 'member1' identifier in print-call is linked to the
    // member symbol inside the test_component2 datatype
    AggregateType testComponent2Type =
        (AggregateType) symbolTable.globalScope.resolveType("test_component2");
    Symbol member1Symbol = testComponent2Type.resolve("member1");

    IdNode memberAccessRhs = (IdNode) printParameterNode.getRhs();
    var symbolsForMember1Identifier = symbolTable.getSymbolsForAstNode(memberAccessRhs);
    Assert.assertEquals(1, symbolsForMember1Identifier.size());

    var symbolForMember1Identifier = symbolsForMember1Identifier.get(0);
    Assert.assertEquals(member1Symbol, symbolForMember1Identifier);
  }

  @Test
  public void memberAccessFuncCall() {
    String program =
        """
        fn other_func(test_component2 comp) -> test_component2 {
            return comp;
        }

        fn test_func(test_component2 comp)
        {
            print(other_func(comp).member1);
        }
        """;

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), TestComponent2.class);

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;

    FuncDefNode otherFuncDefNode = (FuncDefNode) ast.getChild(0);
    FunctionSymbol otherFuncSymbol =
        (FunctionSymbol) symbolTable.getSymbolsForAstNode(otherFuncDefNode).get(0);
    FuncDefNode testFuncDefNode = (FuncDefNode) ast.getChild(1);

    var stmtList = testFuncDefNode.getStmts();
    var printStmt = stmtList.get(0);
    var printStmtFuncCall = (FuncCallNode) printStmt;
    MemberAccessNode printParameterNode =
        (MemberAccessNode) (printStmtFuncCall.getParameters().get(0));

    Assert.assertEquals(Node.Type.MemberAccess, printParameterNode.type);

    // check, whether the other_test-call in print-call is linked to the corresponding function
    // symbol
    Node memberAccessLhs = printParameterNode.getLhs();
    var symbolsForCompIdentifier = symbolTable.getSymbolsForAstNode(memberAccessLhs);
    Assert.assertEquals(1, symbolsForCompIdentifier.size());

    var symbolForCompIdentifier = symbolsForCompIdentifier.get(0);
    Assert.assertEquals(otherFuncSymbol, symbolForCompIdentifier);

    // check, whether the 'member1' identifier in print-call is linked to the
    // member symbol inside the test_component2 datatype
    AggregateType testComponent2Type =
        (AggregateType) symbolTable.globalScope.resolveType("test_component2");
    Symbol member1Symbol = testComponent2Type.resolve("member1");

    IdNode memberAccessRhs = (IdNode) printParameterNode.getRhs();
    var symbolsForMember1Identifier = symbolTable.getSymbolsForAstNode(memberAccessRhs);
    Assert.assertEquals(1, symbolsForMember1Identifier.size());

    var symbolForMember1Identifier = symbolsForMember1Identifier.get(0);
    Assert.assertEquals(member1Symbol, symbolForMember1Identifier);
  }

  @Test
  public void memberAccessFuncCallChainedMethod() {
    String program =
        """
    fn other_func(test_component2 comp) -> test_component2 {
        return comp;
    }

    fn test_func(test_component2 comp)
    {
        print(other_func(comp).my_method(42,42).member1);
    }
    """;

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), TestComponent2.class);
    env.typeBuilder().bindMethod(env.getGlobalScope(), TestComponent2.MyMethod.instance);

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);

    FuncDefNode otherFuncDefNode = (FuncDefNode) ast.getChild(0);
    FunctionSymbol otherFuncSymbol =
        (FunctionSymbol) symbolTable.getSymbolsForAstNode(otherFuncDefNode).get(0);
    FuncDefNode testFuncDefNode = (FuncDefNode) ast.getChild(1);

    var stmtList = testFuncDefNode.getStmts();
    var printStmt = stmtList.get(0);
    var printStmtFuncCall = (FuncCallNode) printStmt;
    MemberAccessNode printParameterNode =
        (MemberAccessNode) (printStmtFuncCall.getParameters().get(0));

    Assert.assertEquals(Node.Type.MemberAccess, printParameterNode.type);

    // check, whether the other_test-call in print-call is linked to the corresponding function
    // symbol
    Node memberAccessLhs = printParameterNode.getLhs();
    var symbolsForFuncCall = symbolTable.getSymbolsForAstNode(memberAccessLhs);
    Assert.assertEquals(1, symbolsForFuncCall.size());

    var symbolForCompIdentifier = symbolsForFuncCall.get(0);
    Assert.assertEquals(otherFuncSymbol, symbolForCompIdentifier);

    MemberAccessNode parameterNodeRhs = (MemberAccessNode) printParameterNode.getRhs();
    Node methodCallNode = parameterNodeRhs.getLhs();
    var symbolsForMethodCall = symbolTable.getSymbolsForAstNode(methodCallNode);
    Assert.assertEquals(1, symbolsForMethodCall.size());

    AggregateType testComponent2Type = (AggregateType) fileScope.resolveType("test_component2");
    Symbol methodDeclSymbol = testComponent2Type.resolve("my_method");

    var symbolForMethodCall = symbolsForMethodCall.get(0);
    Assert.assertEquals(methodDeclSymbol, symbolForMethodCall);

    // check, whether the 'member1' identifier in print-call is linked to the
    // member symbol inside the test_component2 datatype
    Symbol member1Symbol = testComponent2Type.resolve("member1");

    IdNode memberAccessRhs = (IdNode) parameterNodeRhs.getRhs();
    var symbolsForMember1Identifier = symbolTable.getSymbolsForAstNode(memberAccessRhs);
    Assert.assertEquals(1, symbolsForMember1Identifier.size());

    var symbolForMember1Identifier = symbolsForMember1Identifier.get(0);
    Assert.assertEquals(member1Symbol, symbolForMember1Identifier);
  }

  @Test
  public void testVariableCreation() {
    String program =
        """
entity_type my_type {
    test_component_with_callback {
        consumer: get_property
    }
}

fn get_property(entity ent) {
    var test : string;
}

quest_config c {
    entity: instantiate(my_type)
}
""";

    // print currently just prints to system.out, so we need to
    // check the contents for the printed string
    var outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), Entity.class);
    env.typeBuilder()
        .createDSLTypeForJavaTypeInScope(
            env.getGlobalScope(), TestComponentEntityConsumerCallback.class);

    var ast = Helpers.getASTFromString(program, env);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);

    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("get_property");
    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);
    VarDeclNode declNode = (VarDeclNode) funcDefNode.getStmtBlock().getChild(0);
    Symbol testVariableSymbol = symbolTable.getSymbolsForAstNode(declNode).get(0);

    Assert.assertNotEquals(Symbol.NULL, testVariableSymbol);
    Assert.assertEquals(BuiltInType.stringType, testVariableSymbol.getDataType());
  }

  @Test
  public void testVariableCreationIfStmt() {
    String program =
        """
        entity_type my_type {
            test_component_with_string_consumer_callback {
                on_interaction: callback
            }
        }

        fn callback(entity ent) {
            if true
                var test : string;
            else
                var test : string;
        }

        quest_config c {
            entity: instantiate(my_type)
        }
        """;

    // print currently just prints to system.out, so we need to
    // check the contents for the printed string
    var outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), Entity.class);
    env.typeBuilder()
        .createDSLTypeForJavaTypeInScope(
            env.getGlobalScope(), TestComponentWithStringConsumerCallback.class);

    var ast = Helpers.getASTFromString(program, env);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);

    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("callback");

    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);
    ConditionalStmtNodeIfElse conditional =
        (ConditionalStmtNodeIfElse) funcDefNode.getStmtBlock().getChild(0);
    VarDeclNode ifStmtDeclNode = (VarDeclNode) conditional.getIfStmt();
    VarDeclNode elseStmtDeclNode = (VarDeclNode) conditional.getElseStmt();

    Symbol ifStmtDeclSymbol = symbolTable.getSymbolsForAstNode(ifStmtDeclNode).get(0);
    Assert.assertNotEquals(Symbol.NULL, ifStmtDeclSymbol);

    // test correct scope relation
    var declScope = ifStmtDeclSymbol.getScope();
    // Note: expected scope relation:
    // - declScope = scope of if-Stmt
    // - parent of declScope = stmt-block of function
    // - parent of parent of declScope = function-scope
    var expectedToBeFunctionScope = declScope.getParent().getParent();
    Assert.assertEquals(funcSymbol, expectedToBeFunctionScope);

    Symbol elseStmtDeclSymbol = symbolTable.getSymbolsForAstNode(elseStmtDeclNode).get(0);
    Assert.assertNotEquals(Symbol.NULL, ifStmtDeclSymbol);
    // test correct scope relation
    declScope = elseStmtDeclSymbol.getScope();
    expectedToBeFunctionScope = declScope.getParent().getParent();
    Assert.assertEquals(funcSymbol, expectedToBeFunctionScope);
  }

  @Test
  public void testEnumVariantBinding() {
    String program =
        """
    fn callback(entity ent) -> my_enum {
        return my_enum.A;
    }
    """;

    // print currently just prints to system.out, so we need to
    // check the contents for the printed string
    var outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), Entity.class);
    env.typeBuilder()
        .createDSLTypeForJavaTypeInScope(
            env.getGlobalScope(), TestComponentWithStringConsumerCallback.class);

    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), MyEnum.class);

    var ast = Helpers.getASTFromString(program);
    var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = result.symbolTable;
    var fileScope = env.getFileScope(null);

    // check creation and binding of enum type in global scope
    Symbol myEnumType = fileScope.resolve("my_enum");
    Assert.assertNotEquals(Symbol.NULL, myEnumType);
    Assert.assertTrue(myEnumType instanceof EnumType);

    // get the function definition as symbol and AST-Node
    // TODO: probably must be resolved in file Scope
    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("callback");
    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);

    // check, if the return type id is bound to enum type
    IdNode retTypeId = (IdNode) funcDefNode.getRetTypeId();
    Symbol retTypeSymbol = symbolTable.getSymbolsForAstNode(retTypeId).get(0);
    Assert.assertEquals(myEnumType, retTypeSymbol);

    // check, that the `my_enum` in return stmt is bound to enum type
    ReturnStmtNode stmt = (ReturnStmtNode) funcDefNode.getStmts().get(0);
    MemberAccessNode stmtExpression = (MemberAccessNode) stmt.getInnerStmtNode();
    IdNode enumNameIdNode = (IdNode) stmtExpression.getLhs();
    Symbol enumNameIdSymbol = symbolTable.getSymbolsForAstNode(enumNameIdNode).get(0);
    Assert.assertEquals(myEnumType, enumNameIdSymbol);

    // check, that the `A` in return stmt is bound to enum variant
    IdNode enumVariantIdNode = (IdNode) stmtExpression.getRhs();
    Symbol enumVariantIdSymbol = symbolTable.getSymbolsForAstNode(enumVariantIdNode).get(0);
    Symbol enumVariantSymbolExpected = ((EnumType) myEnumType).resolve("A");
    Assert.assertEquals(enumVariantSymbolExpected, enumVariantIdSymbol);
  }

  @Test
  public void testEnumVariantBindingIllegalAccess() {
    String program =
        """
    fn callback(entity ent) -> my_enum {
        return my_enum.A.B;
    }
    """;

    // print currently just prints to system.out, so we need to
    // check the contents for the printed string
    var outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), Entity.class);
    env.typeBuilder()
        .createDSLTypeForJavaTypeInScope(
            env.getGlobalScope(), TestComponentWithStringConsumerCallback.class);

    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), MyEnum.class);

    var ast = Helpers.getASTFromString(program);
    try {
      var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
      Assert.fail("Should throw");
    } catch (RuntimeException ex) {
      Assert.assertEquals(ex.getMessage(), "Member access on enum value is not allowed: my_enum.A");
    }
  }

  @Test
  public void testEnumVariantBindingIllegalAccessVariable() {
    String program =
        """
    fn callback(entity ent) -> my_enum {
        var variable :  my_enum;
        variable = my_enum.A;
        var other_variable : my_enum;
        other_variable = variable.B;
    }
    """;

    // print currently just prints to system.out, so we need to
    // check the contents for the printed string
    var outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));

    TestEnvironment env = new TestEnvironment();
    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), Entity.class);
    env.typeBuilder()
        .createDSLTypeForJavaTypeInScope(
            env.getGlobalScope(), TestComponentWithStringConsumerCallback.class);

    env.typeBuilder().createDSLTypeForJavaTypeInScope(env.getGlobalScope(), MyEnum.class);

    var ast = Helpers.getASTFromString(program);
    try {
      // the member access operation `variable.B` should throw an exception, because it is not
      // allowed
      var result = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
      Assert.fail("Should throw");
    } catch (RuntimeException ex) {
      Assert.assertEquals(ex.getMessage(), "Member access on enum value is not allowed: variable");
    }
  }

  @Test
  public void testTaskReferenceInGraph() {
    String program =
        """
        single_choice_task t1 {
            description: "Hello",
            answers: ["1", "2", "3"],
            correct_answer_index: 1
        }

        multiple_choice_task t2 {
            description: "Tschüss",
            answers: ["4", "5", "6"],
            correct_answer_indices: [0,1]
        }

        graph g {
            t1 -> t2
        }
        """;

    // setup
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    TypeBuilder tb = new TypeBuilder(new TypeFactory());
    Scope scope = new Scope();
    var testComponentType = tb.createDSLTypeForJavaTypeInScope(scope, TestComponent.class);

    var env = new GameEnvironment();
    env.loadTypes(testComponentType);
    symbolTableParser.setup(env);

    var ast = Helpers.getASTFromString(program, env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);

    Symbol t1TaskSymbol = fileScope.resolve("t1");
    Symbol t2TaskSymbol = fileScope.resolve("t2");

    DotDefNode dotDefNode = (DotDefNode) ast.getChild(2);

    DotEdgeStmtNode stmtNode = (DotEdgeStmtNode) dotDefNode.getStmtNodes().get(0);

    IdNode t1Reference = stmtNode.getIdLists().get(0).getIdNodes().get(0);
    var t1Symbol = symbolTable.getSymbolsForAstNode(t1Reference).get(0);
    Assert.assertNotEquals(Symbol.NULL, t1Symbol);
    Assert.assertEquals(t1TaskSymbol, t1Symbol);

    IdNode t2Reference = stmtNode.getIdLists().get(1).getIdNodes().get(0);
    var t2Symbol = symbolTable.getSymbolsForAstNode(t2Reference).get(0);
    Assert.assertNotEquals(Symbol.NULL, t2Symbol);
    Assert.assertEquals(t2TaskSymbol, t2Symbol);
  }

  @Test
  public void testTaskReferenceInGraphForwardReference() {
    String program =
        """
        graph g {
            t1 -> t2
        }

        single_choice_task t1 {
            description: "Hello",
            answers: ["1", "2", "3"],
            correct_answer_index: 1
        }

        multiple_choice_task t2 {
            description: "Tschüss",
            answers: ["4", "5", "6"],
            correct_answer_indices: [0,1]
        }

        """;

    // setup
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    TypeBuilder tb = new TypeBuilder(new TypeFactory());
    Scope scope = new Scope();
    var testComponentType = tb.createDSLTypeForJavaTypeInScope(scope, TestComponent.class);

    var env = new GameEnvironment();
    env.loadTypes(testComponentType);
    symbolTableParser.setup(env);

    var ast = Helpers.getASTFromString(program, env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);

    Symbol t1TaskSymbol = fileScope.resolve("t1");
    Symbol t2TaskSymbol = fileScope.resolve("t2");

    DotDefNode dotDefNode = (DotDefNode) ast.getChild(0);

    DotEdgeStmtNode stmtNode = (DotEdgeStmtNode) dotDefNode.getStmtNodes().get(0);

    IdNode t1Reference = stmtNode.getIdLists().get(0).getIdNodes().get(0);
    var t1Symbol = symbolTable.getSymbolsForAstNode(t1Reference).get(0);
    Assert.assertNotEquals(Symbol.NULL, t1Symbol);
    Assert.assertEquals(t1TaskSymbol, t1Symbol);

    IdNode t2Reference = stmtNode.getIdLists().get(1).getIdNodes().get(0);
    var t2Symbol = symbolTable.getSymbolsForAstNode(t2Reference).get(0);
    Assert.assertNotEquals(Symbol.NULL, t2Symbol);
    Assert.assertEquals(t2TaskSymbol, t2Symbol);
  }

  @Test
  public void testForLoopVariableBinding() {
    String program =
        """
    fn test() {
        var my_list : int[];
        for int entry in my_list {
            print(entry);
        }
    }

    """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    var env = new GameEnvironment();
    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);

    // get variable declaration
    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("test");
    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);
    VarDeclNode varDeclNode = (VarDeclNode) funcDefNode.getStmtBlock().getChild(0);
    Symbol myListSymbol = symbolTable.getSymbolsForAstNode(varDeclNode).get(0);

    // get iterableIdNode and check, that it references the variable symbol
    ForLoopStmtNode loopNode = (ForLoopStmtNode) funcDefNode.getStmtBlock().getChild(1);
    Node iterableIdNode = loopNode.getIterableIdNode();
    Symbol iterableIdNodeRefSymbol = symbolTable.getSymbolsForAstNode(iterableIdNode).get(0);
    Assert.assertEquals(myListSymbol, iterableIdNodeRefSymbol);

    // get the loop variable id node and the linked symbol
    Node loopVariableIdNode = loopNode.getVarIdNode();
    Symbol loopVariableSymbol = symbolTable.getSymbolsForAstNode(loopVariableIdNode).get(0);
    Assert.assertNotEquals(Symbol.NULL, loopVariableSymbol);

    // get the print call and check, that the parameter references the loop variable symbol
    FuncCallNode printCallNode = (FuncCallNode) loopNode.getStmtNode().getChild(0);
    IdNode parameterIdNode = (IdNode) printCallNode.getParameters().get(0);
    Symbol parameterSymbol = symbolTable.getSymbolsForAstNode(parameterIdNode).get(0);
    Assert.assertEquals(loopVariableSymbol, parameterSymbol);
  }

  @Test
  public void testCountingLoopVariableBinding() {
    String program =
        """
    fn test() {
        var my_list : int[];
        for int entry in my_list count i{
            print(entry);
            print(i);
        }
    }

    """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    var env = new GameEnvironment();
    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);

    // get variable declaration
    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("test");
    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);
    VarDeclNode varDeclNode = (VarDeclNode) funcDefNode.getStmtBlock().getChild(0);
    Symbol myListSymbol = symbolTable.getSymbolsForAstNode(varDeclNode).get(0);

    // get iterableIdNode and check, that it references the variable symbol
    CountingLoopStmtNode loopNode = (CountingLoopStmtNode) funcDefNode.getStmtBlock().getChild(1);
    Node iterableIdNode = loopNode.getIterableIdNode();
    Symbol iterableIdNodeRefSymbol = symbolTable.getSymbolsForAstNode(iterableIdNode).get(0);
    Assert.assertEquals(myListSymbol, iterableIdNodeRefSymbol);

    // get the loop variable id node and the linked symbol
    Node loopVariableIdNode = loopNode.getVarIdNode();
    Symbol loopVariableSymbol = symbolTable.getSymbolsForAstNode(loopVariableIdNode).get(0);
    Assert.assertNotEquals(Symbol.NULL, loopVariableSymbol);

    // get the print call and check, that the parameter references the loop variable symbol
    FuncCallNode printCallNode = (FuncCallNode) loopNode.getStmtNode().getChild(0);
    IdNode parameterIdNode = (IdNode) printCallNode.getParameters().get(0);
    Symbol parameterSymbol = symbolTable.getSymbolsForAstNode(parameterIdNode).get(0);
    Assert.assertEquals(loopVariableSymbol, parameterSymbol);

    // get the loop variable id node and the linked symbol
    Node counterVariableIdNode = loopNode.getCounterIdNode();
    Symbol counterVariableSymbol = symbolTable.getSymbolsForAstNode(counterVariableIdNode).get(0);
    Assert.assertNotEquals(Symbol.NULL, counterVariableSymbol);

    // get the second print call and check, that the parameter references the counter variable
    // symbol
    FuncCallNode secondPrintCall = (FuncCallNode) loopNode.getStmtNode().getChild(1);
    IdNode secondPrintParameterIdNode = (IdNode) secondPrintCall.getParameters().get(0);
    Symbol secondPrintParameterSymbol =
        symbolTable.getSymbolsForAstNode(secondPrintParameterIdNode).get(0);
    Assert.assertEquals(counterVariableSymbol, secondPrintParameterSymbol);
  }

  @Test
  public void testWhileLoop() {
    String program =
        """
    fn test() {
        var my_list : int[];
        while my_list {
            print(my_list);
        }
    }

    """;

    // setup
    var ast = Helpers.getASTFromString(program);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    var env = new GameEnvironment();
    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);

    // get variable declaration
    FunctionSymbol funcSymbol = (FunctionSymbol) fileScope.resolve("test");
    FuncDefNode funcDefNode = (FuncDefNode) symbolTable.getCreationAstNode(funcSymbol);
    VarDeclNode varDeclNode = (VarDeclNode) funcDefNode.getStmtBlock().getChild(0);
    Symbol myListSymbol = symbolTable.getSymbolsForAstNode(varDeclNode).get(0);

    // get iterableIdNode and check, that it references the variable symbol
    WhileLoopStmtNode loopNode = (WhileLoopStmtNode) funcDefNode.getStmtBlock().getChild(1);
    Node expressionIdNode = loopNode.getExpressionNode();
    Symbol expressionRefNode = symbolTable.getSymbolsForAstNode(expressionIdNode).get(0);
    Assert.assertEquals(myListSymbol, expressionRefNode);
  }

  @Test
  public void testImportFunc() {
    String program = """
        #import "test.dng":test_fn_param as my_func
        """;

    // setup
    var env = new GameEnvironment(testLibPath);
    var ast = Helpers.getASTFromString(program, env);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);
    Symbol myFuncSymbol = fileScope.resolve("my_func");
    Assert.assertNotEquals(Symbol.NULL, myFuncSymbol);

    ImportFunctionSymbol importFunctionSymbol = (ImportFunctionSymbol) myFuncSymbol;
    FunctionSymbol originalFunctionSymbol = importFunctionSymbol.originalFunctionSymbol();
    Assert.assertEquals("test_fn_param", originalFunctionSymbol.getName());
    Assert.assertNotEquals(importFunctionSymbol.getScope(), originalFunctionSymbol.getScope());

    // test parameter resolving
    Symbol paramSymbol = importFunctionSymbol.resolve("param");
    Assert.assertEquals(originalFunctionSymbol, paramSymbol.getScope());
  }

  @Test
  public void testImportFuncBroken() {
    String program =
        """
        #import "test.dng"; id as my_func // ';' instead of ':'

        single_choice_task t1 {
          description:"hello",
          correct_answer_index: 1,
          answers: [1,2,3]
        }

        graph g {
          t1
        }

        dungeon_config c {
          dependency_graph: g
        }
        """;

    // setup
    var env = new GameEnvironment(testLibPath);
    System.out.println(Helpers.getPrettyPrintedParseTree(program, env));

    var ast = Helpers.getASTFromString(program, env);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);
    Symbol myFuncSymbol = fileScope.resolve("my_func");
  }

  @Test
  public void testImportType() {
    String program = """
            #import "test.dng":my_ent_type as my_type
            """;

    // setup
    var env = new GameEnvironment(testLibPath);
    var ast = Helpers.getASTFromString(program, env);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    symbolTableParser.setup(env);
    var symbolTable = symbolTableParser.walk(ast).symbolTable;
    var fileScope = env.getFileScope(null);
    Symbol myTypeSymbol = fileScope.resolve("my_type");
    Assert.assertNotEquals(Symbol.NULL, myTypeSymbol);

    ImportAggregateTypeSymbol importSymbol = (ImportAggregateTypeSymbol) myTypeSymbol;
    AggregateType originalType = importSymbol.originalTypeSymbol();
    Assert.assertEquals("my_ent_type", originalType.getName());
    Assert.assertNotEquals(fileScope, originalType.getScope());

    // resolve
    Symbol member = originalType.resolve("interaction_component");
    Assert.assertEquals(originalType, member.getScope());
  }

  @Test
  public void testBlockImportImportedType() {
    String program = """
        #import "test.dng":my_imported_type as my_type
        """;

    // setup
    var env = new GameEnvironment(testLibPath);
    var ast = Helpers.getASTFromString(program, env);
    SemanticAnalyzer symbolTableParser = new SemanticAnalyzer();

    symbolTableParser.setup(env);
    try {
      var symbolTable = symbolTableParser.walk(ast).symbolTable;
      Assert.fail("Semantic analysis is supposed to throw an exception!");
    } catch (RuntimeException ex) {
      Assert.assertEquals("Importing an imported symbol is not allowed!", ex.getMessage());
    }
  }

  @Test
  public void funcDefBrokenCompletely() {
    String program =
        """
          fn test_func_1(int param1, ,) ->  {
              print(param1);
          }

          fn test_func_2(int param4) -> int {
              print(param4);
          }
          """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcSymbol1 = fileScope.resolve("test_func_1");
    Assert.assertNotEquals(Symbol.NULL, funcSymbol1);
    var funcSymbol2 = (FunctionSymbol) fileScope.resolve("test_func_2");
    Assert.assertEquals("$fn(int) -> int$", funcSymbol2.getDataType().getName());

    // print call should be linked to print function

    FuncCallNode funcCall = (FuncCallNode) ((FuncDefNode) ast.getChild(0)).getStmts().get(0);
    var printFuncSymbol = symtableResult.symbolTable.getSymbolsForAstNode(funcCall).get(0);
    Assert.assertEquals(NativePrint.func, printFuncSymbol);
  }

  @Test
  public void funcDefPartlyBrokenStmtBlock() {
    String program =
        """
          fn test_func_1(int param1) -> int {
            var x = 1+;;;;
            print(param1);
          }
          """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    // print call should be linked to print function
    var stmts = ((FuncDefNode) ast.getChild(0)).getStmts();
    var funcCallNode = (FuncCallNode) stmts.get(5);

    var printFuncSymbol = symtableResult.symbolTable.getSymbolsForAstNode(funcCallNode).get(0);
    Assert.assertEquals(NativePrint.func, printFuncSymbol);
  }

  @Test
  public void funcDefBrokenParamDef() {
    String program =
        """
          fn test_func_1(int ) -> int {
              print(param1);
          }

          fn test_func_2(int param4) -> int {
              print(param4);
          }
          """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var fileScope = env.getFileScope(null);

    var funcSymbol1 = (FunctionSymbol) fileScope.resolve("test_func_1");
    var funcSymbol2 = (FunctionSymbol) fileScope.resolve("test_func_2");

    // just ignore the erroneous parameter definition
    Assert.assertEquals("$fn() -> int$", funcSymbol1.getDataType().getName());
    Assert.assertEquals("$fn(int) -> int$", funcSymbol2.getDataType().getName());
  }

  @Test
  public void incompleteMemberAccess() {
    String program =
        """
        fn test(entity ent, int x) {
            if x {
                ent.
            }
        }
        """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = symtableResult.symbolTable;

    var funcDef = (FuncDefNode) ast.getChild(0);
    var paramDef = funcDef.getParameters().get(0).getChild(1);
    var paramSymbol = symbolTable.getSymbolsForAstNode(paramDef).get(0);

    var stmt = (ConditionalStmtNodeIf) funcDef.getStmts().get(0);
    var incompleteMemberAccess = stmt.getIfStmt().getChild(0);
    var idNode = (IdNode) incompleteMemberAccess.getChild(0).getChild(0);
    Assert.assertEquals("ent", idNode.getName());
    var symbol = symtableResult.symbolTable.getSymbolsForAstNode(idNode).get(0);
    Assert.assertEquals(paramSymbol, symbol);
  }

  @Test
  public void incompleteVarDeclExpr() {
    String program = """
    fn test(entity ent, int x) {
        var y =
    }
    """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = symtableResult.symbolTable;

    var funcDef = (FuncDefNode) ast.getChild(0);
    var stmt = (VarDeclNode)funcDef.getStmts().get(0);
    var symbol = symbolTable.getSymbolsForAstNode(stmt).get(0);
    Assert.assertNotEquals(Symbol.NULL, symbol);
  }

  @Test
  public void incompleteVarDeclType() {
    String program = """
      fn test(entity ent, int x) {
          var y :
      }
      """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = symtableResult.symbolTable;
    var fileScope = env.getFileScope(null);

    var funcDef = (FuncDefNode) ast.getChild(0);
    var stmt = (VarDeclNode)funcDef.getStmts().get(0);
    var symbol = symbolTable.getSymbolsForAstNode(stmt).get(0);
    Assert.assertNotEquals(Symbol.NULL, symbol);
    Assert.assertEquals(BuiltInType.noType, symbol.getDataType());
  }

  @Test
  public void incompleteAssignment() {
    String program =
        """
      fn test(entity ent, int x) {
          var y = 42;
          y =
      }
      """;

    var env = new GameEnvironment();
    var ast = Helpers.getASTFromString(program, env);
    var symtableResult = Helpers.getSymtableForASTWithCustomEnvironment(ast, env);
    var symbolTable = symtableResult.symbolTable;

    var funcDef = (FuncDefNode) ast.getChild(0);
    var decl = (VarDeclNode)funcDef.getStmts().get(0);
    var declSymbol = symbolTable.getSymbolsForAstNode(decl).get(0);
    Assert.assertNotEquals(Symbol.NULL, declSymbol);

    var assignStmt = funcDef.getStmts().get(1);
    var assignment = (AssignmentNode)assignStmt.getChild(0);
    var idNode = assignment.getChild(0);
    var assignedSymbol = symbolTable.getSymbolsForAstNode(idNode).get(0);
    Assert.assertEquals(declSymbol, assignedSymbol);
  }
}
