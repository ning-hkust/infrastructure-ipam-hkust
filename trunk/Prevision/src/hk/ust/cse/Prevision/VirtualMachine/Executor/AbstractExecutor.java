package hk.ust.cse.Prevision.VirtualMachine.Executor;

import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions.EXCEPTION_TYPE;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Prevision_PseudoImpl.PseudoImplMap;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.SubClassHack;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import javax.naming.TimeLimitExceededException;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.heapTrace.HeapTracer;
import com.ibm.wala.util.heapTrace.HeapTracer.Result;

public abstract class AbstractExecutor {

  public class BBorInstInfo {
    public BBorInstInfo(ISSABasicBlock currentBB, boolean startingBB, boolean skipToBB, Formula formula, 
        Formula formula4BB, int controlType, ISSABasicBlock previousBB, BBorInstInfo previousInfo, 
        MethodMetaData methData, String callSites, InvokeInstData invokeInstData, AbstractExecutor executor) {
      this.currentBB      = currentBB;
      this.startingBB     = startingBB;
      this.skipToBB       = skipToBB;
      this.formula        = formula;
      this.formula4BB     = formula4BB;
      this.controlType    = controlType;
      this.previousBB     = previousBB;
      this.previousInfo   = previousInfo;
      this.methData       = methData;
      this.callSites      = callSites;
      this.invokeInstData = invokeInstData; // only used for invocation instructions
      this.executor       = executor;
    }
    
    public BBorInstInfo clone() {
      BBorInstInfo newInfo = new BBorInstInfo(currentBB, startingBB, skipToBB, formula, formula4BB, 
          controlType, previousBB, previousInfo, methData, callSites, invokeInstData, executor);
      newInfo.target = target;
      return newInfo;
    }

    public InvokeInstData       invokeInstData;
    public Object[]             target; // SSAInvokeInstruction, IR, CGNode
    
    public Formula              formula;
    public Formula              formula4BB;
    public boolean              startingBB;
    public final int            controlType;
    public final boolean        skipToBB;
    public final ISSABasicBlock currentBB;
    public final ISSABasicBlock previousBB;
    public final BBorInstInfo   previousInfo;
    public final MethodMetaData methData;
    public final String         callSites;
    public final AbstractExecutor executor;
  }
  
  public static class InvokeInstData {
    public InvokeInstData(int startLine, int startInst, boolean inclLine, 
        CallStack callStack, int curInvokeDepth, String callSites, Stack<BBorInstInfo> workList) {
      this.startLine      = startLine;
      this.startInst      = startInst;
      this.inclLine       = inclLine;
      this.callStack      = callStack;
      this.curInvokeDepth = curInvokeDepth;
      this.callSites      = callSites;
      this.workList       = workList;
    }

    public final int                 startLine;
    public final int                 startInst;
    public final int                 curInvokeDepth;
    public final boolean             inclLine;
    public final CallStack           callStack;
    public final String              callSites;
    public final Stack<BBorInstInfo> workList;
  }

  protected AbstractExecutor(String appJar, String pseudoImplJarFile, AbstractHandler instHandler, 
      SMTChecker smtChecker, boolean forward) throws Exception {
    
    // create the wala analyzer which holds all the 
    // wala related information of this executor instance
    m_walaAnalyzer = new WalaAnalyzer(appJar);
    
    // add the pseudoImpl jar file to analysis scope
    if (pseudoImplJarFile != null) {
      m_walaAnalyzer.addJarFile(pseudoImplJarFile);
      m_usePseudo = true;
    }
    else {
      m_usePseudo = false;
    }
    
    // set instruction handler
    m_instHandler = instHandler;
    
    // set smt checker to use
    m_smtChecker = smtChecker;
    
    // direction of the executor
    m_forward = forward;
  }
  
  public void addJarFile(String appJar) throws Exception {
    m_walaAnalyzer.addJarFile(appJar);
  }

