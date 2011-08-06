package hk.ust.cse.Prevision.VirtualMachine;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class AbstractMemory {
  
  // an empty memory
  public AbstractMemory () {
    m_refMap = new Hashtable<String, Hashtable<String, Reference>>();
    m_phiMap = new Hashtable<String, Hashtable<String, List<Reference>>>();
    m_defMap = new Hashtable<String, Hashtable<String, Integer>>();
  }
  
  public AbstractMemory(Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, List<Reference>>> phiMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    m_refMap = refMap;
    m_phiMap = phiMap;
    m_defMap = defMap;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_refMap;
  }
  
  public Hashtable<String, Hashtable<String, List<Reference>>> getPhiMap() {
    return m_phiMap;
  }

  public Hashtable<String, Hashtable<String, Integer>> getDefMap() {
    return m_defMap;
  }
  
  public AbstractMemory deepClone(Hashtable<Object, Object> cloneMap) {
    Hashtable<String, Hashtable<String, Reference>> newRefMap       = deepCloneRefMap(cloneMap);
    Hashtable<String, Hashtable<String, List<Reference>>> newPhiMap = deepClonePhiMap(cloneMap);
    Hashtable<String, Hashtable<String, Integer>> newDefMap         = deepCloneDefMap();
    return new AbstractMemory(newRefMap, newPhiMap, newDefMap);
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
        Reference ref = newMethodRefs.get(key2);
        Reference cloneRef = (Reference) cloneMap.get(ref);
        if (cloneRef == null) {
          cloneRef = ref.deepClone(cloneMap);
          cloneMap.put(ref, cloneRef);
        }
        newMethodRefs.put(key2, cloneRef);
      }
      newRefMap.put(key, newMethodRefs);
    }
    return newRefMap;
  }
  
  @SuppressWarnings("unchecked")
  private Hashtable<String, Hashtable<String, List<Reference>>> deepClonePhiMap(Hashtable<Object, Object> cloneMap) {
    Hashtable<String, Hashtable<String, List<Reference>>> newPhiMap = 
      (Hashtable<String, Hashtable<String, List<Reference>>>) m_phiMap.clone();

    Enumeration<String> keys = newPhiMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Hashtable<String, List<Reference>> methodPhis = newPhiMap.get(key);
      
      Hashtable<String, List<Reference>> newMethodPhis = 
        (Hashtable<String, List<Reference>>) methodPhis.clone();
      Enumeration<String> keys2 = newMethodPhis.keys();
      while (keys2.hasMoreElements()) {
        String key2 = (String) keys2.nextElement();
        List<Reference> refsList = newMethodPhis.get(key2);
        List<Reference> cloneRefsList = new ArrayList<Reference>();
        for (Reference ref : refsList) {
          Reference cloneRef = (Reference) cloneMap.get(ref);
          if (cloneRef == null) {
            cloneRef = ref.deepClone(cloneMap);
            cloneMap.put(ref, cloneRef);
          }
          cloneRefsList.add(cloneRef);
        }
        newMethodPhis.put(key2, cloneRefsList);
      }
      newPhiMap.put(key, newMethodPhis);
    }
    return newPhiMap;
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
           m_phiMap.equals(memory.getPhiMap()) && 
           m_defMap.equals(memory.getDefMap());
  }

  public int hashCode() {
    return m_refMap.hashCode() + m_phiMap.hashCode() + m_defMap.hashCode();
  }
  
  private Hashtable<String, Hashtable<String, Reference>>       m_refMap; // callSites -> {refName -> reference}
  private Hashtable<String, Hashtable<String, List<Reference>>> m_phiMap; // callSites -> {defName -> references}
  private Hashtable<String, Hashtable<String, Integer>>         m_defMap; // callSites -> {defName (no '@') -> defCount}
}
