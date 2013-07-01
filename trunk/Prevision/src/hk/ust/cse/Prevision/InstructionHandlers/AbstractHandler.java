package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.BBorInstInfo;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.InvokeInstData;
import hk.ust.cse.Prevision_PseudoImpl.PseudoImplMap;
import hk.ust.cse.Wala.MethodMetaData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.TimeLimitExceededException;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public abstract class AbstractHandler {
  
  private static final String s_regExpInstStr = "(?:v[\\d]+ = )*([\\p{Alpha}]+[ _]*[\\p{Alpha}]+)(?:\\([\\w]+\\))*(?: <[ \\S]+)*";
  private static final Pattern s_instPattern  = Pattern.compile(s_regExpInstStr);
  
  public abstract Formula handle_arraylength(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_arrayload(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_arraystore(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_binaryop(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_catch(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_checkcast(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_compare(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_conversion(Formula formula, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Formula handle_conditional_branch(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_conditional_branch(Formula formula, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor);
  
  public abstract Formula handle_getfield(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_getstatic(Formula formula, SSAInstruction inst, BBorInstInfo instInfo); 
  
  public abstract Formula handle_goto(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_instanceof(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_invokeinterface(Formula formula, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokevirtual(Formula formula, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokespecial(Formula formula, SSAInstruction inst, BBorInstInfo instInfo); 

  public abstract Formula handle_invokestatic(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_invokeinterface_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula formula, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokevirtual_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula formula, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokespecial_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula formula, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_invokestatic_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula formula, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth);

  public abstract Formula handle_load_metadata(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_monitorenter(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_monitorexit(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_neg(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_new(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_phi(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_phi(Formula formula, SSAInstruction inst, BBorInstInfo instInfo, int phiVarID, ISSABasicBlock predBB);
  
  public abstract Formula handle_pi(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_putfield(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_putstatic(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_return(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_switch(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_switch(Formula formula, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor);
  
  public abstract Formula handle_throw(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public abstract Formula handle_entryblock(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);
  
  public abstract Formula handle_exitblock(Formula formula, SSAInstruction inst, BBorInstInfo instInfo);

  public final Formula handle(ExecutionOptions execOptions, CGNode method,  Formula formula, 
      SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {

    Formula preCond = null;
    try {
      Matcher matcher = s_instPattern.matcher(inst.toString());
      if (matcher.find()) {
        String instType = matcher.group(1).toString();
        if (instType != null && instType.length() > 0) {
          // a hack to handle checkcast
          instType = instType.startsWith("checkcast") ? "checkcast" : instType;
          System.out.println("handling " + instType + "...");

          // eliminate spaces in instruction names
          instType = instType.replace(' ', '_');
          
          // step into method when we are entering call stack or the current invoke depth < max depth
          boolean stepIntoInvoke = instType.startsWith("invoke") && 
              (execOptions.isEnteringCallStack() || curInvokeDepth < execOptions.maxInvokeDepth);
          
          // use reflection to call the handler of this instruction
          if (!stepIntoInvoke) {
            Method handler = this.getClass().getMethod("handle_" + instType,
                Formula.class, SSAInstruction.class, BBorInstInfo.class);
            preCond = (Formula) handler.invoke(this, formula, inst, instInfo);
          }
          else {
            System.out.println("stepping into " + instType + "...");
            Method handler = this.getClass().getMethod("handle_" + instType + "_stepin", 
                ExecutionOptions.class, CGNode.class, Formula.class, SSAInstruction.class, 
                BBorInstInfo.class, CallStack.class, int.class);
            preCond = (Formula) handler.invoke(this, execOptions, method, 
                formula, inst, instInfo, callStack, curInvokeDepth);
          }
          
          // save traversed path
          if (preCond != null) {
            preCond.addToTraversedPath(inst, instInfo.methData, instInfo.callSites);
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
   * some methods are not stepped in when met during the execution
   */
  public final void setMethodStepInFilters(String filterFilePath) {
    try {
      if (filterFilePath != null) {
        m_methodStepInFilters = new ArrayList<String>();
        
        String line = null;
        BufferedReader reader = new BufferedReader(new FileReader(filterFilePath));
        while ((line = reader.readLine()) != null) {
          if (line.length() > 0) {
            m_methodStepInFilters.add(line);
          }
        }
        reader.close();
      }
    } catch (IOException e) {}
  }
  
  protected final boolean isMethodSigFiltered(String methodName) {
    boolean filtered = false;
    if (m_methodStepInFilters != null) {
      for (int i = 0, size = m_methodStepInFilters.size(); i < size && !filtered; i++) {
        filtered |= methodName.matches(m_methodStepInFilters.get(i));
      }
    }
    return filtered;
  }
  
  /**
   * create a new refMap clone only when necessary
   * create necessary 'this', 'parameter' and 'return' references for the invocation
   */
  protected final void beforeInvocation(SSAInvokeInstruction invokeInst, Reference ref, Reference 
      def, List<Reference> params, Hashtable<String, Hashtable<String, Reference>> refMap) {

    String thisCallSite = String.format("%04d", invokeInst.getProgramCounter());
    
    List<Reference> oldReferences = new ArrayList<Reference>();
    
    // new reference of 'v1' (this) for the new method
    if (!invokeInst.isStatic() && ref != null) {
      if (findReference(ref.getName(), ref.getCallSites(), refMap) != null) {
        // substitute refRef by thisRef for callee method, take over all instances
        Reference thisRef = new Reference("v1", ref.getType(), 
            ref.getCallSites() + thisCallSite, ref.getInstances(), null, true);
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
            paramRef.getCallSites() + thisCallSite, paramRef.getInstances(), null, true);
        oldReferences.add(paramRef);
        addRefToRefMap(refMap, newParamRef);
      }
    }

    // new reference of the return value for the new method
    // will be replaced with a real return reference name in the exit block
    if (invokeInst.getDef() != -1 && def != null) {
      if (findReference(def.getName(), def.getCallSites(), refMap) != null) {
        Reference returnRef = new Reference("RET", def.getType(), 
            def.getCallSites() + thisCallSite, def.getInstances(), null, true);
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
  protected final void afterInvocation(SSAInvokeInstruction invokeInst, String callSites, ISSABasicBlock currentBB, 
      String refName, String refType, String defName, List<String> paramNames, Hashtable<String, 
      Hashtable<String, Reference>> refMap, Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    String thisCallSite = String.format("%04d", invokeInst.getProgramCounter());
    
    Hashtable<String, Reference> calleeRefs = refMap.get(callSites + thisCallSite);
    if (calleeRefs != null) {
      // new reference of 'v1' (this) for the new method
      if (!invokeInst.isStatic()) {
        Reference thisRef = calleeRefs.get("v1");
        if (thisRef != null) {
          Reference refRef = findReference(refName, callSites, refMap);
          if (refRef == null) { // thisRef is add during callee method
            refRef = new Reference(refName, refType, callSites, thisRef.getInstances(), null, true);
            addRefToRefMap(refMap, refRef);
          }
          else {
            try {
              refRef.assignInstance(thisRef.getInstances(), true);
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
              paramRef = new Reference(paramName, newParamRef.getType(), callSites, 
                  new Instance(callSites, currentBB), null, true);
              if (paramRef.getInstance().isBounded() /* constant such as #!0 */ ) {
                newParamRef.setInstancesValue(paramRef.getInstance());
                newParamRef.putInstancesToOld();
              }
              else {
                // make paramRef point to newParamRef.getInstances()
                // since newParamRef in refMap, add paramRef also
                try {
                  paramRef.assignInstance(newParamRef.getInstances(), true);
                  newParamRef.putInstancesToOld();
                } catch (Exception e) {e.printStackTrace();}
                addRefToRefMap(refMap, paramRef);
              }
            }
            else {
              try {
                paramRef.assignInstance(newParamRef.getInstances(), true);
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
  private void removeMethodReferences(SSAInvokeInstruction invokeInst, 
      String callSites, Hashtable<String, Hashtable<String, Reference>> refMap) {
    refMap.remove(callSites + String.format("%04d", invokeInst.getProgramCounter()));
  }
  
  /**
   * would not make a new clone
   */
  private void removeMethodDefCounts(SSAInvokeInstruction invokeInst, 
      String callSites, Hashtable<String, Hashtable<String, Integer>> defMap) {
    defMap.remove(callSites + String.format("%04d", invokeInst.getProgramCounter()));
  }
  
  protected final Formula computeToEnterCallSite(SSAInvokeInstruction invokeInst, BBorInstInfo instInfo, 
      ExecutionOptions execOptions, CGNode caller, CallStack callStack, int curInvokeDepth, 
      String callSites, Formula newPostCond) throws InvalidStackTraceException, TimeLimitExceededException {
    Formula preCond = null;
    
    // get the actual target method to enter
    String methodSig  = null;
    CGNode methodNode = null;
    if (instInfo.target != null && instInfo.target[0].equals(invokeInst) && instInfo.target[1] != null) {
      // instInfo.target[0]: the virtual invoke instruction whose target was dispatched
      // instInfo.target[1]: the ir of the dispatched target
      // instInfo.target[2]: the cgNode of the dispatched target
      methodSig  = ((IR) (instInfo.target[1])).getMethod().getSignature();
      methodNode = (CGNode) instInfo.target[2];
    }
    else {
      methodSig  = invokeInst.getDeclaredTarget().getSignature();
      methodNode = null;
    }
    
    // get inner call stack
    CallStack innerCallStack = callStack.getInnerCallStack();
    int lineNo = innerCallStack.getCurLineNo();
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = (innerCallStack.getDepth() == 1) ? execOptions.inclInnerMostLine : true;
    int startingInst = (innerCallStack.getDepth() == 1) ? execOptions.startingInst : -1;
    
    // create a new invokeInstData if we are not continuing
    if (instInfo.invokeInstData == null) { // a new traverse in this invocation
      String newCallSites = callSites + String.format("%04d", invokeInst.getProgramCounter());
      instInfo.invokeInstData = new InvokeInstData(lineNo, startingInst, inclLine,
          innerCallStack, curInvokeDepth, newCallSites, new Stack<BBorInstInfo>());
    }
    InvokeInstData invokeInstData = instInfo.invokeInstData;

    ExecutionResult execResult = instInfo.executor.computeRec(execOptions, methodNode, methodSig, 
        invokeInstData.startLine, invokeInstData.startInst, invokeInstData.inclLine, invokeInstData.callStack, 
        invokeInstData.curInvokeDepth, invokeInstData.callSites, invokeInstData.workList, newPostCond);
    preCond = execResult.getFirstSatisfiable();
    System.out.println("Come back to caller method: " + instInfo.methData.getName());
    
    return preCond;
  }
  
  protected final Formula computeAtCallSite(SSAInvokeInstruction invokeInst, BBorInstInfo instInfo, 
      ExecutionOptions execOptions, CGNode caller, CallStack callStack, int curInvokeDepth, 
      String callSites, Formula newPostCond) throws InvalidStackTraceException, TimeLimitExceededException {
    Formula preCond = null;

    // get the actual target method to enter
    String methodSig  = null;
    CGNode methodNode = null;
    if (instInfo.target != null && instInfo.target[0].equals(invokeInst) && instInfo.target[1] != null) {
      // instInfo.target[0]: the virtual invoke instruction whose target was dispatched
      // instInfo.target[1]: the ir of the dispatched target
      // instInfo.target[2]: the cgNode of the dispatched target
      methodSig  = ((IR) (instInfo.target[1])).getMethod().getSignature();
      methodNode = (CGNode) instInfo.target[2];
    }
    else {
      methodSig  = invokeInst.getDeclaredTarget().getSignature();
      methodNode = null;
    }
    
    // use pseudo implementations when necessary
    if (instInfo.executor.usePseudo()) {
      String pseudoImpl = PseudoImplMap.findPseudoImpl(methodSig);
      methodSig = pseudoImpl != null ? pseudoImpl : methodSig;
    }
    
    // create a new invokeInstData if we are not continuing
    if (instInfo.invokeInstData == null) { // a new traverse in this invocation
      String newCallSites = callSites + String.format("%04d", invokeInst.getProgramCounter());
      instInfo.invokeInstData = new InvokeInstData(-1, -1, false,
          callStack, curInvokeDepth + 1, newCallSites, new Stack<BBorInstInfo>());
    }
    InvokeInstData invokeInstData = instInfo.invokeInstData;

    ExecutionResult exeResult = instInfo.executor.computeRec(execOptions, methodNode, methodSig, 
        invokeInstData.startLine, invokeInstData.startInst, invokeInstData.inclLine, invokeInstData.callStack, 
        invokeInstData.curInvokeDepth, invokeInstData.callSites, invokeInstData.workList, newPostCond);
    preCond = exeResult.getFirstSatisfiable();
    System.out.println("Come back to caller method: " + instInfo.methData.getName());
    
    return preCond;
  }
  
  /**
   * would not make a new clone
   */
  protected final void addRefToRefMap(Hashtable<String, Hashtable<String, Reference>> refMap, Reference newReference) {
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
  protected final void addDefToDefMap(Hashtable<String, Hashtable<String, Integer>> defMap, Reference def) {
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
  
  protected final Reference findOrCreateReference(String refName, String refType, 
      String callSites, ISSABasicBlock createBlock, Formula postCond) {
    return findOrCreateReference(refName, refType, callSites, createBlock, null, postCond);
  }
  
  protected final Reference findOrCreateReference(String refName, String refType, 
      String callSites, ISSABasicBlock createBlock, Instance declInstance, Formula postCond) {
    
    Hashtable<String, Hashtable<String, Reference>> refMap = postCond.getRefMap();
    
    Reference ref = findReference(refName, callSites, refMap);
    // cannot find, create a new one
    if (ref == null) {
      ref = new Reference(refName, refType, callSites, 
          new Instance(callSites, createBlock) /* unbounded instance */, declInstance, true);
      
      // define constant fields
      if (refName.startsWith("##")) {
        String str = refName.substring(2);
        ref.getInstance().setField("count", "I", callSites, new Instance("#!" + (refName.length() - 2), "I", createBlock), true, true);
        ref.getInstance().setField("offset", "I", callSites, new Instance("#!0", "I", createBlock), true, true);
        
        Instance valueInstance = new Instance(callSites, createBlock);
        ref.getInstance().setField("value", "[C", callSites, valueInstance, true, true);
        valueInstance.setField("length", "I", callSites, new Instance("#!" + (refName.length() - 2), "I", createBlock), true, true);

        // add only once
        Relation arrayRel = postCond.getRelation("@@array");
        boolean newConstStr = true;
        for (int i = 0, size = arrayRel.getFunctionCount(); i < size && newConstStr; i++) {
          if (arrayRel.isUpdate(i)) {
            newConstStr = !valueInstance.toString().equals(arrayRel.getDomainValues().get(i)[0].toString());
          }
        }
        if (newConstStr) {
          // add chars to array
          for (int i = 0, size = str.length(); i < size && i < 3 /* for solver performance reason */; i++) {
            char c = str.charAt(i);
            Instance[] domains = new Instance[] {valueInstance, new Instance("#!" + i, "I", null)};
            arrayRel.update(domains, new Instance("#!" + ((int) c), "C", null));
          }
        }
      }
    }
    return ref;
  }
  
  protected final Reference findReference(String refName, String callSites, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {
    
    Reference ref = null;
    Hashtable<String, Reference> methodRefs = refMap.get(callSites);
    if (methodRefs != null) {
      ref = methodRefs.get(refName);
    }
    return ref;
  }
  
  protected final boolean containsRef(String refName, String callSites, 
      Hashtable<String, Hashtable<String, Reference>> refMap) {

    Hashtable<String, Reference> methodRefs = refMap.get(callSites);
    return methodRefs != null && methodRefs.containsKey(refName);
  }
  
//  
//  /**
//   * Assuming there is at most one 'Caught ...' at a time.
//   */
//  protected final String findCaughtExceptionTypeStr(Predicate predicate) {
//    String caughtStr = null;
//    if (predicate.getVarMap().containsKey("Caught")) {
//      caughtStr = predicate.getVarMap().get("Caught").get(0);
//    }
//    return caughtStr;
//  }
//  
//  protected final Hashtable<String, List<String>> setExceptionCaught(
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
//  protected final Hashtable<String, List<String>> setExceptionTriggered(
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
//  protected final Hashtable<String, List<String>> setExceptionThrownCurrent(
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
//  protected final Hashtable<String, List<String>> checkExceptionThrown(
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
  
  protected String getConstantPrefix(int varID, MethodMetaData methData) {
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
  
  protected final Formula defaultHandler(Formula formula, SSAInstruction inst, BBorInstInfo instInfo) {
    //return new Formula(formula);
    return formula;
  }
  
  private List<String> m_methodStepInFilters;
}
