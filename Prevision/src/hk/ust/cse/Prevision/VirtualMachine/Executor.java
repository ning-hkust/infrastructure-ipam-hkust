package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.InvalidStackTraceException;
import hk.ust.cse.Prevision.Summary;
import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteBackwardHandler;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.StaticAnalysis.DefAnalyzerWrapper;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.heapTrace.HeapTracer;
import com.ibm.wala.util.heapTrace.HeapTracer.Result;

public class Executor {
  public class GlobalOptionsAndStates {
    public GlobalOptionsAndStates(CallStack fullCallStack, boolean useSummary) {      
      // set call stack
      this.fullCallStack = fullCallStack;
      
      // initialize summary
      this.summary = useSummary ? new Summary() : null;

      // states
      this.m_enteringCallStack = true; // at the beginning, we are entering call stack
    }

    public boolean isEnteringCallStack() {
      return m_enteringCallStack;
    }

    public void finishedEnteringCallStack() {
      m_enteringCallStack = false;
    }

    // global options
    public boolean   inclInnerMostLine      = false;
    public boolean   inclStartingInst       = false;
    public boolean   saveNotSatResults      = false;
    public int       maxDispatchTargets     = Integer.MAX_VALUE;
    public int       maxRetrieve            = 1;
    public int       maxSmtCheck            = 1000;
    public int       maxInvokeDepth         = 1;
    public int       maxLoop                = 1;
    public int       startingInst           = -1;   // -1 if don't want to specify the starting instruction index
    public int[]     startingInstBranchesTo = null; // null if don't want to specify the instructions that the starting instruction is branching to
    
    public final CallStack fullCallStack;
    public final Summary   summary;

    // global states
    private boolean m_enteringCallStack;
  }

  public class BBorInstInfo {
    public BBorInstInfo(ISSABasicBlock currentBB, boolean isSkipToBB, Formula postCond,
        int sucessorType, ISSABasicBlock sucessorBB, BBorInstInfo sucessorInfo, 
        MethodMetaData methData, String callSites, Stack<BBorInstInfo> workList, Executor executor) {
      this.currentBB    = currentBB;
      this.isSkipToBB   = isSkipToBB;
      this.postCond     = postCond;
      this.sucessorType = sucessorType;
      this.sucessorBB   = sucessorBB;
      this.sucessorInfo = sucessorInfo;
      this.methData     = methData;
      this.callSites    = callSites;
      this.workList     = workList; // only used for invocation instructions
      this.executor     = executor;
    }
    
    public BBorInstInfo clone() {
      BBorInstInfo newInfo = new BBorInstInfo(currentBB, isSkipToBB, postCond, 
          sucessorType, sucessorBB, sucessorInfo, methData, callSites, workList, executor);
      newInfo.target = target;
      return newInfo;
    }

    public Stack<BBorInstInfo>                       workList;
    public SimpleEntry<SSAInvokeInstruction, CGNode> target;
    
    public final Formula             postCond;
    public final int                 sucessorType;
    public final boolean             isSkipToBB;
    public final ISSABasicBlock      currentBB;
    public final ISSABasicBlock      sucessorBB;
    public final BBorInstInfo        sucessorInfo;
    public final MethodMetaData      methData;
    public final String              callSites;
    public final Executor            executor;
  }

  public Executor(String appJar, AbstractHandler instHandler, SMTChecker smtChecker, 
      DefAnalyzerWrapper defAnalyzer) throws Exception {
    
    // create the wala analyzer which holds all the 
    // wala related information of this executor instance
    m_walaAnalyzer = new WalaAnalyzer(appJar);
    
    // set instruction handler
    m_instHandler = instHandler;
    
    // set smt checker to use
    m_smtChecker = smtChecker;

    // set def analyzer
    m_defAnalyzer = defAnalyzer;
  }
  
  public ExecutionResult compute(GlobalOptionsAndStates optAndStates, 
      Formula postCond) throws InvalidStackTraceException {
    return compute(optAndStates, postCond, null);
  }
  
  public ExecutionResult compute(GlobalOptionsAndStates optAndStates, 
      Formula postCond, IR ir) throws InvalidStackTraceException {
    CallStack fullStack = optAndStates.fullCallStack;
    
    // compute from the outermost stack frame
    String methNameOrSig = fullStack.getCurMethodNameOrSign();
    int lineNo           = fullStack.getCurLineNo();
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = true;
    int startingInst = -1;
    if (fullStack.getDepth() == 1) {
      inclLine     = optAndStates.inclInnerMostLine;
      startingInst = optAndStates.startingInst;
    }

    // get ir(ssa) for methods in jar file
    if (ir == null) {
      if (Utils.isMethodSignature(methNameOrSig)) {
        ir = Jar2IR.getIR(m_walaAnalyzer, methNameOrSig);
      }
      else {
        ir = Jar2IR.getIR(m_walaAnalyzer, methNameOrSig, lineNo);
      }
    
      // return if method is not found
      if (ir == null) {
        String msg = "Failed to locate " + methNameOrSig + ":" + lineNo;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }
    }
      
    // get the initial cgNode
    CGNode cgNode = null;
    if (optAndStates.maxDispatchTargets > 0) {
      cgNode = m_walaAnalyzer.getCallGraph().getNode(ir.getMethod());
    }
    
    // the initial outter most workList
    Stack<BBorInstInfo> workList = new Stack<BBorInstInfo>();
    
    return computeRec(optAndStates, cgNode, ir, lineNo, startingInst, inclLine, 
        fullStack, 0, "", workList, postCond);
  }
  