  public ExecutionResult compute(ExecutionOptions execOptions, Formula formula) 
                                 throws InvalidStackTraceException, TimeLimitExceededException {
    return compute(execOptions, formula, null);
  }
  
  public ExecutionResult compute(ExecutionOptions execOptions, Formula formula, IR ir) 
                                 throws InvalidStackTraceException, TimeLimitExceededException {
    CallStack fullStack = execOptions.fullCallStack;
    
    // compute from the outermost stack frame
    String methNameOrSig = fullStack.getCurMethodNameOrSign();
    int lineNo           = fullStack.getCurLineNo();
    
    // for non-CUSTOM exceptions, we always include the 
    // exception line to compute the exception preconditions
    if (execOptions.exceptionType != EXCEPTION_TYPE.CUSTOM) {
      execOptions.inclInnerMostLine = true;
    }
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = fullStack.getDepth() == 1 ? execOptions.inclInnerMostLine : true;
    int startingInst = fullStack.getDepth() == 1 ? execOptions.startingInst : -1;

    // get ir(ssa) for methods in jar file
    if (ir == null) {
      // use pseudo implementations when necessary
      if (usePseudo()) {
        String pseudoImpl = PseudoImplMap.findPseudoImpl(methNameOrSig);
        methNameOrSig = pseudoImpl != null ? pseudoImpl : methNameOrSig;
      }
      
      ir = Utils.isMethodSignature(methNameOrSig) ? Jar2IR.getIR(m_walaAnalyzer, methNameOrSig) : 
                                                    Jar2IR.getIR(m_walaAnalyzer, methNameOrSig, lineNo);
      // return if method is not found
      if (ir == null) {
        String msg = "Failed to locate " + methNameOrSig + ":" + lineNo;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }
      
      if (execOptions.callGraphBuilder != null) {
        // re-compute call graph with ir (outermost) since main()s may not be good enough
        HashSet<IMethod> additionalEntrypoints = new HashSet<IMethod>();
        if (execOptions.addIRAsEntryPoint) {
          additionalEntrypoints.add(ir.getMethod());
        }
        m_walaAnalyzer.recomputeCallGraph(additionalEntrypoints, execOptions.callGraphBuilder);
      }
    }
      
    // get the initial cgNode
    CGNode cgNode = (execOptions.callGraphBuilder != null && 
        execOptions.maxDispatchTargets > 0) ? m_walaAnalyzer.getCallGraph().getNode(ir.getMethod()) : null;
    
    // the initial outermost workList
    Stack<BBorInstInfo> workList = new Stack<BBorInstInfo>();
    
    return computeRec(execOptions, cgNode, ir, lineNo, startingInst, inclLine, fullStack, 0, "", workList, formula);
  }
  
  public ExecutionResult computeRec(ExecutionOptions execOptions, CGNode cgNode, String methNameOrSig, 
      int startLine, int startingInst, boolean inclLine, CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula formula) throws InvalidStackTraceException, TimeLimitExceededException {
    
    // get ir(ssa) for methods in jar file
    IR ir = Utils.isMethodSignature(methNameOrSig) ? Jar2IR.getIR(m_walaAnalyzer, methNameOrSig) : 
                                                     Jar2IR.getIR(m_walaAnalyzer, methNameOrSig, startLine);
    // return if method is not found
    if (ir == null) {
      String msg = "Failed to locate " + methNameOrSig + ":" + startLine;
      System.err.println(msg);
      throw new InvalidStackTraceException(msg);
    }
    
    return computeRec(execOptions, cgNode, ir, startLine, startingInst, 
        inclLine, callStack, curInvokeDepth, callSites, workList, formula);
  }

  /**
   * @param cgNode: only useful when we use 'compDispatchTargets' function
   */
  public abstract ExecutionResult computeRec(ExecutionOptions execOptions, CGNode cgNode, IR ir, int startLine, 
      int startingInst, boolean inclLine, CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula formula) throws InvalidStackTraceException, TimeLimitExceededException;
  
