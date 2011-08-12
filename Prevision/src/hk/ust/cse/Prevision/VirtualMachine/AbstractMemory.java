package hk.ust.cse.Prevision.VirtualMachine;

import java.util.Enumeration;
import java.util.Hashtable;

public class AbstractMemory {
  
  // an empty memory
  public AbstractMemory () {
    m_refMap = new Hashtable<String, Hashtable<String, Reference>>();
    m_defMap = new Hashtable<String, Hashtable<String, Integer>>();
  }
  
  public AbstractMemory(Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    m_refMap = refMap;
    m_defMap = defMap;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_refMap;
  }
  
  public Hashtable<String, Hashtable<String, Integer>> getDefMap() {
    return m_defMap;
  }
  
  public AbstractMemory deepClone(Hashtable<Object, Object> cloneMap) {
    Hashtable<String, Hashtable<String, Reference>> newRefMap = deepCloneRefMap(cloneMap);
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = deepCloneDefMap();
    return new AbstractMemory(newRefMap, newDefMap);
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
  
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    
    if (!(obj instanceof AbstractMemory)) {
      return false;
    }
    
    AbstractMemory memory = (AbstractMemory) obj;
    return m_refMap.equals(memory.getRefMap()) && 
           m_defMap.equals(memory.getDefMap());
  }

  public int hashCode() {
    return m_refMap.hashCode() + m_defMap.hashCode();
  }
  
  private Hashtable<String, Hashtable<String, Reference>> m_refMap; // callSites -> {refName -> reference}
  private Hashtable<String, Hashtable<String, Integer>>   m_defMap; // callSites -> {defName (no '@') -> defCount}
}
