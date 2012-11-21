package hk.ust.cse.Prevision.VirtualMachine.Executor;

import hk.ust.cse.Prevision.InstructionHandlers.AbstractBackwardHandler;
import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteBackwardHandler;
import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.Optimization.DefAnalyzerWrapper;
import hk.ust.cse.Prevision.Optimization.HeuristicBacktrack;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import javax.naming.TimeLimitExceededException;

import com.ibm.wala.cfg.Util;
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

public class BackwardExecutor extends AbstractExecutor {

  public BackwardExecutor(String appJar, String pseudoImplJarFile, AbstractHandler instHandler, SMTChecker smtChecker) throws Exception {
    
    super(appJar, pseudoImplJarFile, instHandler, smtChecker, false);

    // set def analyzer
    m_defAnalyzer = new DefAnalyzerWrapper(m_walaAnalyzer, 10);
  }

  /**
   * @param cgNode: only useful when we use 'compDispatchTargets' function
   */
  public ExecutionResult computeRec(ExecutionOptions execOptions, CGNode cgNode, IR ir, int startLine, 
      int startingInst, boolean inclLine, CallStack callStack, int curInvokeDepth, String callSites, 
      Stack<BBorInstInfo> workList, Formula formula) throws InvalidStackTraceException, TimeLimitExceededException {
    
    assert(ir != null);
    
    // start timing
    long start = System.currentTimeMillis();

    // initialize MethodMetaData, save it if it's not inside an 
    // invocation and it's at the outermost call
    MethodMetaData methMetaData = new MethodMetaData(ir);
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_methMetaData = methMetaData;

      // clear m_execResult
      m_execResult = null;

      // for heuristic backtracking
      m_heuristicBacktrack = new HeuristicBacktrack(m_smtChecker);
      
      // set global start time
      m_globalStartTime = start;
      
      // save the initial preconditions that have been computed
      m_computedInitPrep = new HashSet<String>();
    }
    
    if (workList.empty()) { // new worklist, simply start from startLine
      ISSABasicBlock startFromBB = findBasicBlock(methMetaData, startLine, startingInst, m_forward);
      if (startFromBB == null) {
        String msg = "Failed to find a valid basic block at line: " + startLine + " and instruction index: " + startingInst;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }

      // create an initial Formula if formula is null
      formula = formula == null ? new Formula(m_forward) : formula; // TRUE formula

      // push in the first block
      workList.push(new BBorInstInfo(startFromBB, false, false, formula, formula, 
          Formula.NORMAL_SUCCESSOR, null, null, methMetaData, callSites, null, this));
    }

    // we don't want to return a null execResult
    boolean timeExceeded = false;
    ExecutionResult execResult = new ExecutionResult();
    try {
      // start depth first search for this method
      computeMethod(execOptions, cgNode, methMetaData, workList, 
          startLine, startingInst, inclLine, callStack, curInvokeDepth, callSites, execResult);
    } catch (TimeLimitExceededException e) {
      timeExceeded = true;
    }
    
    // save result if it's not inside an invocation and it's at the outermost call
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
    
    if (timeExceeded) {
      throw new TimeLimitExceededException();
    }