  protected Object[] findInvokeTargets(WalaAnalyzer walaAnalyzer, CGNode caller, 
      CallSiteReference callSite, MethodReference mr, int maxToGet, boolean useSubClassHack) {
    
    // find the potential targets of this invocation
    IR[] targetIRs       = null;
    CGNode[] targetNodes = null;
    
    if (caller != null) {
      // use the default call graph builder to find invocation targets
      SimpleEntry<IR[], CGNode[]> ret = 
          WalaUtils.findInvocationTargets(walaAnalyzer, caller, callSite, Integer.MAX_VALUE);
      targetIRs   = ret.getKey();
      targetNodes = ret.getValue();
    }

    // if no target ir is found, append all implementation methods
    if ((caller == null || targetIRs.length == 0) && maxToGet > 0) {
      targetIRs   = WalaUtils.getImplementations(walaAnalyzer, mr, Integer.MAX_VALUE /* do not limit now */);
      targetNodes = new CGNode[targetIRs.length]; // all null
    }
    
    // if found new targets
    if (targetIRs != null && targetIRs.length > 0 && targetIRs[0] != null && 
       !targetIRs[0].getMethod().getSignature().equals(mr.getSignature())) {
      
      // select the dispatch targets to use
      HashMap<IR, CGNode> irCGNodeMap = new HashMap<IR, CGNode>();
      for (int i = 0; i < targetIRs.length; i++) {
        // save IR to CGNode Mapping
        irCGNodeMap.put(targetIRs[i], targetNodes[i]);
      }
      
      // try to select frequent sub-class irs
      if (useSubClassHack) {
        String superClass = mr.getDeclaringClass().getName().toString();
        IR[] freqIRs = SubClassHack.findFreqSubclassIRs(walaAnalyzer, superClass, mr.getSignature());
        if (freqIRs != null) {
          targetIRs = freqIRs;
        }
      }
      targetIRs = targetIRs.length > maxToGet ? Arrays.copyOf(targetIRs, maxToGet) : targetIRs;
      
      // re-create targetNodes
      targetNodes = new CGNode[targetIRs.length];
      for (int i = 0; i < targetIRs.length; i++) {
        targetNodes[i] = irCGNodeMap.get(targetIRs[i]);
      }
    }

    // sort to make sure deterministic
    if (targetIRs != null) {
      // put to one array
      Object[][] targets = new Object[targetIRs.length][];
      for (int i = 0; i < targets.length; i++) {
        targets[i] = new Object[] {targetIRs[i], targetNodes[i]};
      }
      Arrays.sort(targets, new Comparator<Object[]>() {
        @Override
        public int compare(Object[] target1, Object[] target2) {
          return ((IR) target1[0]).getMethod().getSignature().compareTo(
                 ((IR) target2[0]).getMethod().getSignature());
        }
      });
      
      // separate again
      for (int i = 0; i < targets.length; i++) {
        targetIRs[i]   = (IR) targets[i][0];
        targetNodes[i] = (CGNode) targets[i][1];
      }
    }
    
    return new Object[] {targetIRs, targetNodes};
  }
  
  protected boolean sameOrChildMethod(MethodReference mr, String methodNameOrSign) {
    boolean isSame = false;
    
    methodNameOrSign = methodNameOrSign.replace('$', '.');
    if (Utils.isMethodSignature(methodNameOrSign)) {
      // get invoking method signature
      isSame = mr.getSignature().equals(methodNameOrSign);
    }
    else {
      String declaringClass = mr.getDeclaringClass().getName().toString();
      
      // get invoking method name
      String invokingMethod = Utils.getClassTypeJavaStr(declaringClass) + "." + mr.getName().toString();
      // naive compare first, sometimes can avoid including subject jar in the classpath
      if (!invokingMethod.equals(methodNameOrSign)) {
        IR ir = Jar2IR.getIR(m_walaAnalyzer, mr.getSignature());
        if (ir != null) {
          isSame = (Utils.getClassTypeJavaStr(ir.getMethod().getDeclaringClass().getName().toString())
              + "." + mr.getName().toString()).equals(methodNameOrSign);
        }
      }
      else {
        isSame = true;
      }
    }
    return isSame;
  }
  
