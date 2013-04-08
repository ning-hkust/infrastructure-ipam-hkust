package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

// very special condition term to support condition of the type: (a and b) or (x and y)
// it should only be used in very special situations such as find more model values.
// usually we do not need to consider this type when parsing a list of conditions
public class AndConditionTerm extends ConditionTerm {

  public AndConditionTerm(List<ConditionTerm> andConditionTerms) {
    m_andConditionTerms = andConditionTerms;
  }

  public Instance[] getInstances() {
    List<Instance> instances = new ArrayList<Instance>();
    for (ConditionTerm term : m_andConditionTerms) {
      instances.addAll(Arrays.asList(term.getInstances()));
    }
    return instances.toArray(new Instance[instances.size()]);
  }
  
  public List<ConditionTerm> getAndConditionTerms() {
    return m_andConditionTerms;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();

    for (int i = 0, size = m_andConditionTerms.size(); i < size; i++) {
      str.append(m_andConditionTerms.get(i).toString());
      if (i != size - 1) {
        str.append(" and ");
      }
    }

    return str.toString();
  }
  
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    
    if (!(o instanceof AndConditionTerm)) {
      return false;
    }
    
    AndConditionTerm andTerm = (AndConditionTerm) o;
    return (m_andConditionTerms.equals(andTerm.getAndConditionTerms()));
  }
  
  public AndConditionTerm deepClone(Hashtable<Object, Object> cloneMap) {
    List<ConditionTerm> cloneList = new ArrayList<ConditionTerm>();
    for (ConditionTerm andConditionTerm : m_andConditionTerms) {
      cloneList.add(andConditionTerm.deepClone(cloneMap));
    }
    return new AndConditionTerm(cloneList);
  }
  
  private final List<ConditionTerm> m_andConditionTerms;
}
