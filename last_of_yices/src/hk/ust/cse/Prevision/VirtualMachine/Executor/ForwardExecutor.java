package hk.ust.cse.Prevision.VirtualMachine.Executor;

import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteForwardHandler;
import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import javax.naming.TimeLimitExceededException;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.types.MethodReference;

public class ForwardExecutor extends AbstractExecutor {

  public ForwardExecutor(String appJar, String pseudoImplJarFile, AbstractHandler instHandler, SMTChecker smtChecker) throws Exception {
    super(appJar, pseudoImplJarFile, instHandler, smtChecker, true);
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
    // invocation and it's at the out most call
    MethodMetaData methMetaData = new MethodMetaData(ir);
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_methMetaData = methMetaData;

      // clear m_execResult
      m_execResult = null;

      // set global start time
      m_globalStartTime = start;
    }
    
    if (workList.empty()) { // new worklist, simply start from entry block
      // create an initial Formula if formula is null
      formula = formula == null ? new Formula(m_forward) : formula; // TRUE formula

      // push in the first block
      workList.push(new BBorInstInfo(methMetaData.getcfg().entry(), true, false, formula, formula, 
          Formula.NORMAL_SUCCESSOR, null, null, methMetaData, callSites, null, this));
    }

    // start depth first search for this method
    ExecutionResult execResult = computeMethod(
        execOptions, cgNode, methMetaData, workList, callStack, curInvokeDepth, callSites);
    
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
  