  // if lineNumber is <= 0, we return the exit block
  protected ISSABasicBlock findBasicBlock(MethodMetaData methData, int lineNumber, int instIndex, boolean preferSmallBB) {

    ISSABasicBlock basicBlock = null;
    if (lineNumber > 0 && instIndex < 0) {
      basicBlock = preferSmallBB ? methData.getFirstBasicBlockForLine(lineNumber) : 
                                   methData.getLastBasicBlockForLine(lineNumber);
    }
    else if (lineNumber > 0 && instIndex >= 0) {
      // confirm that the retrieved line number of this instruction 
      // is identical to the inputed line number
      int line = methData.getLineNumber(instIndex);
      if (line != lineNumber) {
        System.err.println(
            "Using a starting instruction index, but its line number is different from the inputed line number!");
      }
      else {
        // get the bb for this instruction
        basicBlock = methData.getcfg().getBlockForInstruction(instIndex);
      }
    }
    else {
      // get the exit block
      basicBlock = methData.getcfg().exit();
    }
    return basicBlock;
  }
  
  protected void pushChildrenBlocks(List<Object[]> childrenBlocks, boolean skipToBlocks, final boolean pushAsc, 
      final BBorInstInfo currentInfo, final MethodMetaData methData, int controlType, Stack<BBorInstInfo> workList, 
      CallStack callStack, int curInvokeDepth, int maxLoop, String callSites, boolean starting) {
    
    List<Object[]> visitedList    = new ArrayList<Object[]>();
    List<Object[]> notvisitedList = new ArrayList<Object[]>();
    for (Object[] childrenBlock : childrenBlocks) {
      ISSABasicBlock bb = (ISSABasicBlock) childrenBlock[0];
      // make sure we are not pushing the current node again. Sometimes, a 
      // monitorexit node can be a child of itself, making the search endless.
      if (bb.getNumber() == currentInfo.currentBB.getNumber()) {
        continue;
      }
      
      // get all visited records    
      Integer count = ((Formula) childrenBlock[1]).getVisitedRecord().get(childrenBlock[0]);
      
      // also consider the case where a call stack entering point is within a loop
      count = (count == null ? 0 : count) + 
          (isFlyingOverStartingLine(currentInfo.currentBB, bb, callStack, curInvokeDepth, methData) ? 1 : 0);
      
      if (count > 0 && count < maxLoop) {
        visitedList.add(childrenBlock);
      }
      else if (count == 0) {
        notvisitedList.add(childrenBlock);
      }
      else if (count >= maxLoop) {
        int lineNo = methData.getLineNumber(bb);
        System.out.println("BB" + bb.getNumber() + (lineNo >= 0 ? (" @ line " + lineNo) : "") + 
            " is discarded due to reaching max loop unrollment!");
      }
    }

    // sort them
    Collections.sort(visitedList, new Comparator<Object[]>(){ 
      public int compare(Object[] arg0, Object[] arg1) { 
        ISSABasicBlock bb0 = (ISSABasicBlock) (pushAsc ? arg0[0] : arg1[0]);
        ISSABasicBlock bb1 = (ISSABasicBlock) (pushAsc ? arg1[0] : arg0[0]);
        // in backward computation, we want to get to the previous conditional block quickly
        boolean forceBack = !pushAsc && ((bb0.getNumber() > bb1.getNumber() && isConditionalBlock(bb0, methData) && isLineNumberSmaller(bb0, currentInfo.currentBB, methData)) || 
                                         (bb1.getNumber() > bb0.getNumber() && isConditionalBlock(bb1, methData) && isLineNumberSmaller(bb1, currentInfo.currentBB, methData)));
        return (forceBack ? -1 : 1) * (bb0.getNumber() - bb1.getNumber());
      } 
    });
    Collections.sort(notvisitedList, new Comparator<Object[]>(){ 
      public int compare(Object[] arg0, Object[] arg1) { 
        ISSABasicBlock bb0 = (ISSABasicBlock) (pushAsc ? arg0[0] : arg1[0]);
        ISSABasicBlock bb1 = (ISSABasicBlock) (pushAsc ? arg1[0] : arg0[0]);
        // in backward computation, we want to get to the previous conditional block quickly
        boolean forceBack = !pushAsc && ((bb0.getNumber() > bb1.getNumber() && isConditionalBlock(bb0, methData) && isLineNumberSmaller(bb0, currentInfo.currentBB, methData)) || 
                                         (bb1.getNumber() > bb0.getNumber() && isConditionalBlock(bb1, methData) && isLineNumberSmaller(bb1, currentInfo.currentBB, methData)));
        return (forceBack ? -1 : 1) * (bb0.getNumber() - bb1.getNumber());
      } 
    });
    
    // push the visited ones into the beginning of the stack
    System.out.print("Push: ");
    for (Object[] visited : visitedList) {
      System.out.print("BB" + ((ISSABasicBlock) visited[0]).getNumber() + " ");
      workList.push(new BBorInstInfo((ISSABasicBlock) visited[0], (currentInfo.startingBB && starting), 
          skipToBlocks, (Formula) visited[1], (Formula) visited[1], controlType, currentInfo.currentBB, 
          currentInfo, methData, callSites, null, this));
    }
    
    // push the non visited ones into the beginning of the stack
    for (Object[] notvisited : notvisitedList) {
      System.out.print("BB" + ((ISSABasicBlock) notvisited[0]).getNumber() + " ");
      workList.push(new BBorInstInfo((ISSABasicBlock) notvisited[0], (currentInfo.startingBB && starting), 
          skipToBlocks, (Formula) notvisited[1], (Formula) notvisited[1], controlType, currentInfo.currentBB, 
          currentInfo, methData, callSites, null, this));
    }
    System.out.println("");
  }

