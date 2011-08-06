package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.Hashtable;

public class ConditionTerm {
  public enum Comparator {
    OP_EQUAL, 
    OP_INEQUAL, 
    OP_GREATER, 
    OP_GREATER_EQUAL, 
    OP_SMALLER, 
    OP_SMALLER_EQUAL}
  
  public ConditionTerm(Instance instance1, Comparator op, Instance instance2) {
    m_instance1 = instance1;
    m_instance2 = instance2;
    m_op        = op;
  }

  public Instance getInstance1() {
    return m_instance1;
  }
  
  public Instance getInstance2() {
    return m_instance2;
  }

  public Comparator getComparator() {
    return m_op;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();

    str.append(m_instance1.toString());
    switch (m_op) {
    case OP_EQUAL:
      str.append(" == ");
      break;
    case OP_INEQUAL:
      str.append(" != ");
      break;
    case OP_GREATER:
      str.append(" > ");
      break;
    case OP_GREATER_EQUAL:
      str.append(" >= ");
      break;
    case OP_SMALLER:
      str.append(" < ");
      break;
    case OP_SMALLER_EQUAL:
      str.append(" <= ");
      break;
    default:
      str.append(" ? ");
      break;
    }
    str.append(m_instance2.toString());

    return str.toString();
  }
  
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    
    if (!(o instanceof ConditionTerm)) {
      return false;
    }
    
    ConditionTerm term = (ConditionTerm)o;
    return (m_instance1.equals(term.getInstance1()) && 
            m_instance2.equals(term.getInstance2()) && 
            m_op.equals(term.getComparator()));
  }
  
  public ConditionTerm deepClone(Hashtable<Object, Object> cloneMap) {
    Instance clone1 = (Instance) cloneMap.get(m_instance1);
    Instance clone2 = (Instance) cloneMap.get(m_instance2);
    if (clone1 == null) {
      clone1 = m_instance1.deepClone(cloneMap);
      cloneMap.put(m_instance1, clone1);
    }
    if (clone2 == null) {
      clone2 = m_instance2.deepClone(cloneMap);
      cloneMap.put(m_instance2, clone2);
    }
    return new ConditionTerm(clone1, m_op, clone2);
  }
  
  private Instance    m_instance1;
  private Instance    m_instance2;
  private final Comparator m_op;
}
