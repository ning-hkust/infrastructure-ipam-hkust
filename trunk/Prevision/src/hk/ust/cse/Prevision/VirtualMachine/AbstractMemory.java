package hk.ust.cse.Prevision.VirtualMachine;


import java.util.Enumeration;
import java.util.Hashtable;

public class AbstractMemory {
  
  // an empty memory
  public AbstractMemory(boolean forward) {
    m_refMap      = new Hashtable<String, Hashtable<String, Reference>>();
    m_defMap      = new Hashtable<String, Hashtable<String, Integer>>();
    m_relationMap = new Hashtable<String, Relation>();
    
    // always have a special relation for array
    addRelation("@@array", 2, forward, new String[] {"not_null_reference", "I"}, "Unknown-Type");
  }
  
  public AbstractMemory(Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap, Hashtable<String, Relation> relationMap) {
    
    m_refMap      = refMap;
    m_defMap      = defMap;
    m_relationMap = relationMap;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_refMap;
  }
  
  public Hashtable<String, Hashtable<String, Integer>> getDefMap() {
    return m_defMap;
  }
  
  public Hashtable<String, Relation> getRelationMap() {
    return m_relationMap;
  }
  
  public Relation getRelation(String relationName) {
    return m_relationMap.get(relationName);
  }
  
  public Relation addRelation(String relationName, 
      int domainDimension, boolean forward, String[] domainTypes, String rangeType) {
    
    if (!m_relationMap.containsKey(relationName)) {
      m_relationMap.put(relationName, new Relation(relationName, domainDimension, forward, domainTypes, rangeType));
    }
    return getRelation(relationName);
  }
  
  public Relation addFieldRelation(String relationName, boolean forward, String[] domainTypes, String rangeType) {
    return addRelation(relationName, 1, forward, domainTypes, rangeType);
  }
  
  public AbstractMemory deepClone(Hashtable<Object, Object> cloneMap) {
    Hashtable<String, Hashtable<String, Reference>> newRefMap = deepCloneRefMap(cloneMap);
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = deepCloneDefMap();
    Hashtable<String, Relation> newRelationMap                = deepCloneRelationMap(cloneMap);
    return new AbstractMemory(newRefMap, newDefMap, newRelationMap);
  }
  
  @SuppressWarnings("unchecked")
  private Hashtable<String, Hashtable<String, Reference>> deepCloneRefMap(Hashtable<Object, Object> cloneMap) { 
    Hashtable<String, Hashtable<String, Reference>> newRefMap = 
      (Hashtable<String, Hashtable<String, Reference>>) m_refMap.clone();

    Enumeration<String> keys = newRefMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Hashtable<String, Reference> methodRefs = newRefMap.get(key);
      
      Hashtable<String, Reference> newMethodRefs = 
        (Hashtable<String, Reference>) methodRefs.clone();
      Enumeration<String> keys2 = newMethodRefs.keys();
      while (keys2.hasMoreElements()) {
        String key2 = (String) keys2.nextElement();
        newMethodRefs.put(key2, newMethodRefs.get(key2).deepClone(cloneMap));
      }
      newRefMap.put(key, newMethodRefs);
    }
    return newRefMap;
  }
  
  @SuppressWarnings("unchecked")
  private Hashtable<String, Hashtable<String, Integer>> deepCloneDefMap() {
    Hashtable<String, Hashtable<String, Integer>> newDefMap = 
      (Hashtable<String, Hashtable<String, Integer>>) m_defMap.clone();

    Enumeration<String> keys = newDefMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      newDefMap.put(key, (Hashtable<String, Integer>) newDefMap.get(key).clone());
    }
    return newDefMap;
  }
  
  @SuppressWarnings("unchecked")
  private Hashtable<String, Relation> deepCloneRelationMap(Hashtable<Object, Object> cloneMap) {
    Hashtable<String, Relation> newRelationMap = (Hashtable<String, Relation>) m_relationMap.clone();
    
    Enumeration<String> keys = newRelationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      newRelationMap.put(key, newRelationMap.get(key).deepClone(cloneMap));
    }
    return newRelationMap;
  }
  
  private final Hashtable<String, Hashtable<String, Reference>> m_refMap; // callSites -> {refName -> reference}
  private final Hashtable<String, Hashtable<String, Integer>>   m_defMap; // callSites -> {defName (no '@') -> defCount}
  private final Hashtable<String, Relation>                     m_relationMap;
}