  public ExecutionResult computeRec(GlobalOptionsAndStates optAndStates, 
      CGNode cgNode, String methNameOrSig, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula postCond) throws InvalidStackTraceException {
    
    // get ir(ssa) for methods in jar file
    IR ir = null;
    if (Utils.isMethodSignature(methNameOrSig)) {
      ir = Jar2IR.getIR(m_walaAnalyzer, methNameOrSig);
    }
    else {
      ir = Jar2IR.getIR(m_walaAnalyzer, methNameOrSig, startLine);
    }
  
    // return if method is not found
    if (ir == null) {
      String msg = "Failed to locate " + methNameOrSig + ":" + startLine;
      System.err.println(msg);
      throw new InvalidStackTraceException(msg);
    }
    
    return computeRec(optAndStates, cgNode, ir, startLine, startingInst, 
        inclLine, callStack, curInvokeDepth, callSites, workList, postCond);
  }

  /**
   * @param cgNode: only useful when we use 'compDispatchTargets' function
   */
  public ExecutionResult computeRec(GlobalOptionsAndStates optAndStates, 
      CGNode cgNode, IR ir, int startLine, int startingInst, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula postCond) throws InvalidStackTraceException {
    
    assert(ir != null);
    
    // start timing
    long start = System.currentTimeMillis();
    
    // printIR(ir);
    // System.out.println(ir.toString());

    // init MethodMetaData, save it if it's not inside an 
    // invocation and it's at the out most call
    MethodMetaData methMetaData = new MethodMetaData(ir);
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_methMetaData = methMetaData;

      // create container to save the invoke instructions at call stack points
      m_callStackInvokes = new Hashtable<BBorInstInfo, SSAInstruction>();
      
      // clear m_execResult
      m_execResult = null;
    }
    
    if (workList.empty()) { // new worklist, simply start from startLine
      // start from the basic block at nStartLine
      ISSABasicBlock startFromBB = findBasicBlock(methMetaData, startLine, startingInst);
      if (startFromBB == null) {
        String msg = "Failed to find a valid basic block at line: " + startLine + 
                     " and instruction index: " + startingInst;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }

      // create a "TRUE" Formula if postCond is null
      postCond = (postCond == null) ? new Formula() : postCond;

      // push in the first block\
      workList.push(new BBorInstInfo(startFromBB, false, postCond, 
          Formula.NORMAL_SUCCESSOR, null, null, methMetaData, callSites, null, this));
    }

    // start depth first search
    ExecutionResult execResult = computeMethod(optAndStates, cgNode, methMetaData, 
        workList, startLine, startingInst, inclLine, callStack, curInvokeDepth, callSites);
    
    // save result if it's not inside an invocation and it's at the out most call
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_execResult = execResult;
      
      // print satisfiable preconditions
      List<Formula> satisfiables = m_execResult.getSatisfiables();
      for (int i = 0; i < satisfiables.size(); i++) {
        System.out.println("Satisfiable Precondition " + (i + 1) + ": ");
        printResult(satisfiables.get(i));
        System.out.println();
      }
      
      // clear non-solver data in each Formula object
      m_execResult.clearAllSatNonSolverData();

