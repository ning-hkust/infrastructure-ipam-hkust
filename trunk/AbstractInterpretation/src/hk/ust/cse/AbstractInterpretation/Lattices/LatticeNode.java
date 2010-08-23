package hk.ust.cse.AbstractInterpretation.Lattices;

public class LatticeNode {
  public LatticeNode(int node) {
    m_node = node;
  }
  
  public int getNode() {
    return m_node;
  }
  
  private final int m_node;
}
