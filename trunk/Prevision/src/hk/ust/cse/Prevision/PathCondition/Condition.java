package hk.ust.cse.Prevision.PathCondition;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Condition {
  public Condition(List<ConditionTerm> terms) {
    m_terms = terms;
  }

  public List<ConditionTerm> getConditionTerms() {
    return m_terms;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("(");
    for (int i = 0, size = m_terms.size(); i < size; i++) {
      str.append("(");
      str.append(m_terms.get(i).toString());
      str.append(")");
      if (i != size - 1) {
        str.append(" or ");
      }
    }
    str.append(")");
    return str.toString();
  }
  
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    
    if (!(o instanceof Condition)) {
      return false;
    }
    
    return m_terms.equals(((Condition)o).getConditionTerms());
  }
  
  public Condition deepClone(Hashtable<Object, Object> cloneMap) {
    List<ConditionTerm> cloneTerms = new ArrayList<ConditionTerm>();
    for (ConditionTerm term : m_terms) {
      cloneTerms.add(term.deepClone(cloneMap));
    }
    return new Condition(cloneTerms);
  }

  private final List<ConditionTerm> m_terms;
}