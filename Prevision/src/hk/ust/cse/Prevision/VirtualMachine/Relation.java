package hk.ust.cse.Prevision.VirtualMachine;


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;

/**
 * arrays and fields are handled as relations manipulated by 
 * functions (read, update) from the array theory
 */
public class Relation {
  public Relation(String name, int domainDimension, boolean forward, String[] domainTypes, String rangeType) {
    m_name            = name;
    m_domainDimension = domainDimension; // should be one for field (parent) and two for array (base and index)
    m_forward         = forward; // direction of the symbolic execution
    
    // to save relational updates
    m_domainTypes   = domainTypes;
    m_rangeType     = rangeType;
    m_domainValues  = new ArrayList<Instance[]>();
    m_rangeValues   = new ArrayList<Instance>();
    m_functionTimes = new ArrayList<Long>();
  }
  
  public void update(Instance[] domainValues, Instance rangeValue) {
    if ((isArrayRelation() && domainValues.length != 2) || (isFieldRelation() && domainValues.length != 1)) {
      throw new IllegalArgumentException("The number of domain values is not correct.");
    }
    
    // if it is backward execution, put the latest operation at front
    m_domainValues.add(m_forward ? m_domainValues.size() : 0, domainValues);
    m_rangeValues.add(m_forward ? m_rangeValues.size() : 0, rangeValue);
    m_functionTimes.add(m_forward ? m_functionTimes.size() : 0, System.nanoTime());
  }
  
  public Reference read(Instance[] domainValues, String type, ISSABasicBlock createBlock) {
    if ((isArrayRelation() && domainValues.length != 2) || (isFieldRelation() && domainValues.length != 1)) {
      throw new IllegalArgumentException("The number of domain values is not correct.");
    }
    
    // record the read relation
    long currentTime = System.nanoTime();

    // if it is backward execution, put the latest operation at front
    m_domainValues.add(m_forward ? m_domainValues.size() : 0, domainValues);
    m_rangeValues.add(m_forward ? m_rangeValues.size() : 0, null);
    m_functionTimes.add(m_forward ? m_functionTimes.size() : 0, currentTime);
    
    // return an unique reference representing the read function
    return new Reference("read_" + m_name + "_" + currentTime, type, "", new Instance("", createBlock), null, true);
  }
  
  public void remove(int index) {
    m_domainValues.remove(index);
    m_rangeValues.remove(index);
    m_functionTimes.remove(index);
  }
  
  public void move(int fromIndex, int toIndex) {
    m_domainValues.add(toIndex, m_domainValues.remove(fromIndex));
    m_rangeValues.add(toIndex, m_rangeValues.remove(fromIndex));
    m_functionTimes.add(toIndex, m_functionTimes.remove(fromIndex));
  }
  
  public boolean isArrayRelation() {
    return m_name.equals("@@array");
  }
  
  public boolean isFieldRelation() {
    return !isArrayRelation();
  }
  
  public boolean isUpdate(int index) {
    return index >= 0 && index < m_rangeValues.size() && m_rangeValues.get(index) != null;
  }
  
  public int getLastUpdateIndex(int currentIndex) {
    int index = -1;
    for (int i = currentIndex - 1; i >= 0; i--) {
      if (m_rangeValues.get(i) != null) {
        index = i;
        break;
      }
    }
    return index;
  }
  
  public int getIndex(long time) {
    return m_functionTimes.indexOf(time);
  }
  
  public int getIndex(String time) {
    return getIndex(Long.parseLong(time));
  }
  
  public String[] getDomainTypes() {
    return m_domainTypes;
  }
  
  public void setDomainTypes(String[] domainTypes) {
    m_domainTypes = domainTypes;
  }
  
  public String getRangeType() {
    return m_rangeType;
  }
  
  public void setRangeType(String rangeType) {
    m_rangeType = rangeType;
  }
  
  public List<Instance[]> getDomainValues() {
    return m_domainValues;
  }
  
  public List<Instance> getRangeValues() {
    return m_rangeValues;
  }
  
  public List<Long> getFunctionTimes() {
    return m_functionTimes;
  }
  
  public int getFunctionCount() {
    return m_functionTimes.size();
  }
  
  public String getName() {
    return m_name;
  }
  
  public int getDomainDimension() {
    return m_domainDimension;
  }
  
  public int getRangeDimension() {
    return 1;
  }
  
  public boolean getDirection() {
    return m_forward;
  }
  
  public Relation deepClone(Hashtable<Object, Object> cloneMap) {
    Relation cloneRelation = new Relation(m_name, m_domainDimension, m_forward, m_domainTypes, m_rangeType);
  
    for (Instance[] domainValues : m_domainValues) {
      Instance[] cloneDomainValues = new Instance[domainValues.length];
      for (int i = 0; i < domainValues.length; i++) {
        cloneDomainValues[i] = domainValues[i].deepClone(cloneMap);
      }
      cloneRelation.m_domainValues.add(cloneDomainValues);
    }
    
    for (Instance rangeValue : m_rangeValues) {
      cloneRelation.m_rangeValues.add(rangeValue == null ? null : rangeValue.deepClone(cloneMap));
    }
    
    for (Long functionTime : m_functionTimes) {
      cloneRelation.m_functionTimes.add(functionTime);
    }
    return cloneRelation;
  }
  
  private final String           m_name;
  private final int              m_domainDimension;
  private final boolean          m_forward;
  
  private String[]               m_domainTypes;
  private String                 m_rangeType;
  private final List<Instance[]> m_domainValues;
  private final List<Instance>   m_rangeValues;
  private final List<Long>       m_functionTimes;
}
