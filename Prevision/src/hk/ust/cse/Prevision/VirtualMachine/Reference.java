package hk.ust.cse.Prevision.VirtualMachine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

/* a reference always lies in a constant location: as an 
 * SSA variable of an ir or a field of an instance */
public class Reference {
  
  public Reference(String name, String type, String callSites, Instance instance, Instance declInstance) {
    m_name         = name;
    m_type         = type;
    m_callSites    = callSites;
    m_instances    = new HashSet<Instance>();
    m_oldInstances = new HashSet<Instance>();
    m_lifeTimes    = new Hashtable<Instance, Long[]>();
    m_declInstance = declInstance;
    
    if (name.equals("null") || name.startsWith("#") || name.equals("true") || name.equals("false")) { // is constant
      // ignore original instance
      instance = new Instance(name, null, instance.getCreateBlock());
    }
    if (instance != null) {
      m_instances.add(instance);
      startInstanceLiftTime(instance);
      instance.setLastReference(this);
    }
  }
  
  public Reference(String name, String type, String callSites, Collection<Instance> instances, Instance declInstance) {
    m_name         = name;
    m_type         = type;
    m_callSites    = callSites;
    m_instances    = new HashSet<Instance>();
    m_oldInstances = new HashSet<Instance>();
    m_lifeTimes    = new Hashtable<Instance, Long[]>();
    m_declInstance = declInstance;
    
    if (name.equals("null") || name.startsWith("#") || name.equals("true") || name.equals("false")) { // is constant
      // ignore original instance
      Instance instance = new Instance(name, null, m_instances.iterator().next().getCreateBlock());
      m_instances.add(instance);
      startInstanceLiftTime(instance);
      instance.setLastReference(this);
    }
    else if (instances != null) {
      m_instances.addAll(instances);
      startInstanceLiftTime(instances);
      for (Instance instance : instances) {
        instance.setLastReference(this);
      }
    }
  }
  
  public void assignInstance(Instance instance) throws Exception {
    if (!isConstantReference()) {
      m_instances.add(instance);
      startInstanceLiftTime(instance);
      instance.setLastReference(this);
    }
    else {
      throw new Exception("Should not assign a new instance to a constant reference!");
    }
  }
  
  public void assignInstance(Collection<Instance> instances) throws Exception {
    if (!isConstantReference()) {
      m_instances.addAll(instances);
      startInstanceLiftTime(instances);
      for (Instance instance : instances) {
        instance.setLastReference(this);
      }
    }
    else {
      throw new Exception("Should not assign a new instance to a constant reference!");
    }
  }
  
  public void putInstancesToOld() {
    m_oldInstances.addAll(m_instances);
    endInstanceLifeTime(m_instances);
    m_instances.clear();
  }
  
  // usually, need putInstancesToOld() after it
  public void setInstancesValue(Instance assigner) {
    if (!canReferenceSetValue()) {
      for (Instance instance : m_instances) {
        try {
          instance.storeValue(assigner); // never setValue directly for field instances
        } catch (Exception e) {e.printStackTrace();}
      }
    }
    else {
      for (Instance instance : m_instances) {
        try {
          instance.setValue(assigner);
        } catch (Exception e) {e.printStackTrace();}
      }
    }
  }
  
  public boolean canReferenceSetValue() {
    // fieldReferences always have concrete type name
    return m_type.equals("Unknown-Type");
  }
  
  public String getName() {
    return m_name;
  }
  
  public String getLongName() {
    String longName = m_name;
    if (m_declInstance != null) {
      if (!m_declInstance.isBounded() && m_declInstance.getLastReference() != null) {
        longName = m_declInstance.getLastReference().getLongName() + "." + m_name;
      }
      else if (m_declInstance.isConstant()){
        longName = m_declInstance.getValue() + "." + m_name;
      }
      else if (m_declInstance.isBounded()) {
        longName = m_declInstance + "." + m_name;
      }
    }
    return longName;
  }
  

  public String getLongNameWithCallSites() {
    String longName = m_name;
    if (m_declInstance != null) {
      if (!m_declInstance.isBounded() && m_declInstance.getLastReference() != null) {
        longName = m_declInstance.getLastReference().getLongNameWithCallSites() + "." + m_name;
      }
      else if (m_declInstance.isConstant()){
        longName = m_declInstance.getValue() + "." + m_name;
      }
      else if (m_declInstance.isBounded()) {
        longName = m_declInstance + "." + m_name;
      }
    }
    else if (isSSAVariable()) {
      longName = (m_callSites.length() > 0) ? "<" + m_callSites + ">" + m_name : m_name;
    }
    return longName;
  }
  
  public String getType() {
    return m_type;
  }
  
  public String getCallSites() {
    return m_callSites;
  }
  
