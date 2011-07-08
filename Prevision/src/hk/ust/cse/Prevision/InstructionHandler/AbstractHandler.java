package hk.ust.cse.Prevision.InstructionHandler;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.InvalidStackTraceException;
import hk.ust.cse.Prevision.Predicate;
import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.WeakestPrecondition.GlobalOptionsAndStates;
import hk.ust.cse.Prevision.WeakestPreconditionResult;
import hk.ust.cse.Wala.MethodMetaData;

import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public abstract class AbstractHandler {
  
  private static final String s_regExpInstStr = "(?:v[\\d]+ = )*([\\p{Alpha}]+[ ]*[\\p{Alpha}]+)(?:\\([\\w]+\\))*(?: <[ \\S]+)*";
  private static final Pattern s_instPattern  = Pattern.compile(s_regExpInstStr);
  
  public abstract Predicate handle_arraylength(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_arrayload(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_arraystore(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_binaryop(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_catch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_checkcast(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_compare(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_conversion(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Predicate handle_conditional_branch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_getfield(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_getstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Predicate handle_goto(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_instanceof(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_invokeinterface(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Predicate handle_invokevirtual(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Predicate handle_invokespecial(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Predicate handle_invokestatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_invokeinterface_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates);

  public abstract Predicate handle_invokevirtual_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates);

  public abstract Predicate handle_invokespecial_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates);

  public abstract Predicate handle_invokestatic_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates);
  
  public abstract Predicate handle_monitorenter(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_monitorexit(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_neg(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_new(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_phi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_pi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_putfield(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_putstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_return(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_switch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Predicate handle_throw(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Predicate handle_entryblock(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo);
  
  // handler for ShrikeCFG ConstantInstruction instruction
  public abstract Predicate handle_constant(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, String constantStr);

  public final Predicate handle(GlobalOptionsAndStates optionsAndStates, 
        CGNode method, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
        CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {

    Predicate preCond = null;
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
              !instInfo.wp.isCallStackInvokeInst(instInfo, inst))) {
            // invoke handler for this instruction
            Method rmethod = this.getClass().getMethod("handle_" + instType,
                Predicate.class, SSAInstruction.class, BBorInstInfo.class);
            preCond = (Predicate) rmethod.invoke(this, postCond, inst, instInfo);
          }
          else {
            System.out.println("stepping into " + instType + "...");
            // invoke handler for this instruction
            Method rmethod = this.getClass().getMethod("handle_" + instType + "_stepin", 
                GlobalOptionsAndStates.class, CGNode.class, Predicate.class, SSAInstruction.class, 
                BBorInstInfo.class, CallStack.class, int.class, List.class);
            preCond = (Predicate) rmethod.invoke(this, optionsAndStates, method, 
                postCond, inst, instInfo, callStack, curInvokeDepth, usedPredicates);
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
  
  protected static final Hashtable<String, List<String>> mapParamsToMethod(
      SSAInvokeInstruction invokeInst, BBorInstInfo instInfo,
      MethodMetaData methData, String ref, String def, String newValPrefix, 
      String newRef, List<String> params, List<String> newParams,
      Hashtable<String, List<String>> newVarMap, Predicate postCond) {

    // if not static
    boolean isStatic = ref == null;
    if (!isStatic) {
      // add ref (v1 == this)
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, ref, newRef);
    }

    // add params
    for (int i = 0; i < params.size(); i++) {
      String valnum = params.get(i);

      // valnum inside invocation
      String newParam = "v" + newValPrefix + (i + (isStatic ? 1 : 2));
      newParams.add(newParam);
      if (valnum.startsWith("v")) {
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, valnum, newParam);

        // this is necessary, because when wp.computeRec returns, it is possible
        // that its smtStatements contain statements that have newParam elements
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, newParam, null);
      }
    }

    // substitute def with a "RET", which will be replaced with a
    // real return variable in the exit block
    if (invokeInst.getDef() != -1) {
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, "RET");
    }
    
    return newVarMap;
  }
  
  protected static final Hashtable<String, List<String>> mapParamsFromMethod(
      MethodMetaData methData, String ref, String newRef, List<String> params, 
      List<String> newParams, Hashtable<String, List<String>> newVarMap, 
      Predicate preCond) {

    // if not static
    boolean isStatic = ref == null;
    if (!isStatic) {
      newVarMap = substituteVarMapKey(preCond, methData, newVarMap, newRef, ref);
    }

    // set params back
    for (int i = 0; i < newParams.size(); i++) {
      String valnum = params.get(i);
      String newParam = newParams.get(i);
      
      newVarMap = substituteVarMapKey(preCond, methData, newVarMap, newParam, valnum);
    }
    
    return newVarMap;
  }

  protected static final Predicate computeToEnterCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String newValPrefix, 
      Predicate newPostCond) {
    Predicate preCond = null;
    
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
    boolean inclLine = true;
    int startingInst = -1;
    if (innerCallStack.getDepth() == 1) {
      inclLine     = optAndStates.inclInnerMostLine;
      startingInst = optAndStates.startingInst;
    }

    try {
      WeakestPreconditionResult wpResult = instInfo.wp.computeRec(optAndStates, 
          methodNode, methodSig, lineNo, startingInst, inclLine, innerCallStack, 
          curInvokeDepth, newValPrefix, newPostCond);
      preCond = wpResult.getFirstSatisfiable();
    } catch (InvalidStackTraceException e) {}
    
    return preCond;
  }
  
  protected static final Predicate computeAtCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String newValPrefix, 
      Predicate newPostCond) {
    Predicate preCond = null;
  
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
    boolean noMatch = false;
    if (optAndStates.summary != null) {
      preCond = optAndStates.summary.getSummary(methodSig, curInvokeDepth + 1,
          newValPrefix, newPostCond);
      if (preCond == null) {
        noMatch = true;
      }
    }

    // if no summary, compute
    if (preCond == null) {
      try { 
        // call compute, startLine = -1 (from exit block)
        WeakestPreconditionResult wpResult = instInfo.wp.computeRec(optAndStates, 
            methodNode, methodSig, -1, -1, false, callStack, curInvokeDepth + 1, 
            newValPrefix, newPostCond);
        preCond = wpResult.getFirstSatisfiable();
      } catch (InvalidStackTraceException e) {}
    }
    
    // save to summary
    if (noMatch && preCond != null) {
      // use the original method signature
      methodSig = invokeInst.getDeclaredTarget().getSignature();
      
      optAndStates.summary.putSummary(methodSig, curInvokeDepth + 1,
          newValPrefix, newPostCond, preCond);
    }
    
    return preCond;
  }
  
  protected static final List<List<String>> addSMTStatments(List<List<String>> oldSMTStatmentList, 
      List<List<String>> smtStatements) {
    // create new list
    int len1 = oldSMTStatmentList.size();
    int len2 = smtStatements.size();
    List<List<String>> newSMTStatements = new ArrayList<List<String>>(len1 + len2);

    // copy elements from old list
    newSMTStatements.addAll(oldSMTStatmentList);

    // add new statements
    newSMTStatements.addAll(smtStatements);

    return newSMTStatements;
  }
  
  /**
   * create a new varMap clone only when necessary
   */
  protected static final Hashtable<String, List<String>> addVars2VarMap(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMap, String newVar1, String newVar2) {

    boolean isNewClone = false;
    if (newVar1 != null/* && 
        (newVar1.startsWith("v") || newVar1.startsWith("#"))*/) {
      // clone only when necessary
      if (varMap == postCond.getVarMap()) {
        varMap = postCond.getVarMapClone();
      }
      isNewClone = true;

      // if newVar1 it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newVar1);
      String key = (param == null) ? newVar1 : param;
      List<String> varList = varMap.get(key);
      if (varList != null) {
        // add new var to varlist
        varList.add(newVar1);
      }
      else {
        varList = new ArrayList<String>();
        varList.add(newVar1);
        varMap.put(key, varList);
      }
    }

    if (newVar2 != null/* && 
        (newVar2.startsWith("v") || newVar2.startsWith("#"))*/) {
      if (!isNewClone) {
        // clone only when necessary
        if (varMap == postCond.getVarMap()) {
          varMap = postCond.getVarMapClone();
        }
        isNewClone = true;
      }

      // if newVar2 it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newVar2);
      String key = (param == null) ? newVar2 : param;
      List<String> varList = varMap.get(key);
      if (varList != null) {
        // add new var to varlist
        varList.add(newVar2);
      }
      else {
        varList = new ArrayList<String>();
        varList.add(newVar2);
        varMap.put(key, varList);
      }
    }

    return varMap;
  }

  /**
   * create a new varMap clone only when necessary
   */
  protected static final Hashtable<String, List<String>> addVars2VarMap(
      Predicate postCond, MethodMetaData methData,
      Hashtable<String, List<String>> varMap, List<String> newVars) {
    
    boolean isNewClone = false;
    for (String var : newVars) {
      if (var != null/* && 
          (var.startsWith("v") || var.startsWith("#"))*/) {
        if (!isNewClone) {
          // clone only when necessary
          if (varMap == postCond.getVarMap()) {
            varMap = postCond.getVarMapClone();
          }
          isNewClone = true;
        }

        // if var it's a parameter, substitute with
        // the parameter name right away!
        // because we're using valPrefix, a param name will be returned only
        // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix
        // == "")
        // we don't want to replace with a param name if it's still inside some
        // invocation, because it's hard to replace a param at the outter
        // handle_invoke*_stepin
        String param = methData.getParamStr(var);
        String key = (param == null) ? var : param;
        // add new var to varlist
        List<String> varList = varMap.get(key);
        if (varList != null) {
          varList.add(var);
        }
        else {
          varList = new ArrayList<String>();
          varList.add(var);
          varMap.put(key, varList);
        }
      }
    }

    return varMap;
  }

  /**
   * might create a new phiMap and a new varMap clone
   */
  protected static final Hashtable<?, ?>[] assignPhiValue(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMap, String def) {
    Hashtable<String, String> newPhiMap   = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap  = postCond.getDefMap();

    // whenever 
    if (def.startsWith("v")) {
      newDefMap = addDef2DefMap(postCond, newDefMap, def);
    }

    // check if the newly def variable is a
    // potential concrete value to any phi variables
    String phiVar = newPhiMap.get(def);
    if (phiVar != null) {
      // assign concrete value to the phi variable
      // modify directly on this varMap
      varMap = substituteVarMapKey(postCond, methData, varMap, phiVar, def);
      
      // remove phiVar from phiList because it
      // is concrete now
      newPhiMap = postCond.getPhiMapClone();
      Enumeration<String> keys = newPhiMap.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        if (newPhiMap.get(key).equals(phiVar)) {
          newPhiMap.remove(key);
        }
      }
    }
    
    // create return
    return new Hashtable<?, ?>[] {varMap, newPhiMap, newDefMap};
  }

  /**
   * create a new varMap clone only when necessary
   */
  protected static final Hashtable<String, List<String>> substituteVarMapKey(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMaptoSub,
      String oldKey, String newKey) {

    // value never change
    List<String> varList1 = varMaptoSub.get(oldKey);
    if (varList1 != null) {
      // create clone on demand
      if (varMaptoSub == postCond.getVarMap()) {
        varMaptoSub = postCond.getVarMapClone();
      }
      varMaptoSub.remove(oldKey);
      
      // if newKey it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newKey);
      String key = (param == null) ? newKey : param;
      List<String> varList2 = varMaptoSub.get(key);
      if (varList2 != null) {
        for (String var1 : varList1) {
          varList2.add(var1);
        }
      }
      else {
        varList2 = new ArrayList<String>();
        for (String var1 : varList1) {
          varList2.add(var1);
        }
        varMaptoSub.put(key, varList2);
      }
    }

    return varMaptoSub;
  }

  protected static final Hashtable<String, Integer> addDef2DefMap(
      Predicate postCond, Hashtable<String, Integer> defMap, String def) {

    if (def.startsWith("v")) {
      // get a clone
      if (defMap == postCond.getDefMap()) {
        defMap = postCond.getDefMapClone();
      }

      // change back to the original form
      int cut = def.lastIndexOf('@');
      if (cut >= 0) {
        def = def.substring(0, cut);
      }

      Integer count = defMap.get(def);
      if (count != null) {
        defMap.put(def, count + 1);
      }
      else {
        defMap.put(def, 1);
      }
    }
    
    return defMap;
  }
  
  /**
   * Assuming there is at most one 'Caught ...' at a time.
   */
  protected static final String findCaughtExceptionTypeStr(Predicate predicate) {
    String caughtStr = null;
    if (predicate.getVarMap().containsKey("Caught")) {
      caughtStr = predicate.getVarMap().get("Caught").get(0);
    }
    return caughtStr;
  }
  
  protected static final Hashtable<String, List<String>> setExceptionCaught(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String caughtExcepTypeStr) {
    // create clone on demand
    if (varMapToSet == predicate.getVarMap()) {
      varMapToSet = predicate.getVarMapClone();
    }
    
    // put "Caught"
    List<String> caught = new ArrayList<String>();
    caught.add(caughtExcepTypeStr);
    varMapToSet.put("Caught", caught);
    return varMapToSet;
  }
  
  protected static final Hashtable<String, List<String>> setExceptionTriggered(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String triggeredExcepTypeStr) {
    if (varMapToSet.containsKey("Caught")) {
      List<String> caughtExcepTypeStr = varMapToSet.get("Caught");
      
      if (caughtExcepTypeStr.get(0).equals(triggeredExcepTypeStr)) {
        // create clone on demand
        if (varMapToSet == predicate.getVarMap()) {
          varMapToSet = predicate.getVarMapClone();
        }
        
        // replace "Caught" with "Triggered"
        varMapToSet.put("Triggered", caughtExcepTypeStr);
        varMapToSet.remove("Caught");
      }
    }
    return varMapToSet;
  }
  
  protected static final Hashtable<String, List<String>> setExceptionThrownCurrent(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String exceptionVal) {
    // create clone on demand
    if (varMapToSet == predicate.getVarMap()) {
      varMapToSet = predicate.getVarMapClone();
    }
    
    // put "ThrownInstCurrent"
    List<String> thrownInst = new ArrayList<String>();
    thrownInst.add(exceptionVal);
    varMapToSet.put("ThrownInstCurrent", thrownInst);
    return varMapToSet;
  }
  
  protected static final Hashtable<String, List<String>> checkExceptionThrown(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet) {
    if (varMapToSet.containsKey("Caught") && varMapToSet.containsKey("ThrownInstCurrent")) {
      String caughtExcepTypeStr = varMapToSet.get("Caught").get(0);
      
      // get the corresponding final variable
      String excepVar = varMapToSet.get("ThrownInstCurrent").get(0);
      String finalExcepVar = null;
      Enumeration<String> finalvars = varMapToSet.keys();
      while (finalvars.hasMoreElements()) {
        String finalvar = (String) finalvars.nextElement();
        
        if (!finalvar.equals("ThrownInstCurrent")) {
          List<String> midVars = predicate.getVarMap().get(finalvar);
          if (midVars.contains(excepVar)) {
            finalExcepVar = finalvar;
            break;
          }
        }
      }
      
      if (finalExcepVar != null) {
        // very sloppy! Cannot deal with super class. Only 
        // works if the exact exception type is used.
        if (finalExcepVar.contains("(" + caughtExcepTypeStr + ")")) {
          // e.g., FreshInstanceOf(Ljava/lang/NullPointerException), 
          // (Ljava/lang/NullPointerException) param1, 
          // (Ljava/lang/NullPointerException) param1.field1
          
          // the thrown instance is the same type as the caught exception
          varMapToSet = setExceptionTriggered(predicate, varMapToSet, caughtExcepTypeStr);
        }
      }
    }
    
    // remove "ThrownInstCurrent" no matter how
    if (varMapToSet.containsKey("ThrownInstCurrent")) {
      // create clone on demand
      if (varMapToSet == predicate.getVarMap()) {
        varMapToSet = predicate.getVarMapClone();
      }
      varMapToSet.remove("ThrownInstCurrent");
    }
    
    return varMapToSet;
  }
  
  protected static final Predicate defaultHandler(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    // not implement
    return new Predicate(postCond.getSMTStatements(), postCond.getVarMap(),
        postCond.getPhiMap(), postCond.getDefMap());
  }
}
