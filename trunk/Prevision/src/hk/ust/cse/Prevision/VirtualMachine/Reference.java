package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

/* a reference always lies in a constant location: as an 
 * SSA variable of an ir or a field of an instance */
public class Reference {
  
  public Reference(String name, String type, String callSites, 
                   Instance instance, Instance declInstance, boolean setLastRef) {
    m_name         = name;
    m_type         = type;
    m_callSites    = callSites;
    m_instances    = new HashSet<Instance>(1);
    m_oldInstances = new HashSet<Instance>(1);
    m_lifeTimes    = new Hashtable<Instance, Long[]>(1);
    m_declInstance = declInstance;

    if (name.equals("null") || name.startsWith("#") || name.equals("true") || name.equals("false")) { // is constant
      // ignore original instance    
      if (instance != null) {
        instance = new Instance(name, null, instance.getCreateBlock());
      }
    }
    if (instance != null) {
      m_instances.add(instance);
      startInstanceLiftTime(instance);
      if (!instance.isBounded() && setLastRef) {
        instance.setLastReference(this);
      }
    }
  }
  
  public Reference(String name, String type, String callSites, Collection<Instance> instances, 
                   Instance declInstance, boolean setLastRef) {
    m_name         = name;
    m_type         = type;
    m_callSites    = callSites;
    m_instances    = new HashSet<Instance>(1);
    m_oldInstances = new HashSet<Instance>(1);
    m_lifeTimes    = new Hashtable<Instance, Long[]>(1);
    m_declInstance = declInstance;
    
    if (name.equals("null") || name.startsWith("#") || name.equals("true") || name.equals("false")) { // is constant
      // ignore original instance
      Instance constInstance = new Instance(name, null, instances.iterator().next().getCreateBlock());
      m_instances.addAll(instances);
      startInstanceLiftTime(instances);
      for (Instance instance : instances) {
        if (!instance.isBounded()) {
          try {
            instance.setValue(constInstance);
          } catch (Exception e) {e.printStackTrace();}
        }
      }
    }
    else if (instances != null) {
      m_instances.addAll(instances);
      startInstanceLiftTime(instances);
      for (Instance instance : instances) {
        if (!instance.isBounded() && setLastRef) {
          instance.setLastReference(this);
        }
      }
    }
  }
  
  public void assignInstance(Instance instance, boolean setLastRef) throws Exception {
    if (!isConstantReference()) {
      m_instances.add(instance);
      startInstanceLiftTime(instance);
      if (!instance.isBounded() && setLastRef) {
        instance.setLastReference(this);
      }
    }
    else {
      throw new Exception("Should not assign a new instance to a constant reference!");
    }
  }
  