      // end timing
      long end = System.currentTimeMillis();
      System.out.println("Total elapsed: " + (end - start) + "ms!");
    }

    return execResult;
  }
  
  public void saveCallStackInvokeInst(BBorInstInfo invokeInstBB, SSAInstruction invokeInst) {
    m_callStackInvokes.put(invokeInstBB, invokeInst);
  }
  
  public boolean isCallStackInvokeInst(BBorInstInfo invokeInstBB, SSAInstruction invokeInst) {
    SSAInstruction inst = m_callStackInvokes.get(invokeInstBB);
    if (inst != null) {
      return inst == invokeInst;
    }
    return false;
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
 
  private ExecutionResult computeMethod(GlobalOptionsAndStates optAndStates,
      CGNode cgNode, MethodMetaData methData, Stack<BBorInstInfo> workList, 
      int startLine, int startingInst, boolean inclLine, CallStack callStack, 
      int curInvokeDepth, String valPrefix) throws InvalidStackTraceException {

    // start timing
    long start = System.currentTimeMillis();

    // we don't want to return a null execResult
    ExecutionResult execResult = new ExecutionResult();
    int smtChecked   = 0;
    int satRetrieved = 0;
    
    // get cfg for later use
    SSACFG cfg = methData.getcfg();
    
    // if nStartLine <= 0, we start from exit block
    boolean[] starting = new boolean[1];
    starting[0] = startLine > 0;
    
    // if callStack's depth == 1, finished entering call stack
    if (optAndStates.isEnteringCallStack() && callStack.getDepth() <= 1) {
      optAndStates.finishedEnteringCallStack();
    }

    // output method name
    System.out.println("Computing method: " + cfg.getMethod().getSignature());

    // start depth first search
    while (!workList.empty()) {
      // for (int i = 0; i < dfsStack.size(); i++) {
      // System.out.print(dfsStack.get(i).currentBB.getNumber() + " ");
      // }
      // System.out.println();

      BBorInstInfo infoItem = workList.pop();

      // if postCond is still satisfiable
      //if (!infoItem.postCond.isContradicted()) {
      if (true) {
        int instIndex = infoItem.currentBB.getLastInstructionIndex();
        String lineNo = (instIndex >= 0) ? " @ line " + methData.getLineNumber(instIndex) : "";
        System.out.println("Computing BB" + infoItem.currentBB.getNumber() + lineNo);
        
        // get visited records
        Hashtable<ISSABasicBlock, Integer> visitedBB = infoItem.postCond.getVisitedRecord();
        
        // compute for this BB
        int oriStackSize = workList.size();
        Formula precond = computeBB(optAndStates, cgNode, methData, infoItem, startLine, 
            startingInst, inclLine, callStack, starting, curInvokeDepth, valPrefix, workList);

        if (precond == null) {
          break;
        }
        
        // if new invocation targets have been pushed into stack due to 
        // target dispatch, skip the current instruction
        if (workList.size() != oriStackSize) {
          continue;
        }

        // marked as visited
        precond.setVisitedRecord(visitedBB, infoItem);

        // unpop it if can't pop: a new workList created 
        // for this BB and has not yet finished
        if (infoItem.workList != null && !infoItem.workList.empty()) {
          workList.push(infoItem);
        }

//        // check whether the caught exception is/can be triggered
//        if (!infoItem.currentBB.isCatchBlock() && 
//            !infoItem.currentBB.isExitBlock() && 
//            isCatchNeverTriggered(precond)) {
//          // the caught exception is not trigger, no need to go further
//          System.out.println("The caught exception is not triggered! Don't need to go futher.");
//          continue;
//        }
        
        if (!infoItem.currentBB.isEntryBlock()) {
          Collection<ISSABasicBlock> normPredBB =
            cfg.getNormalPredecessors(infoItem.currentBB);
          Collection<ISSABasicBlock> excpPredBB =
            cfg.getExceptionalPredecessors(infoItem.currentBB);
          
//          if ((normPredBB.size() > 1 && !infoItem.currentBB.isExitBlock()) || 
//              (precond.getPhiMap().size() != infoItem.postCond.getPhiMap().size())) {
//            if (precond.smtCheck() == Predicate.SMT_RESULT.UNSAT) {
//              System.out.println("Inner contradiction developed, discard block.");
//              continue;
//            }
//          }

          // if have specified the optAndStates.startingInstBranchesTo list, 
          // only take the specified branches at the starting basic block
          if (shouldCheckBranching(cfg, startingInst, curInvokeDepth, callStack, optAndStates, infoItem)) {
            
            // retain only the branches in startingInstBranchesTo list
            retainOnlyBranches(cfg, optAndStates.startingInstBranchesTo, normPredBB, excpPredBB);
          }
          
//          // only traverse exceptional paths when we come from a catch block
//          if (isCaught(precond)) {
//            // iterate all exceptional predecessors
//            pushChildrenBlocks(excpPredBB, false, infoItem, precond, methData,
//                Formula.EXCEPTIONAL_SUCCESSOR, dfsStack, optAndStates.maxLoop, valPrefix);            
//          }
          
//          // skip skippable conditional branches
//          List<ISSABasicBlock>[] preds = m_defAnalyzer.findSkipToBasicBlocks(cfg, infoItem.currentBB, precond);
//          List<ISSABasicBlock> skipToPreds  = preds[0];
//          List<ISSABasicBlock> notSkipPreds = preds[1];
//          int totalSkipped = normPredBB.size() - skipToPreds.size() - notSkipPreds.size();
//          if (totalSkipped > 0) {
//            System.out.println(totalSkipped + " branches skipped!");
//          }
//          
//          // iterate all normal predecessors
//          pushChildrenBlocks(skipToPreds, true, infoItem, precond, methData,
//              Predicate.NORMAL_SUCCESSOR, dfsStack, optAndStates.maxLoop, valPrefix);
//          pushChildrenBlocks(notSkipPreds, false, infoItem, precond, methData,
//              Predicate.NORMAL_SUCCESSOR, dfsStack, optAndStates.maxLoop, valPrefix);
          pushChildrenBlocks(normPredBB, false, infoItem, precond, methData,
              Formula.NORMAL_SUCCESSOR, workList, optAndStates.maxLoop, valPrefix);
        }
        else if (optAndStates.isEnteringCallStack()) {
          // at method entry, we still cannot find the proper invocation 
          // to enter call stack, throw InvalidStackTraceException
          String msg = "Failed to enter call stack: " + callStack.getNextMethodNameOrSign() + 
            " at " + callStack.getCurMethodNameOrSign() + ":" + callStack.getCurLineNo();
          System.err.println(msg);
          throw new InvalidStackTraceException(msg);
        }
        else if (curInvokeDepth != 0 || !callStack.isOutMostCall()) {
          // we only do smtCheck() if it's not inside an invocation and
          // it's at the outermost invocation
          execResult.addSatisfiable(precond);
          break; // return to InstHandler
        }
        else { // for entry block, no need to go further
          System.out.println("Performing " + (smtChecked + 1) + "th SMT Check...");
          
          // output propagation path
          printPropagationPath(infoItem);
          
          // use SMT Solver to check precond and obtain a model
          SMT_RESULT smtResult = m_smtChecker.smtCheck(precond, methData);
          precond.setSolverResult(m_smtChecker);
          
          // limit maximum smt checks
          boolean canBreak = false;
          if (optAndStates.maxSmtCheck > 0 && ++smtChecked >= optAndStates.maxSmtCheck) {
            execResult.setOverLimit(true);
            System.out.println("Reached Maximum SMT Check Limit!");
            canBreak = true;
          }
          
          if (smtResult == SMT_RESULT.SAT) {
            System.out.println("SMT Check succeeded!\n");
            
            // save the satisfiable precondition
            execResult.addSatisfiable(precond);
            
            // limit maximum number of satisfiable preconditions to retrieve
            if (optAndStates.maxRetrieve > 0 && ++satRetrieved >= optAndStates.maxRetrieve) {
              execResult.setReachMaximum(true);
              System.out.println("Reached Maximum Retrieve Limit!");
              canBreak = true;
            }
          }
          else {
            System.out.println("SMT Check failed!\n");
            
            // memory consumption may grow overtime as not-sat formula added
            if (optAndStates.saveNotSatResults) {
              // clear non-solver data in the not satisfiable formula object
              precond.clearNonSolverData();
              
              // save the not satisfiable precondition
              execResult.addNotSatisfiable(precond);
            }
          }
          
          // break loop if appropriate
          if (canBreak) {
            break;
          }
        }
      }
    }

    // end timing
    long end = System.currentTimeMillis();
    System.out.println("Method " + cfg.getMethod().getName() + " computation elapsed: " + (end - start) + "ms!\n");

    return execResult;
  }

  private Formula computeBB(GlobalOptionsAndStates optAndStates, CGNode cgNode, 
      MethodMetaData methData, BBorInstInfo infoItem, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, boolean[] starting, int curInvokeDepth, 
      String callSites, Stack<BBorInstInfo> workList) throws InvalidStackTraceException {
    
    Formula preCond = infoItem.postCond;
    SSAInstruction[] allInsts = methData.getcfg().getInstructions();
    
    // handle pi instructions first if any
    for (Iterator<SSAPiInstruction> it = infoItem.currentBB.iteratePis(); it.hasNext();) {
      SSAPiInstruction piInst = (SSAPiInstruction) it.next();
      if (piInst != null) {
        preCond = m_instHandler.handle(optAndStates, cgNode, preCond, piInst, 
            infoItem, callStack, curInvokeDepth);
      }
    }

    // handle normal instructions
    boolean lastInst   = true;
    // for skipToBB, the last instruction is always conditional branch, skip!
    int currInstIndex  = infoItem.currentBB.getLastInstructionIndex() - (infoItem.isSkipToBB ? 1 : 0);
    int firstInstIndex = infoItem.currentBB.getFirstInstructionIndex();
    while (currInstIndex >= 0 && currInstIndex >= firstInstIndex) {
      // get instruction
      SSAInstruction inst = allInsts[currInstIndex];

      IR ir = null;
      if (inst instanceof SSAInvokeInstruction && (infoItem.target == null || !infoItem.target.getKey().equals(inst))) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
        MethodReference mr = invokeInst.getDeclaredTarget();
        
        if (cgNode != null) {
          // find the potential targets of this invocation
          SimpleEntry<IR[], CGNode[]> ret = WalaUtils.findInvocationTargets(
              m_walaAnalyzer, cgNode, invokeInst.getCallSite(), optAndStates.maxDispatchTargets);
          IR[] targetIRs       = ret.getKey();
          CGNode[] targetNodes = ret.getValue();
        
          // if found new targets, add them
          if (targetIRs.length > 0 && targetIRs[0] != null && !targetIRs[0].getMethod().getSignature().equals(mr.getSignature())) {
            for (int i = 0; i < targetNodes.length; i++) {
              BBorInstInfo newInfo = infoItem.clone();
              newInfo.target = new SimpleEntry<SSAInvokeInstruction, CGNode>(invokeInst, targetNodes[i]);
              workList.add(newInfo);
            }
            return infoItem.postCond;
          }
          else if (targetIRs.length > 0) {
            infoItem.target = new SimpleEntry<SSAInvokeInstruction, CGNode>(invokeInst, targetNodes[0]);
          }
          else {
            infoItem.target = new SimpleEntry<SSAInvokeInstruction, CGNode>(invokeInst, null);
          }
        }
        else {
          infoItem.target = new SimpleEntry<SSAInvokeInstruction, CGNode>(invokeInst, null);
        }
      }
      else if (inst instanceof SSAInvokeInstruction && infoItem.target != null && infoItem.target.getValue() != null) {
        ir = infoItem.target.getValue().getIR();
      }
      
      
      // determine if entering call stack is still correct
      int currLine = methData.getLineNumber(currInstIndex);
      if (optAndStates.isEnteringCallStack()) {
        // determine if entering call stack is still correct
        if (callStack.getCurLineNo() == currLine) {
          if (inst instanceof SSAInvokeInstruction) {
            String methodNameOrSign = callStack.getNextMethodNameOrSign();
            
            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
            MethodReference mr = (ir == null) ? invokeInst.getDeclaredTarget() : ir.getMethod().getReference();
            
            // FIXME: Should handle polymorphism! mr could be a method
            // of an interface while methodNameOrSign is the concrete method

            // determine if entering call stack is still correct
            if (!isTheSameOrChildMethod(mr, methodNameOrSign)) {
              // skip this instruction
              inst = null;
            }
          }
          else {
            // skip this instruction
            inst = null;
          }
        }
        else {
          // if we cannot enter call stack correctly, no need to continue
          String msg = "Failed to enter call stack: " + callStack.getNextMethodNameOrSign() + 
            " at " + callStack.getCurMethodNameOrSign() + ":" + callStack.getCurLineNo();
          System.err.println(msg);
          throw new InvalidStackTraceException(msg);
        }
      }
      
      if (inst != null && (!starting[0] || (currLine - (inclLine ? 1 : 0)) < startLine)) {
        boolean inclStartingInst = optAndStates.inclStartingInst;
        
        // if it is at the inner most frame, start from the starting index
        if (!starting[0] || startingInst < 0 || 
            (currInstIndex - (inclStartingInst ? 1 : 0)) < startingInst) {
          
          // get precond for this instruction
          if (lastInst) {
            preCond = m_instHandler.handle(optAndStates, cgNode, preCond, inst, 
                infoItem, callStack, curInvokeDepth);
          }
          else {
            // not the last instruction of the block
            BBorInstInfo instInfo = new BBorInstInfo(infoItem.currentBB, 
                infoItem.isSkipToBB, preCond, Formula.NORMAL_SUCCESSOR, 
                infoItem.sucessorBB, infoItem.sucessorInfo, methData, callSites, infoItem.workList, this);
            preCond = m_instHandler.handle(optAndStates, cgNode, preCond, inst, 
                instInfo, callStack, curInvokeDepth);
          }
        }

        // already passed the nStartLine, no need to
        // limit the currLine anymore
        if (starting[0] && currLine < startLine) {
          starting[0] = false;
        }
      }
      else if (inst == null) {
        // if it's a ShrikeCFG ConstantInstruction, we do phiMap
        // assignment for this constant value!
        String constantStr = AbstractHandler.getConstantInstructionStr(currInstIndex, methData);
        if (constantStr != null) {
          preCond = m_instHandler.handle_constant(preCond, null, infoItem, constantStr);
        }
      }

      // get previous inst
      currInstIndex--;
      lastInst = false;
    }
    
    // handle catch instructions at the entry of a catch block
    if (infoItem.currentBB.isCatchBlock()) {
      preCond = m_instHandler.handle_catch(preCond, null, infoItem);
    }
    
//    // handle phi instructions last if any
//    for (Iterator<SSAPhiInstruction> it = infoItem.currentBB.iteratePhis(); it.hasNext();) {
//      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
//      if (phiInst != null) {
//        preCond = m_instHandler.handle(optAndStates, cgNode, preCond, phiInst, 
//            infoItem, callStack, curInvokeDepth);
//      }
//    }
    
    // some wrap up at the entry block
    if (infoItem.currentBB.isEntryBlock()) {
      preCond = m_instHandler.handle_entryblock(preCond, null, infoItem);
    }      

    return preCond;
  }
  
  private boolean isTheSameOrChildMethod(MethodReference mr, String methodNameOrSign) {
    boolean isSame = false;
    
    methodNameOrSign = methodNameOrSign.replace('$', '.');
    if (Utils.isMethodSignature(methodNameOrSign)) {
      // get invoking method signature
      String invokingMethod = mr.getSignature();
      isSame = invokingMethod.equals(methodNameOrSign);
    }
    else {
      String declaringClass = mr.getDeclaringClass().getName().toString();
      declaringClass = Utils.getClassTypeJavaStr(declaringClass);
      
      // get invoking method name
      String invokingMethod = declaringClass + "." + mr.getName().toString();
      // naive compare first, sometimes can avoid including subject jar in the classpath
      if (invokingMethod.equals(methodNameOrSign)) {
        isSame = true;
      }
      else {
        try {
          // get class object
          Class<?> cls = Class.forName(declaringClass);
          for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            // get invoking method name
            invokingMethod = c.getName() + "." + mr.getName().toString();
            if (invokingMethod.equals(methodNameOrSign)) {
              isSame = true;
              break;
            }
          }
        } catch (ClassNotFoundException e) {}        
      }
    }
    return isSame;
  }
  
  private boolean shouldCheckBranching(SSACFG cfg, int startingInst, int curInvokeDepth, 
      CallStack callStack, GlobalOptionsAndStates optAndStates, BBorInstInfo infoItem) {
    return curInvokeDepth == 0 && callStack.getDepth() <= 1 && 
        optAndStates.startingInst >= 0 && optAndStates.startingInstBranchesTo != null && 
        infoItem.sucessorBB == null && 
        infoItem.currentBB.equals(cfg.getBlockForInstruction(startingInst));
  }
  
  private void retainOnlyBranches(SSACFG cfg, int[] branchesTo, 
    Collection<ISSABasicBlock> normPredBB, Collection<ISSABasicBlock> excpPredBB) {

    // only branching to certain basic blocks
    HashSet<ISSABasicBlock> branchingToBBs = new HashSet<ISSABasicBlock>();
    for (int i = 0; i < branchesTo.length; i++) {
      if (branchesTo[i] < cfg.getInstructions().length) {
        ISSABasicBlock bb = cfg.getBlockForInstruction(branchesTo[i]);
        if (bb != null) {
          branchingToBBs.add(bb);
        }        
      }
    }
    
    // remove not using predecessors
    normPredBB.retainAll(branchingToBBs);
    excpPredBB.retainAll(branchingToBBs);
    
    System.out.print("Only retain basic blocks: ");
    Iterator<ISSABasicBlock> iter = normPredBB.iterator();
    while (iter.hasNext()) {
      System.out.print(((ISSABasicBlock)iter.next()).getNumber() + " ");
    }
    iter = excpPredBB.iterator();
    while (iter.hasNext()) {
      System.out.print(((ISSABasicBlock)iter.next()).getNumber() + " ");
    }
    System.out.println();
  }
  
  private void pushChildrenBlocks(Collection<ISSABasicBlock> allChildrenBlocks,
      boolean areSkipToBBs, BBorInstInfo currentInfo, Formula precond, 
      MethodMetaData methData, int successorType, Stack<BBorInstInfo> workList, 
      int maxLoop, String valPrefix) {

    Iterator<ISSABasicBlock> iterBB = allChildrenBlocks.iterator();
    
    // get all visited records
    Hashtable<ISSABasicBlock, Integer> visitedBB = precond.getVisitedRecord();
    
    List<ISSABasicBlock> visitedList    = new ArrayList<ISSABasicBlock>();
    List<ISSABasicBlock> notvisitedList = new ArrayList<ISSABasicBlock>();
    while (iterBB.hasNext()) {
      ISSABasicBlock basicBlock = iterBB.next();
      
      // make sure we are not pushing the current node again.
      // Sometimes, a monitorexit node can be a child of itself, 
      // making the search endless.
      if (basicBlock.getNumber() == currentInfo.currentBB.getNumber()) {
        continue;
      }
      
      Integer count = visitedBB.get(basicBlock);
      if (count != null && count < maxLoop) {
        visitedList.add(basicBlock);
      }
      else if (count == null) {
        notvisitedList.add(basicBlock);
      }
    }

    // sort them from large BB number to small BB number
    Collections.sort(visitedList, new Comparator<ISSABasicBlock>(){ 
      public int compare(ISSABasicBlock arg0, ISSABasicBlock arg1) { 
        return arg1.getNumber() - arg0.getNumber();
      } 
    });
    Collections.sort(notvisitedList, new Comparator<ISSABasicBlock>(){ 
      public int compare(ISSABasicBlock arg0, ISSABasicBlock arg1) { 
        return arg1.getNumber() - arg0.getNumber();
      } 
    });
    
    // decide phis if any
    Hashtable<ISSABasicBlock, Formula> newPreConds = null;
    Iterator<SSAPhiInstruction> phiInsts = currentInfo.currentBB.iteratePhis();
    if (phiInsts.hasNext()) {
      newPreConds = decidePhis(currentInfo, phiInsts, precond);
      for (Formula newPreCond : newPreConds.values()) { // put back visited record
        newPreCond.setVisitedRecord(visitedBB, null);
      }
    }
    
    // push the visited ones into the beginning of the stack
    for (ISSABasicBlock visited : visitedList) {
      // we don't check precond at the moment
      Formula newPreCond = newPreConds == null ? precond : newPreConds.get(visited);
      workList.push(new BBorInstInfo(visited, areSkipToBBs, newPreCond, successorType, 
          currentInfo.currentBB, currentInfo, methData, valPrefix, null, this));
    }
    
    // push the non visited ones into the beginning of the stack
    for (ISSABasicBlock notvisited : notvisitedList) {
      // we don't check precond at the moment
      Formula newPreCond = newPreConds == null ? precond : newPreConds.get(notvisited);
      workList.push(new BBorInstInfo(notvisited, areSkipToBBs, newPreCond, successorType, 
          currentInfo.currentBB, currentInfo, methData, valPrefix, null, this));
    }
  }
  
  private Hashtable<ISSABasicBlock, Formula> decidePhis(BBorInstInfo instInfo, 
      Iterator<SSAPhiInstruction> phiInsts, Formula postCond) {
    Hashtable<ISSABasicBlock, Formula> newPreConds = new Hashtable<ISSABasicBlock, Formula>();

    // sort predecessors of the current block
    List<ISSABasicBlock> predList = new ArrayList<ISSABasicBlock>();
    Iterator<ISSABasicBlock> predBBs = instInfo.methData.getcfg().getPredNodes(instInfo.currentBB);
    while (predBBs.hasNext()) {
      predList.add(predBBs.next());
    }
    final int currentBBNum = instInfo.currentBB.getNumber();
    Collections.sort(predList, new java.util.Comparator<ISSABasicBlock>() {
      @Override
      public int compare(ISSABasicBlock o1, ISSABasicBlock o2) {
        return Math.abs(o2.getNumber() - currentBBNum) - Math.abs(o1.getNumber() - currentBBNum);
      }
    });
    
    // go through all phi instructions of the block
    for (Iterator<SSAPhiInstruction> it = phiInsts; it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        for (int i = 0; i < predList.size() && i < phiInst.getNumberOfUses(); i++) {
          ISSABasicBlock pred = predList.get(i);
          Formula newPreCond = newPreConds.get(pred);
          newPreCond = m_instHandler.handle_phi(newPreCond == null ? postCond : newPreCond, 
              phiInst, instInfo, phiInst.getUse(i), newPreCond == null);
          newPreConds.put(pred, newPreCond);
        }
      }
    }
    
    return newPreConds;
  }

  // if lineNumber is <= 0, we return the exit block
  private ISSABasicBlock findBasicBlock(MethodMetaData methData, int lineNumber, int instIndex) {
    SSACFG cfg = methData.getcfg();
    
    ISSABasicBlock foundBB = null;
    if (lineNumber > 0 && instIndex < 0) {
      // try to retrieve the BB with the largest block number
      int largestBBNum         = -1;
      ISSABasicBlock largestBB = null;

      int instCount = cfg.getInstructions().length;
      for (int i = 0; i < instCount; i++) {
        if (cfg.getInstructions()[i] != null) {
          int line = methData.getLineNumber(i);
          if (line == lineNumber) {
            int bbNum = cfg.getBlockForInstruction(i).getNumber();
            if (bbNum > largestBBNum) {
              largestBBNum = bbNum;
              largestBB = cfg.getBlockForInstruction(i);
            }
          }
        }
      }
      foundBB = largestBB;
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
        foundBB = cfg.getBlockForInstruction(instIndex);
      }
    }
    else {
      // get the exit block
      foundBB = cfg.getBasicBlock(cfg.getMaxNumber());
    }
    return foundBB;
  }
  
