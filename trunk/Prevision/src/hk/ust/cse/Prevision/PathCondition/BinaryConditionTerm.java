package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.Hashtable;

public class BinaryConditionTerm extends ConditionTerm {
  public enum Comparator {
    OP_EQUAL(0), 
    OP_INEQUAL(1), 
    OP_GREATER(2), 
    OP_GREATER_EQUAL(3), 
    OP_SMALLER(4), 
    OP_SMALLER_EQUAL(5);
  
    Comparator(int index) {
      m_index = index;
    }
    
    public int toIndex() {
      return m_index;
    }
    
    public static Comparator fromIndex(int index) {
      switch (index) {
      case 0:
        return OP_EQUAL;
      case 1:
        return OP_INEQUAL;
      case 2:
        return OP_GREATER;
      case 3:
        return OP_GREATER_EQUAL;
      case 4:
        return OP_SMALLER;
      case 5:
        return OP_SMALLER_EQUAL;
      default:
        return null;
      }
    }
    
    public Comparator getOpposite() {
      switch (this) {
      case OP_EQUAL:
        return OP_INEQUAL;
      case OP_GREATER:
        return OP_SMALLER_EQUAL;
      case OP_GREATER_EQUAL:
        return OP_SMALLER;
      case OP_INEQUAL:
        return OP_EQUAL;
      case OP_SMALLER:
        return OP_GREATER_EQUAL;
      case OP_SMALLER_EQUAL:
        return OP_GREATER;
      default:
        return null;
      }
    }
    
    public static Comparator fromString(String str) {
      switch (str) {
      case "==":
        return OP_EQUAL;
      case "!=":
        return OP_INEQUAL;
      case ">":
        return OP_GREATER;
      case ">=":
        return OP_GREATER_EQUAL;
      case "<":
        return OP_SMALLER;
      case "<=":
        return OP_SMALLER_EQUAL;
      default:
        return null;
      }
    }
    
    public String toString() {
      switch (m_index) {
      case 0:
        return "==";
      case 1:
        return "!=";
      case 2:
        return ">";
      case 3:
        return ">=";
      case 4:
        return "<";
      case 5:
        return "<=";
      default:
        return null;
      }
    }
    
    private final int m_index;
  }
  
  public BinaryConditionTerm(Instance instance1, Comparator op, Instance instance2) {
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
  
  public Instance[] getInstances() {
    return new Instance[] {m_instance1, m_instance2};
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
    
    if (!(o instanceof BinaryConditionTerm)) {
      return false;
    }
    
    BinaryConditionTerm term = (BinaryConditionTerm) o;
    return (m_instance1.equals(term.getInstance1()) && 
            m_instance2.equals(term.getInstance2()) && 
            m_op.equals(term.getComparator()));
  }
  
  public BinaryConditionTerm deepClone(Hashtable<Object, Object> cloneMap) {
    Instance clone1 = m_instance1.deepClone(cloneMap);
    Instance clone2 = m_instance2.deepClone(cloneMap);
    return new BinaryConditionTerm(clone1, m_op, clone2);
  }
  
  public ConditionTerm replaceInstances(Hashtable<Instance, Instance> replaceMap) {
    Instance instance1 = m_instance1.replaceInstances(replaceMap);
    Instance instance2 = m_instance2.replaceInstances(replaceMap);
    if (instance1 != m_instance1 || instance2 != m_instance2) {
      return new BinaryConditionTerm(instance1, m_op, instance2);
    }
    else {
      return this;
    }
  }
  
  public boolean isNotEqualToNull() {
    return m_op == Comparator.OP_INEQUAL && m_instance2.isNullConstant();
  }
  
  public boolean isEqualToNull() {
    return m_op == Comparator.OP_EQUAL && m_instance2.isNullConstant();
  }
  
  private final Instance    m_instance1;
  private final Instance    m_instance2;
  private final Comparator m_op;
}
