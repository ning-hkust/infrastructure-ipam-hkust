package hk.ust.cse.AbstractInterpretation.Lattices;

import java.util.Hashtable;

public abstract class AbstractLattice {
  
  public static LatticeNode func2D(LatticeNode op1, LatticeNode op2, String func) {
    int[][] func_table = (int[][]) s_tables.get(func);
    
    LatticeNode ret = null;
    if (func_table != null) {
      ret = new LatticeNode(func_table[op1.getNode()][op2.getNode()]);
    }
    return ret;
  }
  
  // get the lowest upper bound
  public static LatticeNode join(LatticeNode node1, LatticeNode node2) {
    int[][] join_table = (int[][]) s_tables.get("join");
    
    LatticeNode ret = null;
    if (join_table != null) {
      ret = new LatticeNode(join_table[node1.getNode()][node2.getNode()]);
    }
    return ret;
  }
  
  // get the greatest lower bound
  public static LatticeNode meet(LatticeNode node1, LatticeNode node2) {
    int[][] meet_table = (int[][]) s_tables.get("meet");
    
    LatticeNode ret = null;
    if (meet_table != null) {
      ret = new LatticeNode(meet_table[node1.getNode()][node2.getNode()]);
    }
    return ret;
  }
  
  public static Boolean leq(LatticeNode node1, LatticeNode node2) {
    boolean[][] leq_table = (boolean[][]) s_tables.get("leq");
    
    Boolean ret = null;
    if (leq_table != null) {
      ret = leq_table[node1.getNode()][node2.getNode()];
    }
    return ret;
  }
  
  public static boolean hasProperty(LatticeNode node) {
    return node.getNode() == s_desired_property;
  }
  
  protected static int s_desired_property = Integer.MAX_VALUE;
  protected static final Hashtable<String, Object> s_tables = new Hashtable<String, Object>();
}
