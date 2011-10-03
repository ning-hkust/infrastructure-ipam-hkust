package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.Hashtable;

public class TypeConditionTerm extends ConditionTerm {
  public enum Comparator {
    OP_SUBTYPEOF, 
    OP_NOT_SUBTYPEOF,
    OP_INSTANCEOF, 
    OP_NOT_INSTANCEOF, 
    OP_INSTANCEOF_OR_SUBTYPEOF, 
    OP_NOT_INSTANCEOF_AND_NOT_SUBTYPEOF}
  
  public TypeConditionTerm(Instance instance1, Comparator op, String typeString) {
    m_instance1  = instance1;
    m_typeString = typeString;
    m_op         = op;
  }

  public Instance getInstance1() {
    return m_instance1;
  }
  
  public String getTypeString() {
    return m_typeString;
  }
  
  public Instance[] getInstances() {
    return new Instance[] {m_instance1};
  }

  public Comparator getComparator() {
    return m_op;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();

    str.append(m_instance1.toString());
    switch (m_op) {
    case OP_SUBTYPEOF:
      str.append(" subtypeof ");
      break;
    case OP_NOT_SUBTYPEOF:
      str.append(" notsubtypeof ");
      break;
    case OP_INSTANCEOF:
      str.append(" instanceof ");
      break;
    case OP_NOT_INSTANCEOF:
      str.append(" notinstanceof ");
      break;
    case OP_INSTANCEOF_OR_SUBTYPEOF:
      str.append(" instanceof_or_subtypeof ");
      break;
    case OP_NOT_INSTANCEOF_AND_NOT_SUBTYPEOF:
        str.append(" not_instanceof_and_not_subtypeof ");
        break;
    default:
      str.append(" ? ");
      break;
    }
    str.append(m_typeString);

    return str.toString();
  }
  
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    
    if (!(o instanceof TypeConditionTerm)) {
      return false;
    }
    
    TypeConditionTerm term = (TypeConditionTerm) o;
    return (m_instance1.equals(term.getInstance1()) && 
            m_typeString.equals(term.getTypeString()) && 
            m_op.equals(term.getComparator()));
  }
  
  public TypeConditionTerm deepClone(Hashtable<Object, Object> cloneMap) {
    Instance clone1 = m_instance1.deepClone(cloneMap);
    return new TypeConditionTerm(clone1, m_op, m_typeString);
  }
  
  private final Instance    m_instance1;
  private final String      m_typeString;
  private final Comparator m_op;
}
