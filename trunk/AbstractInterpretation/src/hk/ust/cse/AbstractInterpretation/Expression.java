package hk.ust.cse.AbstractInterpretation;

import hk.ust.cse.AbstractInterpretation.Lattices.LatticeNode;

import java.util.Arrays;

public class Expression {
  public enum ExprType {CONSTANT, VARIABLE, EXPRESSION, LET}
  
  public Expression(LatticeNode latticeNode) {
    // e -> n
    m_object      = latticeNode;
    m_function    = null;
    m_expressions = null;
    m_exprType    = ExprType.CONSTANT;
  }
  
  public Expression(String varName) {
    // e -> v
    m_object      = varName;
    m_function    = null;
    m_expressions = null;
    m_exprType    = ExprType.VARIABLE;
  }
  
  public Expression(Expression[] exprs, String function) {
    // e -> e1 func e2
    m_object      = null;
    m_function    = function;
    m_expressions = Arrays.copyOf(exprs, exprs.length);
    m_exprType    = ExprType.EXPRESSION;
  }
  
  public Expression(String varName, Expression eqExpr, Expression inExpr) {
    // e -> LET v = e1 IN e2
    m_object      = varName;
    m_function    = null;
    m_expressions = new Expression[] {eqExpr, inExpr};
    m_exprType    = ExprType.LET;
  }
  
  @SuppressWarnings("static-access")
  public LatticeNode eval(Environment env) {
    if (m_evalResult != null) {
      return m_evalResult;
    }
    
    switch (m_exprType) {
    case CONSTANT: // eval(n, env) = env(n) = n
      m_evalResult = (LatticeNode) m_object;
      break;
    case VARIABLE: // eval(v, env) = env(v)
      Expression expr = env.getExpression((String) m_object); // it should always be found
      m_evalResult = expr.eval(env);
      break;
    case EXPRESSION: // eval(e1 func e2, env) = eval(e1, env) func eval(e2, env)
      LatticeNode[] nodes = new LatticeNode[m_expressions.length];
      for (int i = 0; i < m_expressions.length; i++) {
        nodes[i] = m_expressions[i].eval(env);
      }
      
      // invoke function
      switch (nodes.length) {
      case 2:
        m_evalResult = env.getLattice().func2D(nodes[0], nodes[1], m_function);
        break;
      default:
        break;
      }
      break;  
    case LET: // eval(LET v = e1 IN e2, env) = eval(e2, env{v |-> eval(e1, env)})
      String varName    = (String) m_object;
      Expression eqExpr = m_expressions[0];
      Expression inExpr = m_expressions[1];

      // save original expr
      Expression oriExpr = env.getExpression(varName);
      // get eval for eqExpr
      LatticeNode evalRet = eqExpr.eval(env);
      // create the new environment
      env.putExpression(varName, new Expression(evalRet));
      // eval inExpr in the new environment
      m_evalResult = inExpr.eval(env);
      // recover environment
      env.putExpression(varName, oriExpr);
      break;
    default:
      break;
    }
    
    return m_evalResult;
  }
  
  private final Object       m_object;
  private final String       m_function;
  private final Expression[] m_expressions;
  private final ExprType    m_exprType;
  
  private LatticeNode m_evalResult;
}