  private ExecutionResult computeMethod(ExecutionOptions execOptions, CGNode cgNode, 
      MethodMetaData methData, Stack<BBorInstInfo> workList, CallStack callStack, int curInvokeDepth, 
      String callSites) throws InvalidStackTraceException, TimeLimitExceededException {

    // start timing
    long start = System.currentTimeMillis();

    // we don't want to return a null execResult
    ExecutionResult execResult = new ExecutionResult();
    int smtChecked   = 0;
    int satRetrieved = 0;
    
    // get cfg for later use
    SSACFG cfg = methData.getcfg();
    
    // if callStack's depth == 1, finished entering call stack
    if (execOptions.isEnteringCallStack() && callStack.getDepth() <= 1) {
      execOptions.finishedEnteringCallStack();
      
      //XXX can initialize precondition here
    }

    // output method name
    System.out.println("Computing method: " + cfg.getMethod().getSignature());

    // start depth first search
    while (!workList.empty()) {
      // if exceed time limit, break
      if (System.currentTimeMillis() - m_globalStartTime > execOptions.maxTimeAllow) {
        throw new TimeLimitExceededException();
      }
      
      // next block to compute
      BBorInstInfo infoItem = workList.pop();

      //if (!infoItem.postCond.isContradicted()) {
      if (true) {
        int instIndex = infoItem.currentBB.getFirstInstructionIndex();
        String lineNo = (instIndex >= 0) ? " @ line " + methData.getLineNumber(instIndex) : "";
        System.out.println("Computing BB" + infoItem.currentBB.getNumber() + lineNo);
        
        // compute for this BB
        int oriStackSize = workList.size();
        Formula postCond = null;
        try {
          postCond = computeBB(execOptions, cgNode, methData, infoItem, callStack, curInvokeDepth, callSites, workList);
        } catch (InvalidStackTraceException e) {
          if (workList.size() == 0) {
            // if we cannot enter call stack correctly, no need to continue
            System.err.println(e.getMessage());
            throw e;
          }
        }
        
        if (postCond == null || // an inner contradiction has been detected
            workList.size() > oriStackSize) { // if new invocation targets have been pushed into stack 
                                              // due to target dispatch, skip the current instruction
          continue; 
        }

        // marked as visited
        postCond.setVisitedRecord(postCond.getVisitedRecord(), infoItem, m_forward);

        // re-push if can't pop: a new workList created for this BB has not yet finished
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
        
        if (!infoItem.currentBB.isExitBlock()) {
          Collection<ISSABasicBlock> normSuccBB = cfg.getNormalSuccessors(infoItem.currentBB);
//          Collection<ISSABasicBlock> excpSuccBB = cfg.getExceptionalSuccessors(infoItem.currentBB);
          
          // on the fly checks
          if (execOptions.checkOnTheFly && normSuccBB.size() > 1) {
            SMT_RESULT smtResult = m_smtChecker.smtCheck(
                postCond, false, false, false, true, execOptions.heuristicBacktrack, true);
            postCond.setSolverResult(m_smtChecker);
            
            // trigger callbacks
            for (Object[] m : execOptions.getAfterOnTheFlyCheckCallback()) {
              try {
                ((Method) m[0]).invoke(m[1], postCond);
              } catch (Exception e) {e.printStackTrace();}
            }
            
            if (smtResult == Formula.SMT_RESULT.UNSAT) {
              System.out.println("Inner contradiction developed, discard block.");
              continue;
            }
          }
          
          // handle previous conditional branches according to successors to take
          List<Object[]> bbPostConds = handleBranches(normSuccBB, infoItem, postCond);
          
//          // currently, only accepts exception flow directly from throw instruction to exit
//          if (infoItem.currentBB.getLastInstructionIndex() >= 0 && 
//              infoItem.currentBB.getLastInstruction() instanceof SSAThrowInstruction) {
//            // add the exceptional successors, should have only one that leads to exit block
//            for (ISSABasicBlock successor : excpSuccBB) {
//              bbPostConds.add(new Object[] {successor, postCond});
//            }
//          }
          
//          // if have specified the execOptions.startingInstBranchesTo list, 
//          // only take the specified branches at the starting basic block
//          if (shouldCheckBranching(cfg, startingInst, curInvokeDepth, callStack, execOptions, infoItem)) {
//            
//            // retain only the branches in startingInstBranchesTo list
//            retainOnlyBranches(cfg, execOptions.startingInstBranchesTo, normPredBB, excpPredBB);
//          }
          
//          // only traverse exceptional paths when we come from a catch block
//          if (isCaught(precond)) {
//            // iterate all exceptional predecessors
//            pushChildrenBlocks(excpPredBB, false, infoItem, precond, methData,
//                Formula.EXCEPTIONAL_SUCCESSOR, dfsStack, execOptions.maxLoop, valPrefix);            
//          }
          
          // iterate all normal successors
          pushChildrenBlocks(bbPostConds, false, true, infoItem, methData,
              Formula.NORMAL_SUCCESSOR, workList, execOptions.maxLoop, callSites, false);
          // iterate all exception successors
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
          execResult.addSatisfiable(postCond);
          break; // return to InstHandler
        }
        else { // for entry block, no need to go further
          System.out.println("Performing " + (smtChecked + 1) + "th SMT Check...");
          
          // output propagation path
          printPropagationPath(infoItem);
          
          // use SMT Solver to check postCond and obtain a model
          SMT_RESULT smtResult = m_smtChecker.smtCheck(
              postCond, false, true, true, true, execOptions.heuristicBacktrack, true);
          postCond.setSolverResult(m_smtChecker);
          
          // trigger callbacks
          for (Object[] m : execOptions.getAfterSmtCheckCallbacks()) {
            try {
              ((Method) m[0]).invoke(m[1], postCond);
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
            execResult.addSatisfiable(postCond);
            
            // limit maximum number of satisfiable preconditions to retrieve
            if (execOptions.maxRetrieve > 0 && ++satRetrieved >= execOptions.maxRetrieve) {
              execResult.setReachMaximum(true);
              System.out.println("Reached Maximum Retrieve Limit!");
              canBreak = true;
            }
          }
          else {
            System.out.println("SMT Check failed!\n");
            
            // memory consumption may grow overtime as not-sat formula added
            if (execOptions.saveNotSatResults) {
              // clear non-solver data in the not satisfiable formula object
              postCond.clearNonSolverData();
              
              // save the not satisfiable precondition
              execResult.addNotSatisfiable(postCond);
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

  private Formula computeBB(ExecutionOptions execOptions, CGNode cgNode, MethodMetaData methData, BBorInstInfo infoItem, 
      CallStack callStack, int curInvokeDepth, String callSites, Stack<BBorInstInfo> workList) throws InvalidStackTraceException {
    
    Formula postCond = infoItem.formula;
    SSAInstruction[] allInsts = methData.getcfg().getInstructions();

    // handle phi instructions first if any
    for (Iterator<SSAPhiInstruction> it = infoItem.currentBB.iteratePhis(); it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        postCond = m_instHandler.handle(execOptions, cgNode, postCond, phiInst, infoItem, callStack, curInvokeDepth);
      }
    }
    
    // handle normal instructions
    boolean firstInst = true;
    int currInstIndex = infoItem.currentBB.getFirstInstructionIndex();
    int lastInstIndex = infoItem.currentBB.getLastInstructionIndex();
    while (currInstIndex >= 0 && currInstIndex <= lastInstIndex && postCond != null) {
      // get instruction
      SSAInstruction inst = allInsts[currInstIndex];

      // find dispatch targets
      if (inst instanceof SSAInvokeInstruction && (infoItem.target == null || !infoItem.target[0].equals(inst))) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
        MethodReference mr = invokeInst.getDeclaredTarget();
        
        // find the potential targets of this invocation
        IR[] targetIRs       = null;
        CGNode[] targetNodes = null;
        if (cgNode != null) {
          // use the default call graph builder to find invocation targets
          SimpleEntry<IR[], CGNode[]> ret = WalaUtils.findInvocationTargets(
              m_walaAnalyzer, cgNode, invokeInst.getCallSite(), execOptions.maxDispatchTargets);
          targetIRs   = ret.getKey();
          targetNodes = ret.getValue();
        }
        else {
          targetIRs   = WalaUtils.getImplementations(m_walaAnalyzer, mr, execOptions.maxDispatchTargets);
          targetNodes = new CGNode[targetIRs.length]; // all null
        }
      
        // if found new targets, add them
        if (targetIRs.length > 0 && targetIRs[0] != null && 
           !targetIRs[0].getMethod().getSignature().equals(mr.getSignature())) {
          System.out.println("Multiple invocation targets discovered: " + (targetIRs.length) + " target(s)!");
          for (int i = 0; i < targetNodes.length; i++) {
            // push invocation target to worklist
            BBorInstInfo newInfo = infoItem.clone();
            newInfo.target = new Object[] {invokeInst, targetIRs[i], targetNodes[i]};
            workList.add(newInfo);
            
            // print pushed invocation target name
            MethodReference targetmr = targetIRs[i].getMethod().getReference();
            String targetName = Utils.getClassTypeJavaStr(targetmr.getDeclaringClass().getName() + "." + targetmr.getName());
            System.out.println("Pushed invocation target: " + targetName);
          }
          return infoItem.formula;
        }
        else if (targetIRs.length > 0) {
          infoItem.target = new Object[] {invokeInst, targetIRs[0], targetNodes[0]};
        }
        else {
          infoItem.target = new Object[] {invokeInst, null, null};
        }
      }
      
      if (inst != null) {
        // get postcond for this instruction
        BBorInstInfo instInfo = firstInst ? infoItem : new BBorInstInfo(infoItem.currentBB, 
            infoItem.startingBB, infoItem.skipToBB, postCond, infoItem.formula4BB, Formula.NORMAL_SUCCESSOR, 
            infoItem.previousBB, infoItem.previousInfo, methData, callSites, infoItem.workList, this);
        postCond = m_instHandler.handle(execOptions, cgNode, postCond, inst, 
            instInfo, callStack, curInvokeDepth);
      }

      // get next instruction
      currInstIndex++;
      firstInst = false;
    }
    
    // handle pi instructions first if any
    for (Iterator<SSAPiInstruction> it = infoItem.currentBB.iteratePis(); it.hasNext();) {
      SSAPiInstruction piInst = (SSAPiInstruction) it.next();
      if (piInst != null) {
        postCond = m_instHandler.handle(execOptions, cgNode, postCond, piInst, 
            infoItem, callStack, curInvokeDepth);
      }
    }
    
    // handle catch instructions at the entry of a catch block
    if (infoItem.currentBB.isCatchBlock()) {
      postCond = m_instHandler.handle_catch(postCond, null, infoItem);
    }
    
    // some initialization at the entry block
    if (infoItem.currentBB.isEntryBlock()) {
      postCond = m_instHandler.handle_entryblock(postCond, null, infoItem);
    }
    
    // some wrap up at the exit block
    if (infoItem.currentBB.isExitBlock()) {
      postCond = m_instHandler.handle_exitblock(postCond, null, infoItem);
    }      

    return postCond;
  }
  
  private List<Object[]> handleBranches(Collection<ISSABasicBlock> normSuccBB, BBorInstInfo infoItem, Formula postCond) {
    List<Object[]> bbPostConds = new ArrayList<Object[]>();
    
    for (ISSABasicBlock successor : normSuccBB) {
      int lastIndex = infoItem.currentBB.getLastInstructionIndex();
      if (lastIndex >= 0 && infoItem.currentBB.getLastInstruction() instanceof SSAConditionalBranchInstruction) {
        Formula clone = postCond.clone();
        clone = m_instHandler.handle_conditional_branch(
            clone, infoItem.currentBB.getLastInstruction(), infoItem, successor);
        bbPostConds.add(new Object[] {successor, clone});
      }
      else if (lastIndex >= 0 && infoItem.currentBB.getLastInstruction() instanceof SSASwitchInstruction) {
        Formula clone = postCond.clone();
        clone = m_instHandler.handle_switch(
            clone, infoItem.currentBB.getLastInstruction(), infoItem, successor);
        bbPostConds.add(new Object[] {successor, clone});
      }
      else {
        bbPostConds.add(new Object[] {successor, postCond});
      }
    }
    return bbPostConds;
  }
  
  public static void main(String args[]) {
    try {      
      AbstractHandler instHandler = new CompleteForwardHandler();
      SMTChecker smtChecker = new SMTChecker(SMTChecker.SOLVERS.YICES);
      ForwardExecutor executor = new ForwardExecutor(args[0]/*jar file path*/, null, instHandler, smtChecker);
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
      execOptions.maxDispatchTargets  = 5;
      execOptions.maxRetrieve         = 10;
      execOptions.maxSmtCheck         = 5000;
      execOptions.maxInvokeDepth      = 0;
      execOptions.maxLoop             = 5;
      
      executor.compute(execOptions, null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