    return execResult;
  }
 
  private ExecutionResult computeMethod(ExecutionOptions execOptions, CGNode cgNode, MethodMetaData methData, 
      Stack<BBorInstInfo> workList, int startLine, int startingInst, boolean inclLine, CallStack callStack, 
      int curInvokeDepth, String callSites, ExecutionResult execResult) throws InvalidStackTraceException, TimeLimitExceededException {

    // start timing
    long start = System.currentTimeMillis();

    // counters
    int smtChecked   = 0;
    int satRetrieved = 0;
    
    // get cfg for later use
    SSACFG cfg = methData.getcfg();
    
    // if startLine <= 0, we start from exit block
    boolean[] starting = new boolean[] {startLine > 0};
    
    // if callStack's depth == 1, finished entering call stack
    boolean enteredInnerMost = false;
    if (execOptions.isEnteringCallStack() && callStack.getDepth() <= 1) {
      execOptions.finishedEnteringCallStack();
      enteredInnerMost = true;
    }

    // output method name
    System.out.println("Computing method: " + cfg.getMethod().getSignature());
    
    // continue backtrack if necessary
    if (execOptions.heuristicBacktrack && m_heuristicBacktrack.isContinueInMethod()) {
      m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
    }
    
    // start depth first search
    while (!workList.empty()) {
      // if exceed time limit, break
      if (System.currentTimeMillis() - m_globalStartTime > execOptions.maxTimeAllow) {
        throw new TimeLimitExceededException();
      }
      
      // continue backtrack if necessary
      if (execOptions.heuristicBacktrack && m_heuristicBacktrack.isBacktracking()) {
        m_heuristicBacktrack.backtrack(workList, curInvokeDepth, execOptions.maxInvokeDepth);
        if (workList.empty()) {
          continue;
        }
      }

      // next block to compute
      BBorInstInfo infoItem = workList.pop();

      // if just entered inner most, this bb is set as startingBB
      infoItem.startingBB = enteredInnerMost ? true : infoItem.startingBB;
      enteredInnerMost = false; // only used for the first bb
      
      // since there may be multiple startingBB, but when the second time we descend to here (bottom) 
      // from upper frames, the startLine already becomes -1. So, we need to set startLine back to the 
      // original value such that computeBB can find the correct line number to start
      if (infoItem.startingBB && startLine < 0) {
        startLine = callStack.getInnerMostCallStack().getCurLineNo();
        inclLine = execOptions.inclInnerMostLine;
      }
      starting[0] = starting[0] || (infoItem.startingBB && startLine > 0);
      
      int instIndex = infoItem.currentBB.getLastInstructionIndex();
      String lineNo = (instIndex >= 0) ? " @ line " + methData.getLineNumber(instIndex) : "";
      System.out.println("Computing BB" + infoItem.currentBB.getNumber() + lineNo);
      
      // compute for this BB
      int origWorklistSize = workList.size();
      Formula precond = null;
      List<BBorInstInfo> moreInfoItems = new ArrayList<BBorInstInfo>();
      try {
        precond = computeBB(execOptions, cgNode, methData, infoItem, startLine, startingInst, 
            inclLine, callStack, starting, curInvokeDepth, callSites, workList, moreInfoItems);
      } catch (InvalidStackTraceException e) {
        if (workList.size() == 0) {
          // if we cannot enter call stack correctly, no need to continue
          System.err.println(e.getMessage());
          throw e;
        }
      }
      
      if (precond == null || // an inner contradiction has been detected
          workList.size() > origWorklistSize) { // if new invocation targets have been pushed into stack 
                                                // due to target dispatch, skip the current instruction
        continue;
      }
      
      // we have computed more initial preconditions
      for (BBorInstInfo moreInfoItem : moreInfoItems) {
        workList.add(moreInfoItem);
      }

      // marked as visited
      precond.setVisitedRecord(precond.getVisitedRecord(), infoItem, m_forward);

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
        Collection<ISSABasicBlock> normPredBB = cfg.getNormalPredecessors(infoItem.currentBB);
        Collection<ISSABasicBlock> excpPredBB = cfg.getExceptionalPredecessors(infoItem.currentBB);
        
        // on the fly checks
        Hashtable<String, Reference> methodRefs = precond.getRefMap().get(infoItem.callSites);
        if (execOptions.checkOnTheFly && normPredBB.size() > 1) {
          if (!infoItem.currentBB.isExitBlock() || methodRefs == null || !methodRefs.containsKey("RET")) {
            SMT_RESULT smtResult = m_smtChecker.smtCheck(
                precond, false, false, false, false, execOptions.heuristicBacktrack, true);
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
        boolean startingToBranches = false;
        if (shouldCheckBranching(cfg, startingInst, curInvokeDepth, callStack, execOptions, infoItem)) {
          
          // retain only the branches in startingInstBranchesTo list
          retainOnlyBranches(cfg, execOptions.startingInstBranchesTo, normPredBB, excpPredBB);
          startingToBranches = true;
        }
        
//        // only traverse exceptional paths when we come from a catch block
//        if (isCaught(precond)) {
//          // iterate all exceptional predecessors
//          pushChildrenBlocks(excpPredBB, false, infoItem, precond, methData,
//              Formula.EXCEPTIONAL_SUCCESSOR, dfsStack, execOptions.maxLoop, valPrefix);            
//        }
        
        // try to skip method
        if (execOptions.skipUselessMethods && !startingToBranches && infoItem.currentBB.isExitBlock() && 
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
            phiedPreCond.setVisitedRecord(phiedPreCond.getVisitedRecord(), null, m_forward);
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
          if (execOptions.skipUselessBranches && !startingToBranches && !starting[0] && !phiDefsUseful) {
            ISSABasicBlock skipToCondBB = m_defAnalyzer.findSkipToBasicBlocks(methData.getIR(), 
                infoItem.currentBB, pred, phiedPreCond, infoItem.callSites);
            if (skipToCondBB != null) {
              if (!phiDefsUseful) {
                Collection<ISSABasicBlock> skipToPredBBs = cfg.getNormalPredecessors(skipToCondBB);
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
                BBorInstInfo newInfoItem = new BBorInstInfo(currentBB, infoItem.startingBB, 
                    infoItem.skipToBB, infoItem.formula, infoItem.formula4BB, 
                    infoItem.controlType, infoItem.previousBB, infoItem.previousInfo, 
                    infoItem.methData, infoItem.callSites, infoItem.workList, infoItem.executor);
                pushChildrenBlocks(condBBToPush, false, false, newInfoItem, methData,
                    Formula.NORMAL_SUCCESSOR, workList, execOptions.maxLoop, callSites, starting[0]);
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
        pushChildrenBlocks(bbPreConds, false, false, infoItem, methData,
            Formula.NORMAL_SUCCESSOR, workList, execOptions.maxLoop, callSites, starting[0]);
      }
      else if (execOptions.isEnteringCallStack()) {
        // at method entry, cannot find the proper invocation to enter call stack
        String msg = "Failed to enter call stack: " + callStack.getNextMethodNameOrSign() + 
          " at " + callStack.getCurMethodNameOrSign() + ":" + callStack.getCurLineNo();
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }
      else if (curInvokeDepth != 0 || !callStack.isOutMostCall()) {          
        // we only do smtCheck() if it's not inside an invocation and it's at the outermost invocation
        execResult.addSatisfiable(precond);
        break; // return to InstHandler
      }
      else { // for entry block, no need to go further
        System.out.println("Performing " + (smtChecked + 1) + "th SMT Check...");
        
        // output propagation path
        printPropagationPath(infoItem);
        
        // use SMT Solver to check precond and obtain a model
        SMT_RESULT smtResult = m_smtChecker.smtCheck(
            precond, false, true, true, true, execOptions.heuristicBacktrack, true);
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

    // end timing
    long end = System.currentTimeMillis();
    System.out.println("Method " + cfg.getMethod().getName() + " computation elapsed: " + (end - start) + "ms!\n");

    return execResult;
  }

  private Formula computeBB(ExecutionOptions execOptions, CGNode cgNode, MethodMetaData methData, BBorInstInfo infoItem, 
      int startLine, int startingInst, boolean inclLine, CallStack callStack, boolean[] starting, int curInvokeDepth, 
      String callSites, Stack<BBorInstInfo> workList, List<BBorInstInfo> moreInfoItems) throws InvalidStackTraceException {
    
    Formula preCond = infoItem.formula;
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
    int currInstIndex  = infoItem.currentBB.getLastInstructionIndex() - (infoItem.skipToBB ? 1 : 0);
    int firstInstIndex = infoItem.currentBB.getFirstInstructionIndex();
    while (currInstIndex >= 0 && currInstIndex >= firstInstIndex && preCond != null) {
      // get instruction
      SSAInstruction inst = allInsts[currInstIndex];
      int currLine = methData.getLineNumber(currInstIndex);

      if (!starting[0] || (currLine - (inclLine ? 1 : 0)) < startLine) {
        IR ir = null;
        
        if (inst instanceof SSAInvokeInstruction && (infoItem.target == null || !infoItem.target[0].equals(inst))) {
          SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
          MethodReference mr = invokeInst.getDeclaredTarget();

          if (!execOptions.isEnteringCallStack() && execOptions.maxDispatchTargets < Integer.MAX_VALUE && 
              mr.getDeclaringClass().getName().toString().equals("Ljava/lang/Object")) {
            // since there are so many possible implementations for Object's virtual methods such
            // as Object.hashCode(), Object.clone(), Object.equals(), etc, there is little chance 
            // that we could get the correct one with a limited maxDispatchTargets. And it can 
            // cause an unsat due to caller type checkcast if we do such dispatch. Thus, we simply 
            // ignore these Object methods and hope for pseudo implementations.
          }
          else { // find the potential targets of this invocation
            IR[] targetIRs       = null;
            CGNode[] targetNodes = null;
            
            // force to find all
            CGNode caller           = execOptions.isEnteringCallStack() ? null /* force to find all */ : cgNode;
            int targetsToGet        = execOptions.isEnteringCallStack() ? Integer.MAX_VALUE : execOptions.maxDispatchTargets;
            boolean useSubClassHack = !execOptions.isEnteringCallStack();
            
            Object[] ret = findInvokeTargets(m_walaAnalyzer, caller, invokeInst.getCallSite(), mr, targetsToGet, useSubClassHack);
            targetIRs   = (IR[]) ret[0];
            targetNodes = (CGNode[]) ret[1];
            
            // if found new targets, add them
            if (targetIRs != null && targetIRs.length > 0 && targetIRs[0] != null) {
              IMethod firstTarget = targetIRs[0].getMethod();
              String nextFrameSig = callStack.getNextMethodNameOrSign();
              if (!firstTarget.getSignature().equals(mr.getSignature()) /* new target */) {

                // add each target
                System.out.println("Multiple invocation targets: " + (targetIRs.length) + " target(s)!");
                for (int i = targetNodes.length - 1; i >= 0; i--) {
                  if (!execOptions.isEnteringCallStack() || 
                      sameOrChildMethod(targetIRs[i].getMethod().getReference(), nextFrameSig)) {
                    pushInvocationTarget(workList, infoItem, invokeInst, targetIRs[i], targetNodes[i]);
                    if (execOptions.isEnteringCallStack()) {
                      break; // only push the one matching the next call stack frame
                    }
                  }
                }
                return infoItem.formula;
              }
              else if (execOptions.isEnteringCallStack()) {
                // it is a concrete method, but not the next frame method
                if (!sameOrChildMethod(firstTarget.getReference(), nextFrameSig)) {
                  IR[] overrides = WalaUtils.getOverrides(m_walaAnalyzer, mr, Integer.MAX_VALUE);

                  System.out.println("Multiple invocation targets: " + (targetIRs.length) + " target(s)!");                  
                  for (IR override : overrides) {
                    if (sameOrChildMethod(override.getMethod().getReference(), nextFrameSig)) {
                      pushInvocationTarget(workList, infoItem, invokeInst, override, null);
                      break; // only push the one matching the next call stack frame
                    }
                  }
                  return infoItem.formula;
                }
                else {
                  infoItem.target = new Object[] {invokeInst, targetIRs[0], targetNodes[0]};
                }
              }
              else {
                infoItem.target = new Object[] {invokeInst, targetIRs[0], targetNodes[0]};
              }
            }
            else {
              infoItem.target = new Object[] {invokeInst, null, null};
            }
          }
        }
        else if (inst instanceof SSAInvokeInstruction && infoItem.target != null && infoItem.target[1] != null) {
          ir = (IR) infoItem.target[1]; // going into IR which is decided
        }
        
        // determine if entering call stack is still correct
        if (execOptions.isEnteringCallStack()) {
          String currFrame = callStack.getCurMethodNameAndLineNo();
          String nextFrame = callStack.getNextMethodNameAndLineNo();
          if (callStack.getCurLineNo() == currLine) {
            if (inst instanceof SSAInvokeInstruction) {
              SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
              MethodReference mr = (ir == null) ? invokeInst.getDeclaredTarget() : ir.getMethod().getReference();

              // determine if entering call stack is still correct
              if (!sameOrChildMethod(mr, callStack.getNextMethodNameOrSign())) {
                // skip this instruction
                String targetName = Utils.getClassTypeJavaStr(mr.getDeclaringClass().getName() + "." + mr.getName().toString());
                String msg = "Invocation target " + targetName + " doesn't match the next frame: " + nextFrame + " at " + currFrame;
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
            String msg = "Failed to enter next frame: " + nextFrame + " at " + currFrame;
            throw new InvalidStackTraceException(msg);
          }

          if (inst != null) {
            // since we've already found the correct invocation target, we need to remove the rest
            workList.clear();
          }
        }
        
        if (inst != null) {
          // starting finished, compute the crash precondition
          if (infoItem.startingBB && starting[0] && currLine < startLine) {
            // already passed the startLine, no need to limit the currLine anymore
            starting[0] = false;
            
            // create the preconditions according to crash type
            List<Formula> initFormulas = prepInitFormulas(preCond, methData, execOptions, infoItem, moreInfoItems);
            // use this preCond to continue computation
            preCond = initFormulas.size() > 0 ? initFormulas.get(0) : null;
          }
          
          // if it is at the inner most frame, start from the starting index
          if (!starting[0] || startingInst < 0 || (currInstIndex - (execOptions.inclStartingInst ? 1 : 0)) < startingInst) {
            // get precond for this instruction
            BBorInstInfo instInfo = lastInst ? infoItem : new BBorInstInfo(infoItem.currentBB, 
                infoItem.startingBB, infoItem.skipToBB, preCond, infoItem.formula4BB, Formula.NORMAL_SUCCESSOR, 
                infoItem.previousBB, infoItem.previousInfo, methData, callSites, infoItem.workList, this);
            preCond = m_instHandler.handle(execOptions, cgNode, preCond, inst, 
                instInfo, callStack, curInvokeDepth);
          }
        }
      }

      // get previous instruction
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
      
      // starting finished, compute the crash precondition
      if (infoItem.startingBB && starting[0]) {
        // already passed the startLine, no need to limit the currLine anymore
        starting[0] = false;
        
        // create the preconditions according to crash type
        List<Formula> initFormulas = prepInitFormulas(preCond, methData, execOptions, infoItem, moreInfoItems);
        // use this preCond to continue computation
        preCond = initFormulas.size() > 0 ? initFormulas.get(0) : null;
      }
    }

    return preCond;
  }
  
  private void pushInvocationTarget(Stack<BBorInstInfo> workList, BBorInstInfo oriInfoItem, 
      SSAInstruction invokeInst, IR targetIR, CGNode targetNode) {
    
    // push invocation target to worklist
    BBorInstInfo newInfo = oriInfoItem.clone();
    newInfo.target = new Object[] {invokeInst, targetIR, targetNode};
    workList.add(newInfo);
    
    // print pushed invocation target name
    MethodReference targetReference = targetIR.getMethod().getReference();
    System.out.println("Pushed invocation target: " + Utils.getClassTypeJavaStr(
        targetReference.getDeclaringClass().getName() + "." + targetReference.getName()));
  }
  
  private List<Formula> prepInitFormulas(Formula preCond, MethodMetaData methData, ExecutionOptions execOptions, 
      BBorInstInfo infoItem, List<BBorInstInfo> moreInfoItems) {
    
    List<Formula> initFormulas = PrepInitFormula.prepInitFormula(preCond, methData, execOptions);
    
    // remove those identical to the computed ones
    for (int i = 0; i < initFormulas.size(); i++) {
      String conditionListStr = initFormulas.get(i).getConditionList().toString();
      if (m_computedInitPrep.contains(conditionListStr)) {
        initFormulas.remove(i--);
      }
      else {
        m_computedInitPrep.add(conditionListStr);
      }
    }
    
    if (initFormulas.size() > 0) {
      infoItem.formula    = initFormulas.get(0);
      infoItem.formula4BB = initFormulas.get(0);
      infoItem.startingBB = false;
      
      // if may be more than one preconditions, record them to add to worklist
      for (int i = 1, size = initFormulas.size(); i < size; i++) {
        BBorInstInfo infoItem2 = infoItem.clone();
        infoItem2.formula    = initFormulas.get(i);
        infoItem2.formula4BB = initFormulas.get(i);
        infoItem2.startingBB = false;
        moreInfoItems.add(infoItem2); // therefore, multiple startingBB is possible
      }
    }
    return initFormulas;
  }
  
  private boolean shouldCheckBranching(SSACFG cfg, int startingInst, int curInvokeDepth, 
      CallStack callStack, ExecutionOptions execOptions, BBorInstInfo infoItem) {
    return curInvokeDepth == 0 && callStack.getDepth() <= 1 && 
        execOptions.startingInst >= 0 && execOptions.startingInstBranchesTo != null && 
        infoItem.previousBB == null && 
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
  
  private Hashtable<ISSABasicBlock, Formula> decidePhis(BBorInstInfo instInfo, Formula postCond) {
    Hashtable<ISSABasicBlock, Formula> newPreConds = new Hashtable<ISSABasicBlock, Formula>();

    // get predecessors of the current block
    List<ISSABasicBlock> predList = new ArrayList<ISSABasicBlock>();
    Iterator<ISSABasicBlock> predBBs = instInfo.methData.getcfg().getPredNodes(instInfo.currentBB);
    while (predBBs.hasNext()) {
      predList.add(predBBs.next());
    }
    
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

          // decide phi according to predecessor
          int index = Util.whichPred(instInfo.methData.getcfg(), pred, instInfo.currentBB);
          
          // handle the phi instruction, use the 'index' phi variable
          m_instHandler.handle_phi(newPreConds.get(pred), phiInst, instInfo, phiInst.getUse(index), pred);
          newPreConds.get(pred).addToTraversedPath(phiInst, instInfo.methData, instInfo.callSites, phiInst.getUse(index));
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
        String def = ((AbstractBackwardHandler) m_instHandler).getSymbol(phiInst.getDef(), 
            instInfo.methData, instInfo.callSites, postCond.getDefMap());
        useful = methodRefs != null && methodRefs.containsKey(def);
      }
    }
    return useful;
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
  
  public static void main(String args[]) {
    try {
      AbstractHandler instHandler = new CompleteBackwardHandler();
      SMTChecker smtChecker = new SMTChecker(SMTChecker.SOLVERS.YICES);
      BackwardExecutor executor = new BackwardExecutor(args[0]/*jar file path*/, null, instHandler, smtChecker);
      Utils.loadJarFile(args[0]);
      
      // read stack frames
      CallStack callStack = new CallStack(true);
      for (int i = 1; i < args.length; i++) {
        String methName = args[i] /*method name*/;
        int lineNo      = Integer.parseInt(args[++i]) /*line number*/;
        
        callStack.addStackTrace(methName, lineNo);
      }
      
      // set options
      ExecutionOptions execOptions = new ExecutionOptions(callStack);
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

  private final DefAnalyzerWrapper  m_defAnalyzer;
  private HashSet<String>           m_computedInitPrep;
  private HeuristicBacktrack        m_heuristicBacktrack;
}
