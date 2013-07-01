package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SymbolTable;


public abstract class AbstractBackwardHandler extends AbstractHandler {

  /**
   * @return if variable "varID" is a constant, return the prefixed 
   * string representing that constant, otherwise return vVarID
   */
  public String getSymbol(int varID, MethodMetaData methData, String callSites, 
      Hashtable<String, Hashtable<String, Integer>> defCountMap) {
    
    String var = null;
    SymbolTable symbolTable = methData.getSymbolTable();
    
    if (varID >= 0 && symbolTable.isConstant(varID)) {
      Object constant = symbolTable.getConstantValue(varID);
      var = (constant != null) ? getConstantPrefix(varID, methData) + constant.toString() : "null";
    }
    else {
      var = "v" + varID;

      // add defCount information
      Hashtable<String, Integer> methodDefCounts = defCountMap.get(callSites);
      if (methodDefCounts != null) {
        Integer defCount = methodDefCounts.get(var);
        if (defCount != null) {
          var += "@" + defCount;
        }
      }
    }
    return var;
  }
  
  protected final void assignInstance(Reference defRef, Reference fromRef,
      Hashtable<String, Hashtable<String, Reference>> newRefMap, 
      Hashtable<String, Hashtable<String, Integer>> newDefMap) {
    
    // since there is a new def, add to def
    addDefToDefMap(newDefMap, defRef);   

    if (fromRef.getInstance().isBounded()) {
      // associate the two refs' instance together as the same one
      defRef.setInstancesValue(fromRef.getInstance());
      defRef.putInstancesToOld();
      
      // defRef not longer useful
      if (defRef.canReferenceSetValue() && findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
        newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
      }
    }
    else {
      // associate the two refs' instance together as the same one
      try {
        fromRef.assignInstance(defRef.getInstances(), true);
        defRef.putInstancesToOld();
      } catch (Exception e) {e.printStackTrace();}
      // put fromRef to refMap if defRef is in refMap
      if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
        addRefToRefMap(newRefMap, fromRef);
        newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
      }
    }
  }
  
  protected final void assignInstance(Reference defRef, Instance fromInstance,
      Hashtable<String, Hashtable<String, Reference>> newRefMap, 
      Hashtable<String, Hashtable<String, Integer>> newDefMap) {

    // since there is a new def, add to def
    addDefToDefMap(newDefMap, defRef);

    if (fromInstance.isBounded()) {
      // associate the two refs' instance together as the same one
      defRef.setInstancesValue(fromInstance);
      defRef.putInstancesToOld();
      
      // defRef not longer useful
      if (defRef.canReferenceSetValue() && findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
        newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
      }
    }
  }
  
  protected final boolean containsFieldName(String fieldName, Formula formula) {
    HashSet<String> allFieldNames = findAllFieldNames(formula);
    return allFieldNames.contains(fieldName);
  }
  
  // for v27 = Class.forName(v11, ...), look for v27.newInstance() instanceof someClassName, 
  // and then add a condition of v11 == ##someClassName
  protected final void handleClassForName(String def, String classNameParam, Formula postCond, 
      Hashtable<String, Hashtable<String, Reference>> refMap, String callSites, ISSABasicBlock currentBB) {
    
    String forNameNewInstance = def + ".newInstance()";
    for (int i = 0, size = postCond.getConditionList().size(); i < size; i++) {
      TypeConditionTerm typeTerm = postCond.getConditionList().get(i).getOnlyTypeTerm();
      if (typeTerm != null && typeTerm.getInstance1().toString().equals(forNameNewInstance)) {
        String typeName = Utils.getClassTypeJavaStr(typeTerm.getTypeString());
        Instance typeNameInstance = new Instance("##" + typeName, "Ljava/lang/String", currentBB);
        Reference classNameRef = findOrCreateReference(classNameParam, "Ljava/lang/String", callSites, currentBB, postCond);
        
        BinaryConditionTerm classNameTerm = new BinaryConditionTerm(
            classNameRef.getInstance(), BinaryConditionTerm.Comparator.OP_EQUAL, typeNameInstance);
        postCond.getConditionList().add(new Condition(classNameTerm));
        // add new references to refMap
        addRefToRefMap(refMap, classNameRef);
        break;
      }
    }
  }
  
  private HashSet<String> findAllFieldNames(Formula formula) {
    List<String> allFieldNames = new ArrayList<String>();
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    for (Hashtable<String, Reference> methodRefs : refMap.values()) {
      for (Reference ref : methodRefs.values()) {
        findAllFieldNames(ref, allFieldNames, false);
      }
    }
    return new HashSet<String>(allFieldNames);
  }
  
  private void findAllFieldNames(Reference ref, List<String> allFieldNames, boolean isField) {
    if (isField) {
      allFieldNames.add(ref.getName());
    }

    for (Instance refInstance : ref.getInstances()) {
      // recursive for instance's fields
      for (Reference fieldRef : refInstance.getFields()) {
        findAllFieldNames(fieldRef, allFieldNames, true);
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  protected Formula setEquivalentInstances(Formula preCond, String callSites) {
    List<Object[]> prevSets = new ArrayList<Object[]>();
    
    // phase 0: prepare the sequence of set
    Hashtable<String, Reference> callSitesRefs = preCond.getRefMap().get(callSites);
    Collection<Reference> methodRefs = callSitesRefs != null ? callSitesRefs.values() : new ArrayList<Reference>();
    List<Object[]> setSequence = new ArrayList<Object[]>();
    for (Reference ref : methodRefs) {
      getSetSequence(new ArrayList<Instance>(), new ArrayList<Reference>(), ref, setSequence);
    }
    Collections.sort(setSequence, new Comparator<Object[]>() {
      @Override
      public int compare(Object[] o1, Object[] o2) {
        return ((List<Instance>) o1[2]).size() - ((List<Instance>) o2[2]).size();
      }
    });

    boolean changed = true;
    for (@SuppressWarnings("unused") int i = 0; changed; i++) {
      //System.err.println(i + ": ====================================");
      
      // phase 1: find all set instances and corresponding paths
      Hashtable<String, List<Instance>> settedInstances = new Hashtable<String, List<Instance>>();
      for (Reference ref : methodRefs) { // sequence is not important in find
        findSetInstances("", new ArrayList<Instance>(), ref, settedInstances);
      }
//      for (List<Instance> instances : settedInstances.values()) {
//        Utils.deleteRedundents(instances);
//      }

      // check for additional setted path after last set
      HashSet<String> newlySettedPath         = new HashSet<String>();
      Hashtable<String, Long> newlySettedTime = new Hashtable<String, Long>();
      if (prevSets.size() > 0) {
        Hashtable<String, List<Instance>> lastSettedInstances = 
          (Hashtable<String, List<Instance>>) prevSets.get(prevSets.size() - 1)[6];
        Enumeration<String> keys = settedInstances.keys();
        while (keys.hasMoreElements()) {
          String path = (String) keys.nextElement();
          if (!lastSettedInstances.containsKey(path)) {
            newlySettedPath.add(path);
            newlySettedTime.put(path, settedInstances.get(path).iterator().next().getSetValueTime());
            //System.err.println("Found newly setted value for path: " + path);
          }
          else {
            List<Instance> lastSets  = lastSettedInstances.get(path);
            List<Instance> newlySets = settedInstances.get(path);
            for (Instance instance : newlySets) {
              if (!lastSets.contains(instance)) {
                newlySettedPath.add(path);
                newlySettedTime.put(path, instance.getSetValueTime());
                //System.err.println("Found newly setted value for path: " + path);
              }
            }
          }
        }
      }
      
      // find previous sets whose paths match the newly setted values
      List<Integer> matchedIndices = new ArrayList<Integer>();
      for (int j = 0, size = prevSets.size(); j < size - 1; j++) {
        if (newlySettedPath.contains(prevSets.get(j)[0])) {
          matchedIndices.add(j);
          //System.out.println("matched: " + prevSets.get(j)[0]);
        }
      }
      List<Integer> revertingIndices = new ArrayList<Integer>();
      for (int j = 0, size = matchedIndices.size(); j < size; j++) {
        int prevMatchedIndex = matchedIndices.get(j);
        Object[] prevSet = prevSets.get(prevMatchedIndex);
        Object[] lastSet = prevSets.get(prevSets.size() - 1);
        // some reverts may not be necessary
        if (!prevSet[0].equals(lastSet[0]) || prevSet[1] == null || !prevSet[1].equals(lastSet[1])) {
          Long time = (Long) newlySettedTime.get(prevSet[0]);
          if (time < (Long) prevSet[2] && time > (Long) ((Long[]) prevSet[3])[0]/* && false*/) { //XXX
            revertingIndices.add(prevMatchedIndex);
          }
//          else {
//            System.out.println("pos 1 because: " + newlySettedTimes + " < " + 
//                (Long) prevSet[2] + " && > " +  (Long) ((Long[]) prevSet[3])[0] + 
//                " " + prevSet[0] + " " + lastSet[0] + " " + prevSet[1] + " " + lastSet[1]);
//          }
        }
//        else {
//          System.out.println("pos 2");
//        }
      }

      // revert-able
      if (revertingIndices.size() > 0) {
        for (Integer toRevert : revertingIndices) {
          Object[] prevSet = prevSets.get(toRevert);
          Object[] lastSet = prevSets.get(prevSets.size() - 1);

          // find the new positions
          List<Integer> index1s = new ArrayList<Integer>();
          int index2 = -1;
          // find all occurrences of the first instance
          for (int j = 0, size = setSequence.size(); j < size; j++) {
            if (setSequence.get(j)[0] == prevSet[7]) {
              index1s.add(j);
            }
            index2 = (setSequence.get(j)[0] == lastSet[7]) ? j : index2;
          }
          if (index1s.size() > 0 && index2 >= 0) {
            for (int j = 0, shifted = 0, size = index1s.size(); j < size; j++) {
              if (index1s.get(j) < index2) {
                setSequence.add(index2, setSequence.remove(index1s.get(j) - shifted));
                shifted++;
              }
            }
          }
          else if (index1s.size() == 0 || index2 < 0) {
            System.err.println("Failed to find the new position.");
          }
        }
        
        // roll back preCond
        int revertTo = revertingIndices.get(0);
        // fine the pos which preCond is not null
        for (; revertTo >= 0; revertTo--) {
          preCond = (Formula) prevSets.get(revertTo)[4];
          if (preCond != null) {
            break;
          }
        }
        methodRefs = preCond.getRefMap().get(callSites).values();
        Hashtable<Object, Object> cloneMap = (Hashtable<Object, Object>) prevSets.get(revertTo)[5];
        
        // roll back previous history also
        prevSets = new ArrayList<Object[]>(prevSets.subList(0, revertTo));
  
        // substitute the instances, no need to substitute references
        for (Object[] set : setSequence) {
          Instance newInstance = (Instance) cloneMap.get(set[0]);
          if (newInstance != null) {
            List<Instance> newPrevInstances = null;
            List<Instance> prevInstances = (List<Instance>) set[2];
            if (prevInstances != null) {
              newPrevInstances = new ArrayList<Instance>();
              for (Instance prevInstance : prevInstances) {
                newPrevInstances.add((Instance) cloneMap.get(prevInstance));
              }
            }
            set[0] = newInstance;
            set[1] = ((Reference) set[1]).deepClone(cloneMap); // swap in the new instances
            set[2] = newPrevInstances;
          }
          else {
            System.err.println("Failed to map an instance to the new sequence.");
          }
        }
        i = revertTo - 1;
        System.err.println("Newly setted value caused a conflict, reverting back to " + revertTo);
        continue;
      }
      
      // phase 2: set the equivalent not set instances
      changed = false;
      for (int j = 0, size = setSequence.size(); j < size; j++) {
        Object[] next = setSequence.get(j);
        Object[] ret = setEquivalentInstances(preCond, (Instance) next[0], (Reference) next[1], 
            (List<Instance>) next[2], (List<Reference>) next[3], settedInstances, callSites, prevSets.size());
        changed |= (Boolean) ret[0];
        if (ret[1] != null) { // have a set
          prevSets.add(new Object[]{ret[1], ret[2], ret[3], ret[4], ret[5], ret[6], settedInstances, (Instance) next[0]});
          break;
        }
      }
    }
    
    // phase 3: set the rest of the solo instances (from inner methods) at last
    if (callSites.length() == 0) {
      setSoloInstances(preCond.getConditionList());
    }

    return preCond;
  }

  private void findSetInstances(String lastPath, List<Instance> preInstances, 
      Reference ref, Hashtable<String, List<Instance>> settedInstances) {
    
    // build current path
    StringBuilder str = new StringBuilder();
    str.append(lastPath).append(lastPath.length() > 0 ? "." : "").append(ref.getName());
    String path = str.toString();
    
    // look through all instances and old instances
    List<Instance> pathInstances = null;
    List<Instance> refInstances = new ArrayList<Instance>(ref.getInstances());
    refInstances.addAll(ref.getOldInstances());
    refInstances.removeAll(preInstances); // avoid recursions
    for (Instance refInstance : refInstances) {
      List<Instance> allInstances = (refInstance.isBounded()) ? 
                                      new ArrayList<Instance>() : 
                                      new ArrayList<Instance>(refInstance.getBoundedValues());
      allInstances.add(refInstance);

      preInstances.add(refInstance);
      for (Instance instance : allInstances) {
        
        if (instance.getSetValueTime() != Long.MIN_VALUE) { // this instance is set/store
          if (pathInstances == null) {
            pathInstances = settedInstances.get(path);
            if (pathInstances == null) {
              pathInstances = new ArrayList<Instance>();
              settedInstances.put(path, pathInstances);
            }
          }
          pathInstances.add(instance);
          //System.err.println("Found: " + path + ": " + instance);
        }

        // recursive for instance's fields
        String path2 = instance.isBounded() ? instance.getValue() : path;
        for (Reference fieldRef : instance.getFields()) {
          findSetInstances(path2, preInstances, fieldRef, settedInstances);
        }
      }
      preInstances.remove(preInstances.size() - 1);
    }
  }

  private void getSetSequence(List<Instance> preInstances, 
      List<Reference> preReferences, Reference ref, List<Object[]> sequence) {
    
    List<Instance> refInstances = new ArrayList<Instance>(ref.getInstances());
    refInstances.addAll(ref.getOldInstances());
    refInstances.removeAll(preInstances); // avoid recursions
    for (Instance refInstance : refInstances) {
      sequence.add(new Object[] {refInstance, ref, preInstances, preReferences});
        
      // recursive for instance's fields
      List<Instance> preInstances2   = new ArrayList<Instance>(preInstances);
      List<Reference> preReferences2 = new ArrayList<Reference>(preReferences);
      preInstances2.add(refInstance);
      preReferences2.add(ref);
      for (Reference fieldRef : refInstance.getFields()) {
        getSetSequence(preInstances2, preReferences2, fieldRef, sequence);
      }
    }
  }

  private Object[] setEquivalentInstances(Formula preCond, Instance refInstance, 
      Reference ref, List<Instance> preInstances, List<Reference> preReferences, 
      Hashtable<String, List<Instance>> settedInstances, String callSites, int setIndex) {

    StringBuilder str = new StringBuilder();
    for (int i = preInstances.size() - 1; i >= 0; i--) {
      if (preInstances.get(i).isBounded()) {
        str.insert(0, str.length() > 0 ? "." : "").insert(0, preInstances.get(i).getValue());
        break;
      }
      else {
        str.insert(0, str.length() > 0 ? "." : "").insert(0, preReferences.get(i).getName());
      }
    }
    str.append(".").append(ref.getName());
    String path = str.toString();

    Object[] ret = new Object[]{false, null, null, null, null, null, null};
    List<Instance> pathSettedInstances = settedInstances.get(path);
    Long[] lifeTime = ref.getLifeTime(refInstance);
    if (pathSettedInstances != null && refInstance.getSetValueTime() == Long.MIN_VALUE && lifeTime != null) {
      Instance nearestSetted = null;
      Long nearestSettedTime = null;

      for (Instance settedInstance : pathSettedInstances) {
        Long setTime = settedInstance.getSetValueTime();
        if (setTime > lifeTime[0] && setTime < lifeTime[1] && 
            (nearestSettedTime == null || setTime < nearestSettedTime)) {
          nearestSetted = settedInstance;
          nearestSettedTime = setTime;
        }
      }

      if (nearestSetted != null) {
        try {
          if (callSites.length() > 0 && !ref.canReferenceSetValue() && !refInstance.getInitCallSites().equals(callSites)) {
            ret[0] = refInstance.storeValue(nearestSetted);
            //System.err.println("Store: " + path + ": " + nearestSetted);
          }
          else {
            Hashtable<Object, Object> cloneMap = new Hashtable<Object, Object>();
            //Formula clone = preCond.clone(cloneMap);
            Formula clone = setIndex % 5 == 0 ? preCond.clone(cloneMap) : null; // clone in 1 out of 5 times
            refInstance.setValueInclSetTime(nearestSetted, Math.min(nearestSetted.getSetValueTime(), lifeTime[1]));
            //System.err.println("Set: " + path + ": " + nearestSetted + ". Re-find set values...");
            ret = new Object[] {true, path, nearestSetted.getValue(), 
                                nearestSettedTime, lifeTime, clone, cloneMap}; // once set, return right away
          }
        } catch (Exception e) {e.printStackTrace();}
      }
    }

    return ret;
  }
  
  private void setSoloInstances(List<Condition> conditionList) {
    for (Condition condition : conditionList) {
      List<ConditionTerm> conditionTerms = condition.getConditionTerms();
      for (ConditionTerm term : conditionTerms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          setSoloInstance(instance);
        }
      }
    }
  }
  
  private void setSoloInstance(Instance instance) {
    if (!instance.isBounded() && instance.getBoundedValues().size() > 0) {
      // supposedly there is only one bounded value at most 
      // from previous setEquivalentInstances method
      try {
        instance.setValue(instance.getBoundedValues().iterator().next());
      } catch (Exception e) {e.printStackTrace();}
    }
    else if (instance.isBounded() && !instance.isAtomic()) {
      setSoloInstance(instance.getLeft());
      setSoloInstance(instance.getRight());
    }
  }
}