  public void assignInstance(Collection<Instance> instances, boolean setLastRef) throws Exception {
    if (!isConstantReference()) {
      m_instances.addAll(instances);
      startInstanceLiftTime(instances);
      for (Instance instance : instances) {
        if (!instance.isBounded() && setLastRef) {
          instance.setLastReference(this);
        }
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
    boolean canReferenceSetValue = canReferenceSetValue();
    
    for (Instance instance : m_instances) {
      if (!canReferenceSetValue && !instance.isBounded()) {
        try {
          instance.storeValue(assigner);
        } catch (Exception e) {e.printStackTrace();}
      }
      else if (!instance.isBounded()) {
        try {
          instance.setValue(assigner);
        } catch (Exception e) {e.printStackTrace();}
      }
    }
  }
  
  public void setFieldInstancesValue(Instance assigner, Formula formula) {
    List<Long> times = formula.getFieldAssignTimes(m_name);
    long currentTime = System.nanoTime();
    long lastTime = (times == null) ? Long.MAX_VALUE : times.get(times.size() - 1);
    
    for (Instance instance : m_instances) {
      if (instance.getCreateTime() > lastTime && instance.getCreateTime() < currentTime && !instance.isBounded()) {
        try {
          instance.setValue(assigner);
        } catch (Exception e) {e.printStackTrace();}
      }
      else if (!instance.isBounded()) {
        try {
          instance.storeValue(assigner);
        } catch (Exception e) {e.printStackTrace();}
      }
    }
  }
  
  public void updateFieldInstancesValue(Collection<Instance> newInstances, Formula formula) {
    // find sets from the new instances
    Hashtable<String, List<Instance>> settedInstances = new Hashtable<String, List<Instance>>();
    for (Instance newInstance : newInstances) {
      findSetInstances("", new ArrayList<Instance>(), newInstance, m_name, settedInstances);
    }
    
    // update the value of the old instances
    for (Instance instance : m_instances) {
      updateFieldInstancesValue("", new ArrayList<Instance>(), instance, m_name, settedInstances, formula);
    }
  }

  // assign new life time values to instance
  public void resetInstanceLiftTime(Instance instance, long startTime, long endTime) {
    m_lifeTimes.put(instance, new Long[] {startTime, endTime});
  }

  private void findSetInstances(String lastPath, List<Instance> preInstances, 
      Instance instance, String fieldName, Hashtable<String, List<Instance>> settedInstances) {
    
    // build current path
    String path = new StringBuilder(lastPath).append(".").append(fieldName).toString();
    
    if (!preInstances.contains(instance)) { // avoid recursions
      preInstances.add(instance);
      if (instance.getSetValueTime() != Long.MIN_VALUE) { // this instance is set/store
        List<Instance> pathInstances = settedInstances.get(path);
        if (pathInstances == null) {
          pathInstances = new ArrayList<Instance>();
          settedInstances.put(path, pathInstances);
        }
        pathInstances.add(instance);
      }

      // recursive for instance's fields
      for (Reference fieldRef : instance.getFields()) {
        for (Instance fieldInstance : fieldRef.getInstances()) {
          findSetInstances(path, preInstances, fieldInstance, fieldRef.getName(), settedInstances);
        }
      }
      preInstances.remove(preInstances.size() - 1);
    }
  }
  
  private void updateFieldInstancesValue(String lastPath, List<Instance> preInstances, 
      Instance instance, String fieldName, Hashtable<String, List<Instance>> settedInstances, Formula formula) {

    // build current path
    String path = new StringBuilder(lastPath).append(".").append(fieldName).toString();
    
    if (!preInstances.contains(instance)) { // avoid recursions
      preInstances.add(instance);
      if (instance.getSetValueTime() == Long.MIN_VALUE) {
        List<Instance> pathInstances = settedInstances.get(path);
        if (pathInstances != null) {
          // get the settable period
          long start = instance.getCreateTime();
          long end = Long.MIN_VALUE;
          for (Long assignTime : formula.getFieldAssignTimes(fieldName)) {
            if (assignTime > start) {
              end = assignTime;
              break;
            }
          }
          
          Instance nearestSetted = null;
          for (Instance pathInstance : pathInstances) {
            if (pathInstance.getSetValueTime() > start && pathInstance.getSetValueTime() <= end && 
                (nearestSetted == null || pathInstance.getSetValueTime() < nearestSetted.getSetValueTime())) {
              nearestSetted = pathInstance;
            }
          }
          
          if (nearestSetted != null) {
            try {
              instance.setValueInclSetTime(nearestSetted, nearestSetted.getSetValueTime());
            } catch (Exception e) {e.printStackTrace();}
          }
        }
      }

      // recursive for instance's fields
      for (Reference fieldRef : instance.getFields()) {
        for (Instance fieldInstance : fieldRef.getInstances()) {
          updateFieldInstancesValue(path, preInstances, fieldInstance, fieldRef.getName(), settedInstances, formula);
        }
      }
      preInstances.remove(preInstances.size() - 1);
    }
  }
  
  public boolean canReferenceSetValue() {
    // fieldReferences always have concrete type name
    return m_type.equals("Unknown-Type") || m_declInstance == null;
  }
  
  public void setType(String type) {
    m_type = type;
  }
  
  public String getName() {
    return m_name;
  }
  
  public String getNameWithCallSite() {
    return (m_callSites.length() > 0) ? "<" + m_callSites + ">" + m_name : m_name;
  }
  
  public String getLongName() {
    return getLongName(new HashSet<List<Object>>());
  }
  
  private String getLongName(HashSet<List<Object>> prevInstances) {
    String longName = m_name;
    if (m_declInstance != null) {
      if (!m_declInstance.isBounded() && m_declInstance.getLastReference() != null) {
        List<Object> current = new ArrayList<Object>();
        current.add(m_declInstance);
        current.add(this);
        
        if (prevInstances.contains(current)) {
          longName = m_declInstance.getLastRefName() + "." + m_name;
        }
        else {
          prevInstances.add(current);
          longName = m_declInstance.getLastReference().getLongName(prevInstances) + "." + m_name;
        }
      }
      else if (m_declInstance.isConstant()) {
        longName = m_declInstance.getValue() + "." + m_name;
      }
      else if (m_declInstance.isBounded()) {
        longName = m_declInstance + "." + m_name;
      }
    }
    return longName;
  }

  public String getLongNameWithCallSites() {
    return getLongNameWithCallSites(new HashSet<List<Object>>());
  }
  
  private String getLongNameWithCallSites(HashSet<List<Object>> prevInstances) {
    String longName = m_name;
    if (m_declInstance != null) {
      if (!m_declInstance.isBounded() && m_declInstance.getLastReference() != null) {
        List<Object> current = new ArrayList<Object>();
        current.add(m_declInstance);
        current.add(this);
        
        if (prevInstances.contains(current)) {
          longName = m_declInstance.getLastReference().getNameWithCallSite() + "." + m_name;
        }
        else {
          prevInstances.add(current);
          longName = m_declInstance.getLastReference().getLongNameWithCallSites(prevInstances) + "." + m_name;
        }
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
    boolean isSSAVar = false;
    
    int index = m_name.lastIndexOf('@');
    String cutName = index < 0 ? m_name : m_name.substring(0, index);
    if (cutName.startsWith("v") && !cutName.contains(".")) {
      try {
        Integer.parseInt(cutName.substring(1));
        isSSAVar = true;
      } catch (Exception e) {}
    }
    return isSSAVar;
  }
  
  public boolean isStaticField() {
    return m_callSites.length() == 0 && m_name.startsWith("L");
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
      cloneRef = new Reference(m_name, m_type, m_callSites, (Instance) null, declInstance, true);
      
      // clone instances
      for (Instance instance : m_instances) {
        Instance cloneInstance = instance.deepClone(cloneMap);
        cloneRef.m_instances.add(cloneInstance);
        if (!cloneInstance.isBounded() && instance.getLastReference() == this) {
          cloneInstance.setLastReference(cloneRef);
        }
      }
      
      // clone old instances
      for (Instance instance : m_oldInstances) {
        Instance cloneInstance = instance.deepClone(cloneMap);
        cloneRef.m_oldInstances.add(cloneInstance);
        if (!cloneInstance.isBounded() && instance.getLastReference() == this) {
          // this should only happen in forward execution
          cloneInstance.setLastReference(cloneRef);
        }
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

  // create reference from persistence string
  public static Reference createReference(int num, String type, String name, List<Integer> instances, 
      List<Integer> oldInstances, Hashtable<Integer, Long[]> lifeTimes, int numDeclInstance, 
      Hashtable<Integer, Instance> instanceNumMap, Hashtable<Integer, Reference> referenceNumMap) {
    
    Reference reference = referenceNumMap.get(num);
    if (reference == null) {
      reference = new Reference(name, type, "", (Instance) null, null, false);
      referenceNumMap.put(num, reference);
    }
    
    // use reflection to assign fields    
    // m_type, m_value
    Utils.setField(Reference.class, "m_type", reference, type);
    Utils.setField(Reference.class, "m_name", reference, name);

    // lifetimes
    Hashtable<Instance, Long[]> lifeTimesTable = new Hashtable<Instance, Long[]>();
    Utils.setField(Reference.class, "m_lifeTimes", reference, lifeTimesTable);
    
    // m_instances
    HashSet<Instance> instanceSet = new HashSet<Instance>();
    for (Integer numInstance : instances) {
      Instance instance = numInstance < 0 ? null : instanceNumMap.get(numInstance);
      if (numInstance >= 0 && instance == null) {
        instance = new Instance("", "", null);
        instanceNumMap.put(numInstance, instance);
      }
      instanceSet.add(instance);
      
      Long[] lifeTime = lifeTimes.get(numInstance);
      if (lifeTime != null) {
        lifeTimesTable.put(instance, lifeTime);
      }
    }
    Utils.setField(Reference.class, "m_instances", reference, instanceSet);
    
    // m_oldInstances
    HashSet<Instance> oldInstanceSet = new HashSet<Instance>();
    for (Integer numInstance : oldInstances) {
      Instance instance = numInstance < 0 ? null : instanceNumMap.get(numInstance);
      if (numInstance >= 0 && instance == null) {
        instance = new Instance("", "", null);
        instanceNumMap.put(numInstance, instance);
      }
      oldInstanceSet.add(instance);
      
      Long[] lifeTime = lifeTimes.get(numInstance);
      if (lifeTime != null) {
        lifeTimesTable.put(instance, lifeTime);
      }
    }
    Utils.setField(Reference.class, "m_oldInstances", reference, oldInstanceSet);
    
    // m_declInstance
    Instance instance = numDeclInstance < 0 ? null : instanceNumMap.get(numDeclInstance);
    if (numDeclInstance >= 0 && instance == null) {
      instance = new Instance("", "", null);
      instanceNumMap.put(numDeclInstance, instance);
    }
    Utils.setField(Reference.class, "m_declInstance", reference, instance);
    
    return reference;
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

  private String                            m_type;
  private final String                      m_name;
  private final String                      m_callSites;
  private final HashSet<Instance>           m_instances;    // instance should never be null, even if it is a null reference, use a 'null' instance
  private final HashSet<Instance>           m_oldInstances;
  private final Instance                    m_declInstance; // instance that holds this reference, should be null if it is not a field reference
  private final Hashtable<Instance, Long[]> m_lifeTimes;
}