  // return a random instance
  public Instance getInstance() {
    if (m_instances.size() > 0) {
      return m_instances.iterator().next();
    }
    else {
      return null;
    }
  }
  
  public HashSet<Instance> getInstances() {
    return m_instances;
  }
  
  public HashSet<Instance> getOldInstances() {
    return m_oldInstances;
  }
  
  public Long[] getLifeTime(Instance instance) {
    return m_lifeTimes.get(instance);
  }
  
  /**
   * return the field references of this field name
   */
  public List<Reference> getFieldReferences(String fieldName) {
    List<Reference> fieldRefs = new ArrayList<Reference>();
    for (Instance instance : m_instances) {
      Reference fieldRef = instance.getField(fieldName);
      if (fieldRef != null) {
        fieldRefs.add(fieldRef);
      }
    }
    return fieldRefs;
  }
  
  public Instance getDeclaringInstance() {
    return m_declInstance;
  }
  
  public boolean isSSAVariable() {
    return m_name.startsWith("v") && !m_name.contains(".");
  }
  
  public boolean isConstantReference() {
    return m_name.equals("null") || m_name.startsWith("#") || m_name.equals("true") || m_name.equals("false");
  }
  
  public String toString() {
    return "(" + m_type + ")" + m_name;
  }
  
  public Reference deepClone(Hashtable<Object, Object> cloneMap) {
    // if it is already cloned, return the cloned instance
    Reference cloneRef = (Reference) cloneMap.get(this);
    if (cloneRef == null) {
      // clone declInstance
      Instance cloneDeclInstance = m_declInstance;
      if (m_declInstance != null) {
        cloneDeclInstance = m_declInstance.deepClone(cloneMap);
      }
      cloneRef = deepClone(cloneMap, cloneDeclInstance);
    }
    return cloneRef;
  }
  
  public Reference deepClone(Hashtable<Object, Object> cloneMap, Instance declInstance) {
    // if it is already cloned, return the cloned instance
    Reference cloneRef = (Reference) cloneMap.get(this);
    if (cloneRef == null) {
      // create the clone reference with no instance initially
      cloneRef = new Reference(m_name, m_type, m_callSites, (Instance)null, declInstance);
      
      // clone instances
      for (Instance instance : m_instances) {
        Instance cloneInstance = instance.deepClone(cloneMap);
  
        try {
          Reference oriLastRef = cloneInstance.getLastReference();
          cloneRef.assignInstance(cloneInstance); // the lastRef of cloneInstance is now set to cloneRef
          if (instance.getLastReference() != this) {
            // should not reset by new Reference()
            cloneInstance.setLastReference(oriLastRef); // wait for the true lastRef
          }
        } catch (Exception e) {e.printStackTrace();}
      }
      
      // clone old instances
      for (Instance instance : m_oldInstances) {
        Instance cloneInstance = instance.deepClone(cloneMap);
        cloneRef.m_oldInstances.add(cloneInstance);
      }
      
      // clone instance life time
      Enumeration<Instance> keys = m_lifeTimes.keys();
      while (keys.hasMoreElements()) {
        Instance instance = (Instance) keys.nextElement();
        Instance cloneInstance = instance.deepClone(cloneMap);
        Long[] lifeTime = m_lifeTimes.get(instance);
        cloneRef.m_lifeTimes.put(cloneInstance, new Long[] {lifeTime[0], lifeTime[1]});
      }
      
      // save the new clone
      cloneMap.put(this, cloneRef);
    }
    return cloneRef;
  }
  
  private void startInstanceLiftTime(Instance instance) {
    // starts the new lifee time
    m_lifeTimes.put(instance, new Long[] {System.nanoTime(), Long.MAX_VALUE});
  }
  
  private void startInstanceLiftTime(Collection<Instance> instances) {
    long time = System.nanoTime();
    //System.err.println("start lifetime: " + time);
    for (Instance instance : instances) {
      // starts the new life time
      m_lifeTimes.put(instance, new Long[] {time, Long.MAX_VALUE});
    }
  }
  
  private void endInstanceLifeTime(Collection<Instance> instances) {
    long time = System.nanoTime();
    //System.err.println("end lifetime: " + time);
    for (Instance instance : instances) {
      Long[] times = m_lifeTimes.get(instance);
      if (times != null) {
        times[1] = time;
      }
    }
  }
  
  private final String                      m_name;
  private final String                      m_type;
  private final String                      m_callSites;
  private final HashSet<Instance>           m_instances;    // instance should never be null, even if it is a null reference, use a 'null' instance
  private final HashSet<Instance>           m_oldInstances;
  private final Instance                    m_declInstance; // instance that holds this reference, should be null if it is not a field reference
  private final Hashtable<Instance, Long[]> m_lifeTimes;
}
