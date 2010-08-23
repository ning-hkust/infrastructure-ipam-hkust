package hk.ust.cse.AbstractInterpretation;

import hk.ust.cse.AbstractInterpretation.Lattices.AbstractLattice;
import hk.ust.cse.AbstractInterpretation.Lattices.LatticeNode;

import java.util.Enumeration;
import java.util.Hashtable;


public class Environment {

  public Environment(AbstractLattice lattice) {
    // which lattice we are working on
    m_lattice = lattice;
    
    // begin from an empty environment
    m_environment = new Hashtable<String, Expression>();
  }
  
  private Environment(AbstractLattice lattice, Hashtable<String, Expression> env) {
    m_lattice     = lattice;
    m_environment = env;
  }
  
  public void putExpression(String varName, Expression expr) {
    m_environment.put(varName, expr);
  }
  
  public Expression getExpression(String varName) {
    return m_environment.get(varName);
  }
  
  public void removeVariable(String varName) {
    m_environment.remove(varName);
  }
  
  @SuppressWarnings("static-access")
  public static Environment merge(Environment env1, Environment env2) {
    Environment newEnv = env1.clone();
    
    Enumeration<String> allVars = env2.getAllVariables();
    while (allVars.hasMoreElements()) {
      String var = (String) allVars.nextElement();
      
      // get the two expressions for the same variable
      Expression expr1 = env1.getExpression(var);
      Expression expr2 = env2.getExpression(var);
      
      // merge
      if (expr1 == null) {
        expr1 = expr2;
      }
      else if (expr2 != null) {
        LatticeNode node1 = expr1.eval(env1);
        LatticeNode node2 = expr2.eval(env2);
        LatticeNode joinedNode = env1.getLattice().join(node1, node2);
        expr1 = new Expression(joinedNode);
      }
      
      // put back;
      if (expr1 != null) {
        newEnv.putExpression(var, expr1);
      }
    }
    return newEnv;
  }
  
  public Enumeration<String> getAllVariables() {
    return m_environment.keys();
  }
  
  public AbstractLattice getLattice() {
    return m_lattice;
  }
  
  @SuppressWarnings("unchecked")
  public Environment clone() {
    // a shallow clone is enough, because String and Expression are immutable
    Hashtable<String, Expression> env = (Hashtable<String, Expression>) m_environment.clone();
    return new Environment(m_lattice, env);
  }
  
  private final AbstractLattice               m_lattice;
  private final Hashtable<String, Expression> m_environment;
}