  // only useful for backward execution
  private boolean isFlyingOverStartingLine(ISSABasicBlock fromBB, ISSABasicBlock toBB, 
      CallStack callStack, int curInvokeDepth, MethodMetaData methData) {

    boolean flyingOver = false;
    if (!m_forward && callStack.getCurLineNo() > 0 && curInvokeDepth == 0 /* not in any invocation context */) {
      int fromLine = methData.getLineNumber(fromBB);
      int toLine   = methData.getLineNumber(toBB);
      
      flyingOver = fromLine > 0 && toLine > 0 && fromLine < callStack.getCurLineNo() && 
          toLine >= callStack.getCurLineNo() && methData.getcfg().getNormalPredecessors(fromBB).size() > 1;
    }
    return flyingOver;
  }
  
  private boolean isConditionalBlock(ISSABasicBlock bb, MethodMetaData methData) {
    return methData.getcfg().getNormalSuccessors(bb).size() > 1;
  }
  
  private boolean isLineNumberSmaller(ISSABasicBlock bb1, ISSABasicBlock bb2, MethodMetaData methData) {
    int line1 = methData.getLineNumber(bb1.getFirstInstructionIndex());
    int line2 = methData.getLineNumber(bb2.getFirstInstructionIndex());
    if (line1 > 0 && line2 > 0) {
      return line1 < line2;
    }
    else {
      return false;
    }
  }
  
