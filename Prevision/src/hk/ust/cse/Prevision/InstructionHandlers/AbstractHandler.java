package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BBorInstInfo;
import hk.ust.cse.Prevision.VirtualMachine.Executor.GlobalOptionsAndStates;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Wala.MethodMetaData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;

public abstract class AbstractHandler {
  
  private static final String s_regExpInstStr = "(?:v[\\d]+ = )*([\\p{Alpha}]+[ ]*[\\p{Alpha}]+)(?:\\([\\w]+\\))*(?: <[ \\S]+)*";
  private static final Pattern s_instPattern  = Pattern.compile(s_regExpInstStr);
  
  public abstract Formula handle_arraylength(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_arrayload(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_arraystore(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_binaryop(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_catch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_checkcast(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_compare(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_conversion(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Formula handle_conditional_branch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_getfield(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_getstatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Formula handle_goto(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_instanceof(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_invokeinterface(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokevirtual(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokespecial(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokestatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_invokeinterface_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokevirtual_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokespecial_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokestatic_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);
  
  public abstract Formula handle_monitorenter(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_monitorexit(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_neg(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_new(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_phi(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, int phiVarID, boolean needClone);
  
  public abstract Formula handle_pi(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_putfield(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_putstatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_return(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_switch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_throw(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_entryblock(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public final Formula handle(GlobalOptionsAndStates optionsAndStates, CGNode method, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {

    Formula preCond = null;
    try {
      Matcher matcher = s_instPattern.matcher(inst.toString());
      if (matcher.find()) {
        String instType = matcher.group(1).toString();
        if (instType != null && instType.length() > 0) {
          // a hack to handle checkcast
          if (instType.startsWith("checkcast")) {
            instType = "checkcast";
          }
          System.out.println("handling " + instType + "...");

          // eliminate spaces in instruction names
          instType = instType.replace(' ', '_');
          
          if (!instType.startsWith("invoke") || 
              (!optionsAndStates.isEnteringCallStack() && 
               curInvokeDepth >= optionsAndStates.maxInvokeDepth && 
              !instInfo.executor.isCallStackInvokeInst(instInfo, inst))) {
            // invoke handler for this instruction
            Method rmethod = this.getClass().getMethod("handle_" + instType,
                Formula.class, SSAInstruction.class, BBorInstInfo.class);
            preCond = (Formula) rmethod.invoke(this, postCond, inst, instInfo);
          }
          else {
            System.out.println("stepping into " + instType + "...");
            // invoke handler for this instruction
            Method rmethod = this.getClass().getMethod("handle_" + instType + "_stepin", 
                GlobalOptionsAndStates.class, CGNode.class, Formula.class, SSAInstruction.class, 
                BBorInstInfo.class, CallStack.class, int.class);
            preCond = (Formula) rmethod.invoke(this, optionsAndStates, method, 
                postCond, inst, instInfo, callStack, curInvokeDepth);
          }
        }
        else {
          System.err.println("Unknown instruction string: " + inst.toString());
        }
      }
      else {
        System.err.println("Unknown instruction string: " + inst.toString());
      }
    } catch (NoSuchMethodException e) {
      System.err.println("No Handler defined for instruction: " + inst.toString());
    } catch (Exception e2) {
      e2.printStackTrace();
    }    
    return preCond;
  }
  
  /**
   * create a new refMap clone only when necessary
   * create necessary 'this', 'parameter' and 'return' references for the invocation
   */
  protected static final void beforeInvocation(SSAInvokeInstruction invokeInst, 
      Reference ref, Reference def, List<Reference> params, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {

    String thisCallSite = String.format("%04d", invokeInst.getProgramCounter());
    
    List<Reference> oldReferences = new ArrayList<Reference>();
    
    // new reference of 'v1' (this) for the new method
    if (!invokeInst.isStatic() && ref != null) {
      if (findReference(ref.getName(), ref.getCallSites(), refMap) != null) {
        // substitute refRef by thisRef for callee method, take over all instances
        Reference thisRef = new Reference("v1", ref.getType(), 
            ref.getCallSites() + thisCallSite, ref.getInstances(), null);
        oldReferences.add(ref);
        addRefToRefMap(refMap, thisRef);
      }
    }

    // new references of 'v2, v3...' (parameters) for the new method
    for (int i = 0, size = params.size(); i < size; i++) {
      Reference paramRef = params.get(i);
      if (paramRef != null && findReference(paramRef.getName(), paramRef.getCallSites(), refMap) != null) {
        String newParamName = "v" + (i + (invokeInst.isStatic() ? 1 : 2));
        Reference newParamRef = new Reference(newParamName, paramRef.getType(), 
            paramRef.getCallSites() + thisCallSite, paramRef.getInstances(), null);
        oldReferences.add(paramRef);
        addRefToRefMap(refMap, newParamRef);
      }
    }

    // new reference of the return value for the new method
    // will be replaced with a real return reference name in the exit block
    if (invokeInst.getDef() != -1 && def != null) {
      if (findReference(def.getName(), def.getCallSites(), refMap) != null) {
        Reference returnRef = new Reference("RET", def.getType(), 
            def.getCallSites() + thisCallSite, def.getInstances(), null);
        oldReferences.add(def);
        addRefToRefMap(refMap, returnRef);
      }
    }
    
    // put to old
    for (Reference oldReference : oldReferences) {
      oldReference.putInstancesToOld();
    }
  }
  
  /**
   * if we have finished an invocation, remove all references of this method
   * from refMap, re-assign lastRef
   */
  protected static final void afterInvocation(SSAInvokeInstruction invokeInst, 
      String callSites, String refName, String defName, List<String> paramNames, 
      Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    String thisCallSite = String.format("%04d", invokeInst.getProgramCounter());
    
    Hashtable<String, Reference> calleeRefs = refMap.get(callSites + thisCallSite);
    if (calleeRefs != null) {
      // new reference of 'v1' (this) for the new method
      if (!invokeInst.isStatic()) {
        Reference thisRef = calleeRefs.get("v1");
        if (thisRef != null) {
          Reference refRef = findReference(refName, callSites, refMap);
          if (refRef == null) { // thisRef is add during callee method
            refRef = new Reference(refName, thisRef.getType(), callSites, thisRef.getInstances(), null);
            addRefToRefMap(refMap, refRef);
          }
          else {
            try {
              refRef.assignInstance(thisRef.getInstances());
              thisRef.putInstancesToOld();
            } catch (Exception e) {e.printStackTrace();}
          }
        }
        // no need to putInstancesToOld as thisRef will be removed later
      }

      // new references of 'v2, v3...' (parameters) for the new method
      for (int i = 0, size = paramNames.size(); i < size; i++) {
        String paramName = paramNames.get(i);
        if (paramName != null) {
          String newParamName = "v" + (i + (invokeInst.isStatic() ? 1 : 2));
          Reference newParamRef = calleeRefs.get(newParamName);
          if (newParamRef != null) {
            Reference paramRef = findReference(paramName, callSites, refMap);
            if (paramRef == null) { // newParamRef is add during callee method
              paramRef = new Reference(paramName, newParamRef.getType(), callSites, new Instance(), null);
              if (paramRef.getInstance().isBounded() /* constant such as #!0 */ ) {
                newParamRef.setInstancesValue(paramRef.getInstance());
                newParamRef.putInstancesToOld();
              }
              else {
                // make paramRef point to newParamRef.getInstances()
                // since newParamRef in refMap, add paramRef also
                try {
                  paramRef.assignInstance(newParamRef.getInstances());
                  newParamRef.putInstancesToOld();
                } catch (Exception e) {e.printStackTrace();}
                addRefToRefMap(refMap, paramRef);
              }
            }
            else {
              try {
                paramRef.assignInstance(newParamRef.getInstances());
              } catch (Exception e) {e.printStackTrace();}
            }
          }
        }
      }

//      // bound 'ret''s instance to def reference
//      if (invokeInst.getDef() != -1) {
//        Reference defRef = findReference(defName, callSites, refMap);
//        if (defRef != null) {
//
//        }
//      }
    }

    removeMethodReferences(invokeInst, callSites, refMap);
    removeMethodDefCounts(invokeInst, callSites, defMap);
  }
  
  /**
   * would not make a new clone
   */
  private static void removeMethodReferences(SSAInvokeInstruction invokeInst, 
      String callSites, Hashtable<String, Hashtable<String, Reference>> refMap) {
    refMap.remove(callSites + String.format("%04d", invokeInst.getProgramCounter()));
  }
  
  /**
   * would not make a new clone
   */
  private static void removeMethodDefCounts(SSAInvokeInstruction invokeInst, 
      String callSites, Hashtable<String, Hashtable<String, Integer>> defMap) {
    defMap.remove(callSites + String.format("%04d", invokeInst.getProgramCounter()));
  }
  
  protected static final Formula computeToEnterCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String callSites, Formula postCond) {
    Formula preCond = null;
    
    // get method signature
    assert(instInfo.target.getKey().equals(invokeInst));
    String methodSig  = null;
    CGNode methodNode = null;
    if (instInfo.target.getValue() != null) {
      methodSig  = instInfo.target.getValue().getMethod().getSignature();
      methodNode = instInfo.target.getValue();
    }
    else {
      methodSig  = invokeInst.getDeclaredTarget().getSignature();
      methodNode = null;
    }
    
    // get inner call stack
    CallStack innerCallStack = callStack.getInnerCallStack();
    int lineNo = innerCallStack.getCurLineNo();
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = (innerCallStack.getDepth() == 1) ? optAndStates.inclInnerMostLine : true;
    int startingInst = (innerCallStack.getDepth() == 1) ? optAndStates.startingInst : -1;

    // create workList if necessary
    instInfo.workList = (instInfo.workList == null) ? new Stack<BBorInstInfo>() : instInfo.workList;
    
    try {
      String newCallSites = callSites + String.format("%04d", invokeInst.getProgramCounter());
      ExecutionResult execResult = instInfo.executor.computeRec(optAndStates, 
          methodNode, methodSig, lineNo, startingInst, inclLine, innerCallStack, 
          curInvokeDepth, newCallSites, instInfo.workList, postCond);
      preCond = execResult.getFirstSatisfiable();
    } catch (InvalidStackTraceException e) {}
    
    return preCond;
  }
  
  protected static final Formula computeAtCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String callSites, Formula newPostCond) {
    Formula preCond = null;
  
    // get method signature
    assert(instInfo.target.getKey().equals(invokeInst));
    String methodSig  = null;
    CGNode methodNode = null;
    if (instInfo.target.getValue() != null) {
      methodSig  = instInfo.target.getValue().getMethod().getSignature();
      methodNode = instInfo.target.getValue();
    }
    else {
      methodSig  = invokeInst.getDeclaredTarget().getSignature();
      methodNode = null;
    }
    
    // get from summary
//    boolean noMatch = false;
//    if (optAndStates.summary != null) {
//      preCond = optAndStates.summary.getSummary(methodSig, curInvokeDepth + 1,
//          callSites, newPostCond);
//      if (preCond == null) {
//        noMatch = true;
//      }
//    }

    // if no summary, compute
    if (preCond == null) {
      // create workList if necessary
      instInfo.workList = (instInfo.workList == null) ? new Stack<BBorInstInfo>() : instInfo.workList;
      
      try { 
        // call compute, startLine = -1 (from exit block)
        String newCallSites = callSites + String.format("%04d", invokeInst.getProgramCounter());
        ExecutionResult exeResult = instInfo.executor.computeRec(optAndStates, 
            methodNode, methodSig, -1, -1, false, callStack, curInvokeDepth + 1, 
            newCallSites, instInfo.workList, newPostCond);
        preCond = exeResult.getFirstSatisfiable();
      } catch (InvalidStackTraceException e) {}
    }
    
    // save to summary
//    if (noMatch && preCond != null) {
//      // use the original method signature
//      methodSig = invokeInst.getDeclaredTarget().getSignature();
//      optAndStates.summary.putSummary(methodSig, curInvokeDepth + 1,
//          newValPrefix, newPostCond, preCond);
//    }
    
    return preCond;
  }
  
  protected static final List<Condition> addConditions(
      List<Condition> oldConditionList, List<Condition> conditionList) {
    // create new list
    int len1 = oldConditionList.size();
    int len2 = conditionList.size();
    List<Condition> newConditionList = new ArrayList<Condition>(len1 + len2);

    // copy elements from old list
    newConditionList.addAll(oldConditionList);
    // add new statements
    newConditionList.addAll(conditionList);
    return newConditionList;
  }
  
  /**
   * would not make a new clone
   */
  protected static final void addRefToRefMap(
      Hashtable<String, Hashtable<String, Reference>> refMap, Reference newReference) {
    
    if (!newReference.isConstantReference()) { // do not add constant refs to refMap
      Hashtable<String, Reference> methodRefs = refMap.get(newReference.getCallSites());
      if (methodRefs == null) {
        methodRefs = new Hashtable<String, Reference>();
        refMap.put(newReference.getCallSites(), methodRefs);
      }
      methodRefs.put(newReference.getName(), newReference);
    }
  }

  /**
   * would not make a new clone
   */
  protected static final void addDefToDefMap(Hashtable<String, Hashtable<String, Integer>> defMap, Reference def) {
    if (def.isSSAVariable()) {
      Hashtable<String, Integer> methodDefCounts = defMap.get(def.getCallSites());
      if (methodDefCounts == null) {
        methodDefCounts = new Hashtable<String, Integer>();
        defMap.put(def.getCallSites(), methodDefCounts);
      }

      // change back to the original form
      String defName = def.getName();
      int cut = defName.lastIndexOf('@');
      defName = (cut >= 0) ? defName.substring(0, cut) : defName;
      
      // increment def count
      Integer defCount = methodDefCounts.get(defName);
      methodDefCounts.put(defName, (defCount == null) ? 1 : defCount + 1);
    }
  }
  
  protected static final Reference findOrCreateReference(String refName, 
      String refType, String callSites, Hashtable<String, Hashtable<String, Reference>> refMap) {
    return findOrCreateReference(refName, refType, callSites, null, refMap);
  }
  
  protected static final Reference findOrCreateReference(String refName, 
      String refType, String callSites, Instance declInstance, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {
    
    Reference ref = findReference(refName, callSites, refMap);
    // cannot find, create a new one
    if (ref == null) {
      ref = new Reference(refName, refType, callSites, new Instance() /* unbounded instance */, declInstance);
    }
    return ref;
  }
  
  protected static final Reference findReference(String refName, String callSites, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {
    
    Reference ref = null;
    Hashtable<String, Reference> methodRefs = refMap.get(callSites);
    if (methodRefs != null) {
      ref = methodRefs.get(refName);
    }
    return ref;
  }
  
  protected static final boolean containsRef(String refName, String callSites, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {

    Hashtable<String, Reference> methodRefs = refMap.get(callSites);
    return methodRefs != null && methodRefs.containsKey(refName);
  }
  
  protected static final boolean containsFieldName(String fieldName, Formula formula) {
    HashSet<String> allFieldNames = findAllFieldNames(formula);
    return allFieldNames.contains(fieldName);
  }
  
  private static HashSet<String> findAllFieldNames(Formula formula) {
    List<String> allFieldNames = new ArrayList<String>();
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    for (Hashtable<String, Reference> methodRefs : refMap.values()) {
      for (Reference ref : methodRefs.values()) {
        findAllFieldNames(ref, allFieldNames, false);
      }
    }
    return new HashSet<String>(allFieldNames);
  }
  
  private static void findAllFieldNames(Reference ref, List<String> allFieldNames, boolean isField) {
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
  
  protected static final void assignInstance(Reference defRef, Reference fromRef,
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
        fromRef.assignInstance(defRef.getInstances());
        defRef.putInstancesToOld();
      } catch (Exception e) {e.printStackTrace();}
      // put fromRef to refMap if defRef is in refMap
      if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
        addRefToRefMap(newRefMap, fromRef);
        newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
      }
    }
  }
  
  protected static final void assignInstance(Reference defRef, Instance fromInstance,
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
  
//  
//  /**
//   * Assuming there is at most one 'Caught ...' at a time.
//   */
//  protected static final String findCaughtExceptionTypeStr(Predicate predicate) {
//    String caughtStr = null;
//    if (predicate.getVarMap().containsKey("Caught")) {
//      caughtStr = predicate.getVarMap().get("Caught").get(0);
//    }
//    return caughtStr;
//  }
//  
//  protected static final Hashtable<String, List<String>> setExceptionCaught(
//      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String caughtExcepTypeStr) {
//    // create clone on demand
//    if (varMapToSet == predicate.getVarMap()) {
//      varMapToSet = predicate.getVarMapClone();
//    }
//    
//    // put "Caught"
//    List<String> caught = new ArrayList<String>();
//    caught.add(caughtExcepTypeStr);
//    varMapToSet.put("Caught", caught);
//    return varMapToSet;
//  }
//  
//  protected static final Hashtable<String, List<String>> setExceptionTriggered(
//      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String triggeredExcepTypeStr) {
//    if (varMapToSet.containsKey("Caught")) {
//      List<String> caughtExcepTypeStr = varMapToSet.get("Caught");
//      
//      if (caughtExcepTypeStr.get(0).equals(triggeredExcepTypeStr)) {
//        // create clone on demand
//        if (varMapToSet == predicate.getVarMap()) {
//          varMapToSet = predicate.getVarMapClone();
//        }
//        
//        // replace "Caught" with "Triggered"
//        varMapToSet.put("Triggered", caughtExcepTypeStr);
//        varMapToSet.remove("Caught");
//      }
//    }
//    return varMapToSet;
//  }
//  
//  protected static final Hashtable<String, List<String>> setExceptionThrownCurrent(
//      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String exceptionVal) {
//    // create clone on demand
//    if (varMapToSet == predicate.getVarMap()) {
//      varMapToSet = predicate.getVarMapClone();
//    }
//    
//    // put "ThrownInstCurrent"
//    List<String> thrownInst = new ArrayList<String>();
//    thrownInst.add(exceptionVal);
//    varMapToSet.put("ThrownInstCurrent", thrownInst);
//    return varMapToSet;
//  }
//  
//  protected static final Hashtable<String, List<String>> checkExceptionThrown(
//      Predicate predicate, Hashtable<String, List<String>> varMapToSet) {
//    if (varMapToSet.containsKey("Caught") && varMapToSet.containsKey("ThrownInstCurrent")) {
//      String caughtExcepTypeStr = varMapToSet.get("Caught").get(0);
//      
//      // get the corresponding final variable
//      String excepVar = varMapToSet.get("ThrownInstCurrent").get(0);
//      String finalExcepVar = null;
//      Enumeration<String> finalvars = varMapToSet.keys();
//      while (finalvars.hasMoreElements()) {
//        String finalvar = (String) finalvars.nextElement();
//        
//        if (!finalvar.equals("ThrownInstCurrent")) {
//          List<String> midVars = predicate.getVarMap().get(finalvar);
//          if (midVars.contains(excepVar)) {
//            finalExcepVar = finalvar;
//            break;
//          }
//        }
//      }
//      
//      if (finalExcepVar != null) {
//        // very sloppy! Cannot deal with super class. Only 
//        // works if the exact exception type is used.
//        if (finalExcepVar.contains("(" + caughtExcepTypeStr + ")")) {
//          // e.g., FreshInstanceOf(Ljava/lang/NullPointerException), 
//          // (Ljava/lang/NullPointerException) param1, 
//          // (Ljava/lang/NullPointerException) param1.field1
//          
//          // the thrown instance is the same type as the caught exception
//          varMapToSet = setExceptionTriggered(predicate, varMapToSet, caughtExcepTypeStr);
//        }
//      }
//    }
//    
//    // remove "ThrownInstCurrent" no matter how
//    if (varMapToSet.containsKey("ThrownInstCurrent")) {
//      // create clone on demand
//      if (varMapToSet == predicate.getVarMap()) {
//        varMapToSet = predicate.getVarMapClone();
//      }
//      varMapToSet.remove("ThrownInstCurrent");
//    }
//    
//    return varMapToSet;
//  }
//  

  /**
   * @return if variable "varID" is a constant, return the prefixed 
   * string representing that constant, otherwise return vVarID
   */
  public static final String getSymbol(int varID, MethodMetaData methData, 
      String callSites, Hashtable<String, Hashtable<String, Integer>> defCountMap) {
    
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
        if (defCount != null && defCount > 0) {
          var += "@" + defCount;
        }
      }
    }
    return var;
  }

  private static String getConstantPrefix(int varID, MethodMetaData methData) {
    if (!methData.getSymbolTable().isConstant(varID)) {
      return "";
    }
    else if (methData.getSymbolTable().isNumberConstant(varID)) {
      return "#!";
    }
    else if (methData.getSymbolTable().isStringConstant(varID)) {
      return "##";
    }
    else {
      return "#?";
    }
  }
  
  @SuppressWarnings("unchecked")
  protected static Formula setEquivalentInstances(Formula preCond, String callSites) {
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
    for (int i = 0; changed; i++) {
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
          if (time < (Long) prevSet[2] && time > (Long) ((Long[]) prevSet[3])[0] && false) { //XXX
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

  private static void findSetInstances(String lastPath, List<Instance> preInstances, 
      Reference ref, Hashtable<String, List<Instance>> settedInstances) {
    
    // build current path
    StringBuilder str = new StringBuilder();
    str.append(lastPath);
    str.append(".");
    str.append(ref.getName());
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
        
        if (instance.getSetValueTime() != -1) { // this instance is set/store
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

  private static void getSetSequence(List<Instance> preInstances, 
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

  private static Object[] setEquivalentInstances(Formula preCond, Instance refInstance, 
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
    if (pathSettedInstances != null && refInstance.getSetValueTime() == -1 && lifeTime != null) {
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
          if (callSites.length() > 0 && !ref.canReferenceSetValue()) {
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
  
  private static void setSoloInstances(List<Condition> conditionList) {
    for (Condition condition : conditionList) {
      List<ConditionTerm> conditionTerms = condition.getConditionTerms();
      for (ConditionTerm term : conditionTerms) {
        Instance[] instances = new Instance[] {term.getInstance1(), term.getInstance2()};
        for (Instance instance : instances) {
          if (!instance.isBounded() && instance.getBoundedValues().size() > 0) {
            // supposedly there is only one bounded value at most 
            // from previous setEquivalentInstances method
            try {
              instance.setValue(instance.getBoundedValues().iterator().next());
            } catch (Exception e) {e.printStackTrace();}
          }
        }
      }
    }
  }
  
  protected static final Formula defaultHandler(Formula postCond, 
      SSAInstruction inst, BBorInstInfo instInfo) {
    return new Formula(postCond.getConditionList(), postCond.getRefMap(), postCond.getDefMap());
  }
}
