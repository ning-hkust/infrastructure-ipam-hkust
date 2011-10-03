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
  public Relation(String name, int domainDimension) {
    m_name            = name;
    m_domainDimension = domainDimension; // should be one for field (parent) and two for array (base and index)
    
    // to save relational updates
    m_domainValues  = new ArrayList<Instance[]>();
    m_rangeValues   = new ArrayList<Instance>();
    m_functionTimes = new ArrayList<Long>();
  }
  
  public void update(Instance[] domainValues, Instance rangeValue) {
    if ((isArrayRelation() && domainValues.length != 2) || (isFieldRelation() && domainValues.length != 1)) {
      throw new IllegalArgumentException("The number of domain values is not correct.");
    }
    
    m_domainValues.add(domainValues);
    m_rangeValues.add(rangeValue);
    m_functionTimes.add(System.nanoTime());
  }
  
  public Reference read(Instance[] domainValues, String type, ISSABasicBlock createBlock) {
    if ((isArrayRelation() && domainValues.length != 2) || (isFieldRelation() && domainValues.length != 1)) {
      throw new IllegalArgumentException("The number of domain values is not correct.");
    }
    
    // record the read relation
    long currentTime = System.nanoTime();
    m_domainValues.add(domainValues);
    m_rangeValues.add(null);
    m_functionTimes.add(currentTime);
    
    // return an unique reference representing the read function
    return new Reference("read_" + m_name + "_" + currentTime, type, "", new Instance("", createBlock), null);
  }
  
  public boolean isArrayRelation() {
    return m_name.equals("@array");
  }
  
  public boolean isFieldRelation() {
    return !isArrayRelation();
  }
  
  public int getLastUpdateIndex(int currentIndex) {
    int index = -1;
    for (int i = currentIndex + 1, size = m_rangeValues.size(); i < size; i++) {
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
  
  public long getReadStringTime(String readStr) {
    return Long.parseLong(readStr.substring(readStr.lastIndexOf("_") + 1));
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
  
  public Relation deepClone(Hashtable<Object, Object> cloneMap) {
    Relation cloneRelation = new Relation(m_name, m_domainDimension);
  
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

  private final List<Instance[]> m_domainValues;
  private final List<Instance>   m_rangeValues;
  private final List<Long>       m_functionTimes;
}