  protected void printPropagationPath(BBorInstInfo entryNode) {
    // output propagation path
    System.out.print("Computation Path: ");
    StringBuilder computePath = new StringBuilder();
    computePath.append(0);
    BBorInstInfo currentBB = entryNode;
    while (currentBB.previousInfo != null) {
      String bbNum  = String.valueOf(currentBB.previousBB.getNumber());
      String lineNo = String.valueOf(currentBB.methData.getLineNumber(currentBB.previousBB));
      computePath.append(" >- ")
                 .append(new StringBuilder("(" + lineNo + ")").reverse())
                 .append(new StringBuilder(bbNum).reverse());
      currentBB = currentBB.previousInfo;
    }
    System.out.print(computePath.reverse());
    System.out.println(" -> SMT Check");
  }
  
  protected void printResult(Formula preCond) {
    if (preCond != null) {
      System.out.println("SMT Solver Input-----------------------------------");
      System.out.println(preCond.getLastSolverInput());
      
      System.out.println("SMT Solver Output----------------------------------");
      System.out.println(preCond.getLastSolverResult().getOutputInfo(preCond));

//      System.out.println("Path Conditions--------------------------------");
//      HashSet<String> outputted = new HashSet<String>();
//      List<Condition> conditions = preCond.getConditionList();
//      for (int i = conditions.size() - 1; i >= 0; i--) {
//        String conditionStr = conditions.get(i).toString();
//
//        if (!outputted.contains(conditionStr)) {
//          System.out.println(conditionStr);
//          outputted.add(conditionStr);
//        }
//      }
//      System.out.println();
//      
//      System.out.println("Path RefMap-------------------------------------");
//      Hashtable<String, Hashtable<String, Reference>> refMap = preCond.getRefMap();
//      Enumeration<String> keys = refMap.keys();
//      while (keys.hasMoreElements()) {
//        String key = (String) keys.nextElement();
//        System.out.println("Method Callsites: " + key);
//        Hashtable<String, Reference> methodRefs = refMap.get(key);
//        Enumeration<String> keys2 = methodRefs.keys();
//        while (keys2.hasMoreElements()) {
//          String key2 = (String) keys2.nextElement();
//          System.out.println(methodRefs.get(key2).toString());
//        }
//      }
    }
    else {
      System.out.println("The line is unreachable!");
    }
  }
  
  protected void printIR(IR ir) {
    // output CFG
    SSACFG cfg = ir.getControlFlowGraph();
    System.out.println(cfg.toString());

    // output Instructions
    SSAInstruction[] instructions = ir.getInstructions();
    for (SSAInstruction instruction : instructions) {
      System.out.println(instruction.toString());
    }
  }
  
  protected void heapTracer() {
    try {
      Result r = (new HeapTracer(Collections.emptySet(), true)).perform();
      System.err.println(r.toString());
      System.err.flush();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
  
  public AbstractHandler getInstructionHandler() {
    return m_instHandler;
  }
  
  public SMTChecker getSMTChecker() {
    return m_smtChecker;
  }
  
  public MethodMetaData getMethodMetaData() {
    return m_methMetaData;
  }
  
  public ExecutionResult getExecResult() {
    return m_execResult;
  }

  public List<Formula> getSatisfiables() {
    return m_execResult.getSatisfiables();
  }
  
  // return the first satisfiable precondition if exists
  public Formula getFirstSatisfiable() {
    return m_execResult.getFirstSatisfiable();
  }
  
  public WalaAnalyzer getWalaAnalyzer() {
    return m_walaAnalyzer;
  }
  
  public boolean isForward() {
    return m_forward;
  }
  
  public boolean usePseudo() {
    return m_usePseudo;
  }
  
  protected final boolean         m_usePseudo;
  protected final boolean         m_forward;
  protected final WalaAnalyzer    m_walaAnalyzer;
  protected final AbstractHandler m_instHandler;
  protected final SMTChecker      m_smtChecker;
  protected MethodMetaData        m_methMetaData;
  protected ExecutionResult       m_execResult;
  protected long                  m_globalStartTime;
}
