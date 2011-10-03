package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.InvalidStackTraceException;
import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteBackwardHandler;
import hk.ust.cse.Prevision.Optimization.DefAnalyzerWrapper;
import hk.ust.cse.Prevision.Optimization.HeuristicBacktrack;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
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

import com.ibm.wala.classLoader.IMethod;
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

  public class BBorInstInfo {
    public BBorInstInfo(ISSABasicBlock currentBB, boolean isSkipToBB, Formula postCond, 
        Formula postCond4BB, int sucessorType, ISSABasicBlock sucessorBB, BBorInstInfo sucessorInfo, 
        MethodMetaData methData, String callSites, Stack<BBorInstInfo> workList, Executor executor) {
      this.currentBB    = currentBB;
      this.isSkipToBB   = isSkipToBB;
      this.postCond     = postCond;
      this.postCond4BB  = postCond4BB;
      this.sucessorType = sucessorType;
      this.sucessorBB   = sucessorBB;
      this.sucessorInfo = sucessorInfo;
      this.methData     = methData;
      this.callSites    = callSites;
      this.workList     = workList; // only used for invocation instructions
      this.executor     = executor;
    }
    
    public BBorInstInfo clone() {
      BBorInstInfo newInfo = new BBorInstInfo(currentBB, isSkipToBB, postCond, postCond4BB, 
          sucessorType, sucessorBB, sucessorInfo, methData, callSites, workList, executor);
      newInfo.target = target;
      return newInfo;
    }

    public Stack<BBorInstInfo>                       workList;
    public SimpleEntry<SSAInvokeInstruction, CGNode> target;
    
    public Formula                   postCond;
    public Formula                   postCond4BB;
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
  
  public ExecutionResult compute(ExecutionOptions execOptions, Formula postCond) throws InvalidStackTraceException {
    return compute(execOptions, postCond, null);
  }
  
  public ExecutionResult compute(ExecutionOptions execOptions, Formula postCond, IR ir) throws InvalidStackTraceException {
    CallStack fullStack = execOptions.fullCallStack;
    
    // compute from the outermost stack frame
    String methNameOrSig = fullStack.getCurMethodNameOrSign();
    int lineNo           = fullStack.getCurLineNo();
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = true;
    int startingInst = -1;
    if (fullStack.getDepth() == 1) {
      inclLine     = execOptions.inclInnerMostLine;
      startingInst = execOptions.startingInst;
    }

    // get ir(ssa) for methods in jar file
    if (ir == null) {
      ir = Utils.isMethodSignature(methNameOrSig) ? Jar2IR.getIR(m_walaAnalyzer, methNameOrSig) : 
                                                    Jar2IR.getIR(m_walaAnalyzer, methNameOrSig, lineNo);
      // return if method is not found
      if (ir == null) {
        String msg = "Failed to locate " + methNameOrSig + ":" + lineNo;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }
      
      // re-compute call graph with ir (outermost) since main()s may not be good enough
      HashSet<IMethod> additionalEntrypoints = new HashSet<IMethod>();
      additionalEntrypoints.add(ir.getMethod());
      m_walaAnalyzer.recomputeCallGraph(additionalEntrypoints, execOptions.callGraphBuilder);
    }
      
    // get the initial cgNode
    CGNode cgNode = (execOptions.maxDispatchTargets > 0) ? m_walaAnalyzer.getCallGraph().getNode(ir.getMethod()) : null;
    
    // the initial outter most workList
    Stack<BBorInstInfo> workList = new Stack<BBorInstInfo>();
    
    return computeRec(execOptions, cgNode, ir, lineNo, startingInst, inclLine, 
        fullStack, 0, "", workList, postCond);
  }
  
  public ExecutionResult computeRec(ExecutionOptions execOptions, 
      CGNode cgNode, String methNameOrSig, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula postCond) throws InvalidStackTraceException {
    
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
        inclLine, callStack, curInvokeDepth, callSites, workList, postCond);
  }

  /**
   * @param cgNode: only useful when we use 'compDispatchTargets' function
   */
  public ExecutionResult computeRec(ExecutionOptions execOptions, 
      CGNode cgNode, IR ir, int startLine, int startingInst, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula postCond) throws InvalidStackTraceException {
    
    assert(ir != null);
    
    // start timing
    long start = System.currentTimeMillis();

    // init MethodMetaData, save it if it's not inside an 
    // invocation and it's at the out most call
    MethodMetaData methMetaData = new MethodMetaData(ir);
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_methMetaData = methMetaData;

      // create container to save the invoke instructions at call stack points
      m_callStackInvokes = new Hashtable<BBorInstInfo, SSAInstruction>();
      
      // clear m_execResult
      m_execResult = null;
      
      // for heuristic backtracking
      m_heuristicBacktrack = new HeuristicBacktrack(m_smtChecker);
    }
    
    if (workList.empty()) { // new worklist, simply start from startLine
      ISSABasicBlock startFromBB = findBasicBlock(methMetaData, startLine, startingInst);
      if (startFromBB == null) {
        String msg = "Failed to find a valid basic block at line: " + startLine + " and instruction index: " + startingInst;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }

      // create an initial Formula if postCond is null
      if (postCond == null) {
        postCond = new Formula(); // TRUE formula
      }

      // push in the first block
      workList.push(new BBorInstInfo(startFromBB, false, postCond, postCond, 
          Formula.NORMAL_SUCCESSOR, null, null, methMetaData, callSites, null, this));
    }

    // start depth first search for this method
    ExecutionResult execResult = computeMethod(execOptions, cgNode, methMetaData, 
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
      if (execOptions.clearSatNonSolverData) {
        m_execResult.clearAllSatNonSolverData();
      }

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
 
  private ExecutionResult computeMethod(ExecutionOptions execOptions,
      CGNode cgNode, MethodMetaData methData, Stack<BBorInstInfo> workList, 
      int startLine, int startingInst, boolean inclLine, CallStack callStack, 
      int curInvokeDepth, String callSites) throws InvalidStackTraceException {

    // start timing
    long start = System.currentTimeMillis();

    // we don't want to return a null execResult
    ExecutionResult execResult = new ExecutionResult();
    int smtChecked   = 0;
    int satRetrieved = 0;
    
    // get cfg for later use
    SSACFG cfg = methData.getcfg();
    
    // if startLine <= 0, we start from exit block
    boolean[] starting = new boolean[1];
    starting[0] = startLine > 0;
    
    // if callStack's depth == 1, finished entering call stack
    if (execOptions.isEnteringCallStack() && callStack.getDepth() <= 1) {
      execOptions.finishedEnteringCallStack();
      
      // create the real initial post-condition according to crash type
      BBorInstInfo initInfoItem = workList.peek();
      Formula initFormula = PrepInitFormula.prepInitFormula(initInfoItem, execOptions, callStack);
      initInfoItem.postCond    = initFormula;
      initInfoItem.postCond4BB = initFormula;
    }

    // output method name
    System.out.println("Computing method: " + cfg.getMethod().getSignature());
    
    // continue backtrack if necessary
    if (execOptions.heuristicBacktrack && m_heuristicBacktrack.isContinueInMethod()) {
      m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
    } 
    
    // start depth first search
    while (!workList.empty()) {
      // continue backtrack if necessary
      if (execOptions.heuristicBacktrack && m_heuristicBacktrack.isBacktracking()) {
        m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
        if (workList.empty()) {
          continue;
        }
      }

      // next block to compute
      BBorInstInfo infoItem = workList.pop();

      //if (!infoItem.postCond.isContradicted()) {
      if (true) {
        int instIndex = infoItem.currentBB.getLastInstructionIndex();
        String lineNo = (instIndex >= 0) ? " @ line " + methData.getLineNumber(instIndex) : "";
        System.out.println("Computing BB" + infoItem.currentBB.getNumber() + lineNo);

        // get visited records
        Hashtable<ISSABasicBlock, Integer> visitedBB = infoItem.postCond.getVisitedRecord();
        
        // compute for this BB
        int oriStackSize = workList.size();
        Formula precond = null;
        try {
          precond = computeBB(execOptions, cgNode, methData, infoItem, startLine, 
              startingInst, inclLine, callStack, starting, curInvokeDepth, callSites, workList);
        } catch (InvalidStackTraceException e) {
          if (workList.size() == 0) {
            // if we cannot enter call stack correctly, no need to continue
            System.err.println(e.getMessage());
            throw e;
          }
        }
        
        if (precond == null || // an inner contradiction has been detected
            workList.size() > oriStackSize) { // if new invocation targets have been pushed into stack 
                                               // due to target dispatch, skip the current instruction
          continue; 
        }

        // marked as visited
        precond.setVisitedRecord(visitedBB, infoItem);

        // re-push if can't pop: a new workList created for this BB has not yet finished
        if (infoItem.workList != null && !infoItem.workList.empty()) {
          workList.push(infoItem);
          
          // add to watch list
          m_heuristicBacktrack.addToWatchList(infoItem, precond);
        }
        else {
          // don't need to watch anymore
          m_heuristicBacktrack.removeFromWatchList(infoItem);
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
          
          // on the fly checks
          Hashtable<String, Reference> methodRefs = precond.getRefMap().get(infoItem.callSites);
          if (execOptions.checkOnTheFly && normPredBB.size() > 1) {
            if (!infoItem.currentBB.isExitBlock() || methodRefs == null || !methodRefs.containsKey("RET")) {
              SMT_RESULT smtResult = m_smtChecker.smtCheck(precond, methData, false, execOptions.heuristicBacktrack);
              precond.setSolverResult(m_smtChecker);
              
              // trigger callbacks
              for (Object[] m : execOptions.getAfterOnTheFlyCheckCallback()) {
                try {
                  ((Method) m[0]).invoke(m[1], precond);
                } catch (Exception e) {e.printStackTrace();}
              }
              
              if (smtResult == Formula.SMT_RESULT.UNSAT) {
                System.out.println("Inner contradiction developed, discard block.");
                
                // once smt check failed, we track back heuristically
                if (execOptions.heuristicBacktrack) {
                  m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
                }
                continue;
              }
            }
          }

          // if have specified the execOptions.startingInstBranchesTo list, 
          // only take the specified branches at the starting basic block
          if (shouldCheckBranching(cfg, startingInst, curInvokeDepth, callStack, execOptions, infoItem)) {
            
            // retain only the branches in startingInstBranchesTo list
            retainOnlyBranches(cfg, execOptions.startingInstBranchesTo, normPredBB, excpPredBB);
          }
          
//          // only traverse exceptional paths when we come from a catch block
//          if (isCaught(precond)) {
//            // iterate all exceptional predecessors
//            pushChildrenBlocks(excpPredBB, false, infoItem, precond, methData,
//                Formula.EXCEPTIONAL_SUCCESSOR, dfsStack, execOptions.maxLoop, valPrefix);            
//          }
          
          // try to skip method
          if (execOptions.skipUselessMethods && infoItem.currentBB.isExitBlock() && 
              (methodRefs == null || !methodRefs.containsKey("RET"))) {
            ISSABasicBlock skipToEntryBB = m_defAnalyzer.findSkipToBasicBlocks(methData.getIR(), precond);
            if (skipToEntryBB != null) {
              normPredBB.clear();
              normPredBB.add(skipToEntryBB);
              System.out.println(methData.getMethodSignature() + " is skipped, not useful!");
            }
          }

          // decide phis if any
          Hashtable<ISSABasicBlock, Formula> phiedPreConds = null;
          boolean phiDefsUseful = phiDefsUseful(infoItem, precond);
          if (phiDefsUseful) {
            phiedPreConds = decidePhis(infoItem, precond);
            for (Formula phiedPreCond : phiedPreConds.values()) { // put back visited record
              phiedPreCond.setVisitedRecord(precond.getVisitedRecord(), null);
            }
          }
          else { // no phiInst, keep the originals
            phiedPreConds = new Hashtable<ISSABasicBlock, Formula>();
            for (ISSABasicBlock normPred : normPredBB) {
              phiedPreConds.put(normPred, precond);
            }
          }
          
          // try to skip branches
          int totalSkipped = 0;
          List<Object[]> bbPreConds = new ArrayList<Object[]>();
          Enumeration<ISSABasicBlock> keys = phiedPreConds.keys();
          while (keys.hasMoreElements()) {
            ISSABasicBlock pred  = keys.nextElement();
            Formula phiedPreCond = phiedPreConds.get(pred);
            if (execOptions.skipUselessBranches && !phiDefsUseful) {
              ISSABasicBlock skipToCondBB = m_defAnalyzer.findSkipToBasicBlocks(methData.getIR(), 
                  infoItem.currentBB, pred, phiedPreCond, infoItem.callSites);
              if (skipToCondBB != null) {
                if (!phiDefsUseful) {
                  Collection<ISSABasicBlock> skipToPredBBs =
                    cfg.getNormalPredecessors(skipToCondBB);
                  for (ISSABasicBlock skipToPredBB : skipToPredBBs) {
                    boolean contains = false;
                    for (Object[] bbPreCond : bbPreConds) {
                      if (bbPreCond[0] == skipToPredBB && bbPreCond[1] == phiedPreCond) {
                        contains = true;
                        break;
                      }
                    }
                    if (!contains) {
                      bbPreConds.add(new Object[] {skipToPredBB, phiedPreCond});
                    }
                  }
                }
                else {
                  List<Object[]> condBBToPush = new ArrayList<Object[]>();
                  condBBToPush.add(new Object[] {skipToCondBB, phiedPreCond});

                  ISSABasicBlock currentBB = pred.getNumber() + 1 < infoItem.currentBB.getNumber() ? 
                      cfg.getBasicBlock(skipToCondBB.getNumber() + 1) : infoItem.currentBB;
                  BBorInstInfo newInfoItem = new BBorInstInfo(currentBB, 
                      infoItem.isSkipToBB, infoItem.postCond, infoItem.postCond4BB, 
                      infoItem.sucessorType, infoItem.sucessorBB, infoItem.sucessorInfo, 
                      infoItem.methData, infoItem.callSites, infoItem.workList, infoItem.executor);
                  pushChildrenBlocks(condBBToPush, false, newInfoItem, methData,
                      Formula.NORMAL_SUCCESSOR, workList, execOptions.maxLoop, callSites);
                }
                totalSkipped++;
              }
              else {
                bbPreConds.add(new Object[] {pred, phiedPreCond});
              }
            }
            else {
              bbPreConds.add(new Object[] {pred, phiedPreCond});
            }
          }
          if (totalSkipped > 0) {
            System.out.println(totalSkipped + " branches skipped!");
          }
          
          // iterate all normal predecessors
          pushChildrenBlocks(bbPreConds, false, infoItem, methData,
              Formula.NORMAL_SUCCESSOR, workList, execOptions.maxLoop, callSites);
        }
        else if (execOptions.isEnteringCallStack()) {
          // at method entry, cannot find the proper invocation to enter call stack
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
          SMT_RESULT smtResult = m_smtChecker.smtCheck(precond, methData, true, execOptions.heuristicBacktrack);
          precond.setSolverResult(m_smtChecker);
          
          // trigger callbacks
          for (Object[] m : execOptions.getAfterSmtCheckCallbacks()) {
            try {
              ((Method) m[0]).invoke(m[1], precond);
            } catch (Exception e) {e.printStackTrace();}
          }
          
          // limit maximum smt checks
          boolean canBreak = false;
          if (execOptions.maxSmtCheck > 0 && ++smtChecked >= execOptions.maxSmtCheck) {
            execResult.setOverLimit(true);
            System.out.println("Reached Maximum SMT Check Limit!");
            canBreak = true;
          }
          
          if (smtResult == SMT_RESULT.SAT) {
            System.out.println("SMT Check succeeded!\n");
            
            // save the satisfiable precondition
            execResult.addSatisfiable(precond);
            
            // limit maximum number of satisfiable preconditions to retrieve
            if (execOptions.maxRetrieve > 0 && ++satRetrieved >= execOptions.maxRetrieve) {
              execResult.setReachMaximum(true);
              System.out.println("Reached Maximum Retrieve Limit!");
              canBreak = true;
            }
            
            // clear out watch list since there is already a sat along this path
            if (execOptions.heuristicBacktrack) {
              m_heuristicBacktrack.clearWatchList();
            }
          }
          else {
            System.out.println("SMT Check failed!\n");
            
            // once smt check failed, we track back heuristically
            if (execOptions.heuristicBacktrack && smtResult == SMT_RESULT.UNSAT) {
              m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
            }
            
            // memory consumption may grow overtime as not-sat formula added
            if (execOptions.saveNotSatResults) {
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

  private Formula computeBB(ExecutionOptions execOptions, CGNode cgNode, 
      MethodMetaData methData, BBorInstInfo infoItem, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, boolean[] starting, int curInvokeDepth, 
      String callSites, Stack<BBorInstInfo> workList) throws InvalidStackTraceException {
    
    Formula preCond = infoItem.postCond;
    SSAInstruction[] allInsts = methData.getcfg().getInstructions();
    
    // handle pi instructions first if any
    for (Iterator<SSAPiInstruction> it = infoItem.currentBB.iteratePis(); it.hasNext();) {
      SSAPiInstruction piInst = (SSAPiInstruction) it.next();
      if (piInst != null) {
        preCond = m_instHandler.handle(execOptions, cgNode, preCond, piInst, 
            infoItem, callStack, curInvokeDepth);
      }
    }

    // handle normal instructions
    boolean lastInst   = true;
    // for skipToBB, the last instruction is always conditional branch, skip!
    int currInstIndex  = infoItem.currentBB.getLastInstructionIndex() - (infoItem.isSkipToBB ? 1 : 0);
    int firstInstIndex = infoItem.currentBB.getFirstInstructionIndex();
    while (currInstIndex >= 0 && currInstIndex >= firstInstIndex && preCond != null) {
      // get instruction
      SSAInstruction inst = allInsts[currInstIndex];
      int currLine = methData.getLineNumber(currInstIndex);

      if (!starting[0] || (currLine - (inclLine ? 1 : 0)) < startLine) {
        IR ir = null;
        if (inst instanceof SSAInvokeInstruction && (infoItem.target == null || !infoItem.target.getKey().equals(inst))) {
          SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
          MethodReference mr = invokeInst.getDeclaredTarget();
          
          if (cgNode != null) {
            // find the potential targets of this invocation
            int dispTargetsToGet = execOptions.isEnteringCallStack() ? Integer.MAX_VALUE : execOptions.maxDispatchTargets;
            SimpleEntry<IR[], CGNode[]> ret = WalaUtils.findInvocationTargets(
                m_walaAnalyzer, cgNode, invokeInst.getCallSite(), dispTargetsToGet);
            IR[] targetIRs       = ret.getKey();
            CGNode[] targetNodes = ret.getValue();
          
            // if found new targets, add them
            if (targetIRs.length > 0 && targetIRs[0] != null && 
               !targetIRs[0].getMethod().getSignature().equals(mr.getSignature())) {
              System.out.println("Multiple invocation targets discovered: " + (targetIRs.length) + " target(s)!");
              for (int i = 0; i < targetNodes.length; i++) {
                if (!execOptions.isEnteringCallStack() || 
                    isTheSameOrChildMethod(targetIRs[i].getMethod().getReference(), callStack.getNextMethodNameOrSign())) {
                  
                  // push invocation target to worklist
                  BBorInstInfo newInfo = infoItem.clone();
                  newInfo.target = new SimpleEntry<SSAInvokeInstruction, CGNode>(invokeInst, targetNodes[i]);
                  workList.add(newInfo);
                  
                  // output pushed invocation target name
                  MethodReference targetmr = targetIRs[i].getMethod().getReference();
                  String targetName = Utils.getClassTypeJavaStr(targetmr.getDeclaringClass().getName() + "." + targetmr.getName());
                  System.out.println("Pushed invocation target: " + targetName);
                  if (execOptions.isEnteringCallStack()) {
                    break; // only push the one matching the input call stack frame
                  }
                }
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
        if (execOptions.isEnteringCallStack()) {
          // determine if entering call stack is still correct
          if (callStack.getCurLineNo() == currLine) {
            if (inst instanceof SSAInvokeInstruction) {
              SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
              MethodReference mr = (ir == null) ? invokeInst.getDeclaredTarget() : ir.getMethod().getReference();

              // determine if entering call stack is still correct
              if (!isTheSameOrChildMethod(mr, callStack.getNextMethodNameOrSign())) {
                // skip this instruction
                String targetName = Utils.getClassTypeJavaStr(mr.getDeclaringClass().getName() + "." + mr.getName().toString());
                String msg = "Invocation target " + targetName + " doesn't match the call stack: " + 
                  callStack.getNextMethodNameOrSign() + " at " + callStack.getCurMethodNameAndLineNo();
                System.err.println(msg);
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
              " at " + callStack.getCurMethodNameAndLineNo();
            throw new InvalidStackTraceException(msg);
          }
          
          //FIXME: need checkcast
          if (inst != null) {
            // since we've already found the correct invocation target, we need to remove the rest
            workList.clear();
          }
        }
        
        if (inst != null) {
          // if it is at the inner most frame, start from the starting index
          if (!starting[0] || startingInst < 0 || (currInstIndex - (execOptions.inclStartingInst ? 1 : 0)) < startingInst) {
            // get precond for this instruction
            BBorInstInfo instInfo = lastInst ? infoItem : new BBorInstInfo(infoItem.currentBB, 
                infoItem.isSkipToBB, preCond, infoItem.postCond4BB, Formula.NORMAL_SUCCESSOR, 
                infoItem.sucessorBB, infoItem.sucessorInfo, methData, callSites, infoItem.workList, this);
            preCond = m_instHandler.handle(execOptions, cgNode, preCond, inst, 
                instInfo, callStack, curInvokeDepth);
          }
  
          // already passed the startLine, no need to limit the currLine anymore
          starting[0] &= (currLine >= startLine);
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
      isSame = mr.getSignature().equals(methodNameOrSign);
    }
    else {
      String declaringClass = mr.getDeclaringClass().getName().toString();
      
      // get invoking method name
      String invokingMethod = Utils.getClassTypeJavaStr(declaringClass) + "." + mr.getName().toString();
      // naive compare first, sometimes can avoid including subject jar in the classpath
      if (!invokingMethod.equals(methodNameOrSign)) {
        for (Class<?> c = Utils.findClass(declaringClass); c != null && !isSame; c = c.getSuperclass()) {
          isSame = (c.getName() + "." + mr.getName().toString()).equals(methodNameOrSign);
        }       
      }
      else {
        isSame = true;
      }
    }
    return isSame;
  }
  
  private boolean shouldCheckBranching(SSACFG cfg, int startingInst, int curInvokeDepth, 
      CallStack callStack, ExecutionOptions execOptions, BBorInstInfo infoItem) {
    return curInvokeDepth == 0 && callStack.getDepth() <= 1 && 
        execOptions.startingInst >= 0 && execOptions.startingInstBranchesTo != null && 
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
  
  private void pushChildrenBlocks(List<Object[]> bbPreconds, boolean areSkipToBBs, 
      BBorInstInfo currentInfo, MethodMetaData methData, int successorType, 
      Stack<BBorInstInfo> workList, int maxLoop, String valPrefix) {
    
    List<Object[]> visitedList    = new ArrayList<Object[]>();
    List<Object[]> notvisitedList = new ArrayList<Object[]>();
    for (Object[] bbPrecond : bbPreconds) {
      ISSABasicBlock basicBlock = (ISSABasicBlock) bbPrecond[0];
      
      // make sure we are not pushing the current node again. Sometimes, a 
      // monitorexit node can be a child of itself, making the search endless.
      if (basicBlock.getNumber() == currentInfo.currentBB.getNumber()) {
        continue;
      }
      
      // get all visited records
      Hashtable<ISSABasicBlock, Integer> visitedBB = ((Formula) bbPrecond[1]).getVisitedRecord();      
      Integer count = visitedBB.get(basicBlock);
      if (count != null && count < maxLoop) {
        visitedList.add(bbPrecond);
      }
      else if (count == null) {
        notvisitedList.add(bbPrecond);
      }
    }

    // sort them from large BB number to small BB number
    Collections.sort(visitedList, new Comparator<Object[]>(){ 
      public int compare(Object[] arg0, Object[] arg1) { 
        return ((ISSABasicBlock) arg1[0]).getNumber() - ((ISSABasicBlock) arg0[0]).getNumber();
      } 
    });
    Collections.sort(notvisitedList, new Comparator<Object[]>(){ 
      public int compare(Object[] arg0, Object[] arg1) { 
        return ((ISSABasicBlock) arg1[0]).getNumber() - ((ISSABasicBlock) arg0[0]).getNumber();
      } 
    });
    
    // push the visited ones into the beginning of the stack
    for (Object[] visited : visitedList) {
      workList.push(new BBorInstInfo((ISSABasicBlock) visited[0], areSkipToBBs, (Formula) visited[1], 
          (Formula) visited[1], successorType, currentInfo.currentBB, currentInfo, methData, valPrefix, null, this));
    }
    
    // push the non visited ones into the beginning of the stack
    for (Object[] notvisited : notvisitedList) {
      workList.push(new BBorInstInfo((ISSABasicBlock) notvisited[0], areSkipToBBs, (Formula) notvisited[1], 
          (Formula) notvisited[1], successorType, currentInfo.currentBB, currentInfo, methData, valPrefix, null, this));
    }
  }
  
  private Hashtable<ISSABasicBlock, Formula> decidePhis(BBorInstInfo instInfo, Formula postCond) {
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
    
    // create new preconds first, so we can keep the timeStamps earlier than phi Instance assignments
    Iterator<SSAPhiInstruction> phiInsts = instInfo.currentBB.iteratePhis();
    for (Iterator<SSAPhiInstruction> it = phiInsts; it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        for (int i = 0; i < predList.size() && i < phiInst.getNumberOfUses(); i++) {
          ISSABasicBlock pred = predList.get(i);
          Formula newPreCond = newPreConds.get(pred);
          if (newPreCond == null) {
            newPreCond = postCond.clone();
            newPreConds.put(pred, newPreCond);
          }
        }
      }
    }
    
    // then assign phi for each phi instruction
    phiInsts = instInfo.currentBB.iteratePhis();
    for (Iterator<SSAPhiInstruction> it = phiInsts; it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        for (int i = 0; i < predList.size() && i < phiInst.getNumberOfUses(); i++) {
          ISSABasicBlock pred = predList.get(i);
          m_instHandler.handle_phi(newPreConds.get(pred), phiInst, instInfo, phiInst.getUse(i), pred);
          newPreConds.get(pred).addToTraversedPath(phiInst, instInfo.methData, instInfo.callSites, phiInst.getUse(i));
        }
      }
    }

    return newPreConds;
  }
  
  private boolean phiDefsUseful(BBorInstInfo instInfo, Formula postCond) {
    boolean useful = false;
    
    Iterator<SSAPhiInstruction> phiInsts = instInfo.currentBB.iteratePhis();
    Hashtable<String, Reference> methodRefs = postCond.getRefMap().get(instInfo.callSites);
    for (Iterator<SSAPhiInstruction> it = phiInsts; it.hasNext() && !useful;) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        String def = AbstractHandler.getSymbol(phiInst.getDef(), 
            instInfo.methData, instInfo.callSites, postCond.getDefMap());
        useful = methodRefs != null && methodRefs.containsKey(def);
      }
    }
    return useful;
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
      foundBB = cfg.exit();
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
    StringBuilder computePath = new StringBuilder();
    computePath.append(0);
    BBorInstInfo currentBB = entryNode;
    while (currentBB.sucessorInfo != null) {
      computePath.append(" >- ").append(
          new StringBuilder(String.valueOf(currentBB.sucessorBB.getNumber())).reverse());
      currentBB = currentBB.sucessorInfo;
    }
    System.out.print(computePath.reverse());
    System.out.println(" -> SMT Check");
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
      ExecutionOptions execOptions = new ExecutionOptions(callStack, false);
      execOptions.maxDispatchTargets  = 2;
      execOptions.maxRetrieve         = 10;
      execOptions.maxSmtCheck         = 5000;
      execOptions.maxInvokeDepth      = 1;
      execOptions.maxLoop             = 7;
      execOptions.skipUselessBranches = false;
      execOptions.skipUselessMethods  = false;
      
      executor.compute(execOptions, null);
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
  private HeuristicBacktrack        m_heuristicBacktrack;

  private Hashtable<BBorInstInfo, SSAInstruction> m_callStackInvokes;
}