//  private boolean isCaught(Formula formula) {
//    return formula.getVarMap().containsKey("Caught");
//  }
//  
//  /**
//   * Assuming there is at most one 'Caught ...' at a time.
//   */
//  private boolean isCatchNeverTriggered(Formula formula) {
//    return  formula.getVarMap().containsKey("Caught") && 
//           !formula.getVarMap().containsKey("ThrownInstCurrent");
//  }
  
  private void printPropagationPath(BBorInstInfo entryNode) {
    // output propagation path
    System.out.print("Computation Path: ");
    Stack<Integer> computePath = new Stack<Integer>();
    computePath.push(0);
    BBorInstInfo currentBB = entryNode;
    while (currentBB.sucessorInfo != null) {
      computePath.push(currentBB.sucessorBB.getNumber());
      currentBB = currentBB.sucessorInfo;
    }
    while (!computePath.empty()) {
      System.out.print(computePath.pop() + " -> ");
    }
    System.out.println("SMT Check");
  }

  private void printResult(Formula preCond) {
    if (preCond != null) {
      System.out.println("SMT Solver Input-----------------------------------");
      System.out.println(preCond.getLastSolverInput());
      
      System.out.println("SMT Solver Output----------------------------------");
      System.out.println(preCond.getLastSolverOutput());
      
//      System.out.println("Parsed SatModel----------------------------------");
//      for (int i = 0, size = preCond.getLastSatModel().size(); i < size; i++) {
//        System.out.println(preCond.getLastSatModel().get(i).toYicesExprString());
//      }
//      System.out.println();

      System.out.println("Path Conditions--------------------------------");
      HashSet<String> outputted = new HashSet<String>();
      List<Condition> conditions = preCond.getConditionList();
      for (int i = conditions.size() - 1; i >= 0; i--) {
        String conditionStr = conditions.get(i).toString();

        if (!outputted.contains(conditionStr)) {
          System.out.println(conditionStr);
          outputted.add(conditionStr);
        }
      }
      System.out.println();
      
      System.out.println("Path RefMap-------------------------------------");
      Hashtable<String, Hashtable<String, Reference>> refMap = preCond.getRefMap();
      Enumeration<String> keys = refMap.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        System.out.println("Method Callsites: " + key);
        Hashtable<String, Reference> methodRefs = refMap.get(key);
        Enumeration<String> keys2 = methodRefs.keys();
        while (keys2.hasMoreElements()) {
          String key2 = (String) keys2.nextElement();
          System.out.println(methodRefs.get(key2).toString());
        }
      }
    }
    else {
      System.out.println("The line is unreachable!");
      // can't find any good precond, human??? should
      // remember those timeout smtchecks
    }
  }
  
  @SuppressWarnings("unused")
  private void printIR(IR ir) {
    // output CFG
    SSACFG cfg = ir.getControlFlowGraph();
    System.out.println(cfg.toString());

    // output Instructions
    SSAInstruction[] instructions = ir.getInstructions();
    for (SSAInstruction instruction : instructions) {
      System.out.println(instruction.toString());
    }
  }

  @SuppressWarnings("unused")
  private void heapTracer() {
    try {
      Result r = (new HeapTracer(Collections.emptySet(), true)).perform();
      System.err.println(r.toString());
      System.err.flush();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
  
  public static void main(String args[]) {
    try {
      // compute def result first
//      DefAnalyzerWrapper defAnalyzer = new DefAnalyzerWrapper(args[0]);
//      defAnalyzer.addIncludeName("test_program1.");
//      defAnalyzer.computeDef(10);
      
      AbstractHandler instHandler = new CompleteBackwardHandler();
      SMTChecker smtChecker = new SMTChecker(SMTChecker.SOLVERS.YICES);
      Executor executor = new Executor(args[0]/*jar file path*/, instHandler, smtChecker, null);
      
      // read stack frames
      CallStack callStack = new CallStack(true);
      for (int i = 1; i < args.length; i++) {
        String methName = args[i] /*method name*/;
        int lineNo      = Integer.parseInt(args[++i]) /*line number*/;
        
        callStack.addStackTrace(methName, lineNo);
      }
      
      // set options
      GlobalOptionsAndStates optAndStates = executor.new GlobalOptionsAndStates(callStack, false);
      optAndStates.maxDispatchTargets = 2;
      optAndStates.maxRetrieve        = 10;
      optAndStates.maxSmtCheck        = 5000;
      optAndStates.maxInvokeDepth     = 1;
      optAndStates.maxLoop            = 7;
      
      executor.compute(optAndStates, null);
      // wp.heapTracer();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private final WalaAnalyzer        m_walaAnalyzer;
  private final AbstractHandler     m_instHandler;
  private final SMTChecker          m_smtChecker;
  private final DefAnalyzerWrapper  m_defAnalyzer;
  private MethodMetaData            m_methMetaData;
  private ExecutionResult           m_execResult;

  private Hashtable<BBorInstInfo, SSAInstruction> m_callStackInvokes;
}
