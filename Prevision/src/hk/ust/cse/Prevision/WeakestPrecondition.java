package hk.ust.cse.Prevision;

import hk.ust.cse.Prevision.InstructionHandler.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandler.CompleteBackwardHandler;
import hk.ust.cse.Prevision.Wala.Jar2IR;
import hk.ust.cse.Prevision.Wala.MethodMetaData;
import hk.ust.cse.Prevision.Wala.WalaAnalyzer;
import hk.ust.cse.Prevision.Wala.WalaUtils;

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

public class WeakestPrecondition {
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
    public BBorInstInfo(ISSABasicBlock currentBB, Predicate postCond,
        int sucessorType, ISSABasicBlock sucessorBB, BBorInstInfo sucessorInfo, 
        MethodMetaData methData, String valPrefix, WeakestPrecondition wp) {
      this.currentBB    = currentBB;
      this.postCond     = postCond;
      this.sucessorType = sucessorType;
      this.sucessorBB   = sucessorBB;
      this.sucessorInfo = sucessorInfo;
      this.methData     = methData;
      this.valPrefix    = valPrefix;
      this.wp           = wp;
    }
    
    public BBorInstInfo clone() {
      BBorInstInfo newInfo = new BBorInstInfo(currentBB, postCond, sucessorType, 
          sucessorBB, sucessorInfo, methData, valPrefix, wp);
      newInfo.target = target;
      return newInfo;
    }
    
    public SimpleEntry<SSAInvokeInstruction, CGNode> target;
    
    public final Predicate postCond;
    public final int sucessorType;
    public final ISSABasicBlock currentBB;
    public final ISSABasicBlock sucessorBB;
    public final BBorInstInfo sucessorInfo;
    public final MethodMetaData methData;
    public final String valPrefix;
    public final WeakestPrecondition wp;
  }

  public WeakestPrecondition(String appJar, AbstractHandler instHandler) throws Exception {
    // create the wala analyzer which holds all the 
    // wala related information of this wp instance
    m_walaAnalyzer = new WalaAnalyzer(appJar);
    
    // set instruction handler
    m_instHandler = instHandler;
  }
  
  public WeakestPreconditionResult compute(GlobalOptionsAndStates optAndStates, 
      Predicate postCond) throws InvalidStackTraceException {
    return compute(optAndStates, postCond, null);
  }
  
  public WeakestPreconditionResult compute(GlobalOptionsAndStates optAndStates, 
      Predicate postCond, IR ir) throws InvalidStackTraceException {
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
    
    return computeRec(optAndStates, cgNode, ir, lineNo, startingInst, inclLine, 
        fullStack, 0, "", postCond);
  }
  
  public WeakestPreconditionResult computeRec(GlobalOptionsAndStates optAndStates, 
      CGNode cgNode, String methNameOrSig, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, int curInvokeDepth, String valPrefix, 
      Predicate postCond) throws InvalidStackTraceException {
    
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
        inclLine, callStack, curInvokeDepth, valPrefix, postCond);
  }

  /**
   * @param cgNode: only useful when we use 'compDispatchTargets' function
   */
  public WeakestPreconditionResult computeRec(GlobalOptionsAndStates optAndStates, 
      CGNode cgNode, IR ir, int startLine, int startingInst, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String valPrefix, 
      Predicate postCond) throws InvalidStackTraceException {
    
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

      // create dfs
      m_dfsStacks = new Hashtable<SimpleEntry<String, Predicate>, Stack<BBorInstInfo>>();
      
      // clear wpResult
      m_wpResult = null;
    }

    // stack for depth first search, try to start from previous end point
    Stack<BBorInstInfo> dfsStack = null;
    if (postCond != null) {
      // create key
      SimpleEntry<String, Predicate> key = 
        new SimpleEntry<String, Predicate>(valPrefix, postCond);
      dfsStack = m_dfsStacks.get(key);
    }
    if (dfsStack == null || dfsStack.empty()) {
      // start from the basic block at nStartLine
      ISSABasicBlock startFromBB = findBasicBlock(methMetaData, startLine, startingInst);
      if (startFromBB == null) {
        String msg = "Failed to find a valid basic block at line: " + startLine + 
                     " and instruction index: " + startingInst;
        System.err.println(msg);
        throw new InvalidStackTraceException(msg);
      }

      // create a "TRUE" Predicate if postCond is null
      if (postCond == null) {
        postCond = new Predicate();
      }

      // push in the first block
      dfsStack = new Stack<BBorInstInfo>();
      dfsStack.push(new BBorInstInfo(startFromBB, postCond, 
          Predicate.NORMAL_SUCCESSOR, null, null, methMetaData, valPrefix, this));

      // create key
      SimpleEntry<String, Predicate> key = 
        new SimpleEntry<String, Predicate>(valPrefix, postCond);
      m_dfsStacks.put(key, dfsStack);
    }

    // start depth first search
    WeakestPreconditionResult wpResult = computeMethod(optAndStates, cgNode, methMetaData, 
        dfsStack, startLine, startingInst, inclLine, callStack, curInvokeDepth, valPrefix);
    
    // save result if it's not inside an invocation and it's at the out most call
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_wpResult = wpResult;
      
      // print satisfiable preconditions
      List<Predicate> satisfiables = m_wpResult.getSatisfiables();
      for (int i = 0; i < satisfiables.size(); i++) {
        System.out.println("Satisfiable Precondition " + (i + 1) + ": ");
        printResult(satisfiables.get(i));
        System.out.println();
      }
      
      // clear non-solver data in each Predicate object
      m_wpResult.clearAllSatNonSolverData();

      // end timing
      long end = System.currentTimeMillis();
      System.out.println("Total elapsed: " + (end - start) + "ms!");
    }

    return wpResult;
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
  
  public MethodMetaData getMethodMetaData() {
    return m_methMetaData;
  }
  
  public WeakestPreconditionResult getWpResult() {
    return m_wpResult;
  }

  public List<Predicate> getSatisfiables() {
    return m_wpResult.getSatisfiables();
  }
  
  // return the first satisfiable precondition if exists
  public Predicate getFirstSatisfiable() {
    return m_wpResult.getFirstSatisfiable();
  }
  
  public WalaAnalyzer getWalaAnalyzer() {
    return m_walaAnalyzer;
  }
 
  private WeakestPreconditionResult computeMethod(GlobalOptionsAndStates optAndStates,
      CGNode cgNode, MethodMetaData methData, Stack<BBorInstInfo> dfsStack, 
      int startLine, int startingInst, boolean inclLine, CallStack callStack, 
      int curInvokeDepth, String valPrefix) throws InvalidStackTraceException {
    
    // start timing
    long start = System.currentTimeMillis();

    // we don't want to return a null wpResult
    WeakestPreconditionResult wpResult = new WeakestPreconditionResult();
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
    while (!dfsStack.empty()) {
      // for (int i = 0; i < dfsStack.size(); i++) {
      // System.out.print(dfsStack.get(i).currentBB.getNumber() + " ");
      // }
      // System.out.println();

      BBorInstInfo infoItem = dfsStack.pop();

      // if postCond is still satisfiable
      //if (!infoItem.postCond.isContradicted()) {
      if (true) {
        int instIndex = infoItem.currentBB.getLastInstructionIndex();
        String lineNo = (instIndex >= 0) ? " @ line " + methData.getLineNumber(instIndex) : "";
        System.out.println("Computing BB" + infoItem.currentBB.getNumber() + lineNo);
        
        // get visited records
        Hashtable<ISSABasicBlock, Integer> visitedBB = infoItem.postCond.getVisitedRecord();
        
        // compute for this BB
        int oriStackSize = dfsStack.size();
        List<SimpleEntry<String, Predicate>> usedPredicates = new ArrayList<SimpleEntry<String, Predicate>>();
        Predicate precond = computeBB(optAndStates, cgNode, methData, infoItem, 
            startLine, startingInst, inclLine, callStack, starting, curInvokeDepth, 
            valPrefix, dfsStack, usedPredicates);

        if (precond == null) {
          break;
        }

        // if invocation targets have been pushed into stack, skip the current inst
        if (dfsStack.size() != oriStackSize) {
          continue;
        }
        
        // out put for debug
        //System.out.println(precond.getVarMap().toString());
        //System.out.println(precond.getPhiMap().toString());
        //System.out.println(precond.getSMTStatements().toString());

        // marked as visited
        precond.setVisitedRecord(visitedBB, infoItem);

        // decide whether or not we can pop the basic block
        boolean canPop = true;
        for (SimpleEntry<String, Predicate> usedPredicate : usedPredicates) {
          Stack<BBorInstInfo> stack = m_dfsStacks.get(usedPredicate);
          if (stack != null) {
            if (stack.size() > 0) {
              canPop = false;
            }
            else {
              m_dfsStacks.remove(usedPredicate);
            }
          }
        }
        // unpop it if can't pop
        if (!canPop) {
          dfsStack.push(infoItem);
        }

        // check whether the caught exception is/can be triggered
        if (!infoItem.currentBB.isCatchBlock() && 
            !infoItem.currentBB.isExitBlock() && 
            isCatchNeverTriggered(precond)) {
          // the caught exception is not trigger, no need to go further
          System.out.println("The caught exception is not triggered! Don't need to go futher.");
          continue;
        }
        
        if (!infoItem.currentBB.isEntryBlock()) {
          Collection<ISSABasicBlock> normPredBB =
            cfg.getNormalPredecessors(infoItem.currentBB);
          Collection<ISSABasicBlock> excpPredBB =
            cfg.getExceptionalPredecessors(infoItem.currentBB);

          // if have specified the optAndStates.startingInstBranchesTo list, 
          // only take the specified branches at the starting basic block
          if (shouldCheckBranching(cfg, startingInst, curInvokeDepth, callStack, optAndStates, infoItem)) {
            
            // retain only the branches in startingInstBranchesTo list
            retainOnlyBranches(cfg, optAndStates.startingInstBranchesTo, normPredBB, excpPredBB);
          }
          
          // only traverse exceptional paths when we come from a catch block
          if (isCaught(precond)) {
            // iterate all exceptional predecessors
            pushChildrenBlocks(excpPredBB, infoItem, precond, methData,
                Predicate.EXCEPTIONAL_SUCCESSOR, dfsStack, optAndStates.maxLoop, valPrefix);            
          }
          
          // iterate all normal predecessors
          pushChildrenBlocks(normPredBB, infoItem, precond, methData,
              Predicate.NORMAL_SUCCESSOR, dfsStack, optAndStates.maxLoop, valPrefix);
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
          wpResult.addSatisfiable(precond);
          break; // return to InstHandler
        }
        else { // for entry block, no need to go further
          System.out.println("Performing " + (smtChecked + 1) + "th SMT Check...");
          
          // output propagation path
          printPropagationPath(infoItem);
          
          // use SMT Solver to check precond and obtain a model
          Predicate.SMT_RESULT smtResult = precond.smtCheck();
          
          // limit maximum smt checks
          boolean canBreak = false;
          if (optAndStates.maxSmtCheck > 0 && ++smtChecked >= optAndStates.maxSmtCheck) {
            wpResult.setOverLimit(true);
            System.out.println("Reached Maximum SMT Check Limit!");
            canBreak = true;
          }
          
          if (smtResult == Predicate.SMT_RESULT.SAT) {
            System.out.println("SMT Check succeeded!\n");
            
            // save the satisfiable precondition
            wpResult.addSatisfiable(precond);
            
            // limit maximum number of satisfiable preconditions to retrieve
            if (optAndStates.maxRetrieve > 0 && ++satRetrieved >= optAndStates.maxRetrieve) {
              wpResult.setReachMaximum(true);
              System.out.println("Reached Maximum Retrieve Limit!");
              canBreak = true;
            }
          }
          else {
            System.out.println("SMT Check failed!\n");
            
            // memory consumption may grow overtime as not-sat predicates added
            if (optAndStates.saveNotSatResults) {
              // clear non-solver data in the not satisfiable Predicate object
              precond.clearNonSolverData();
              
              // save the not satisfiable precondition
              wpResult.addNotSatisfiable(precond);
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

    return wpResult;
  }

  private Predicate computeBB(GlobalOptionsAndStates optAndStates, CGNode cgNode, 
      MethodMetaData methData, BBorInstInfo infoItem, int startLine, int startingInst, 
      boolean inclLine, CallStack callStack, boolean[] starting, int curInvokeDepth, 
      String valPrefix, Stack<BBorInstInfo> dfsStack, 
      List<SimpleEntry<String, Predicate>> usedPredicates) throws InvalidStackTraceException {
    
    Predicate preCond = infoItem.postCond;
    SSAInstruction[] allInsts = methData.getcfg().getInstructions();
    
    // handle pi instructions first if any
    for (Iterator<SSAPiInstruction> it = infoItem.currentBB.iteratePis(); it.hasNext();) {
      SSAPiInstruction piInst = (SSAPiInstruction) it.next();
      if (piInst != null) {
        preCond = m_instHandler.handle(optAndStates, cgNode, preCond, piInst, 
            infoItem, callStack, curInvokeDepth, usedPredicates);
      }
    }

    // handle normal instructions
    boolean lastInst   = true;
    int currInstIndex  = infoItem.currentBB.getLastInstructionIndex();
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
              dfsStack.add(newInfo);
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
                infoItem, callStack, curInvokeDepth, usedPredicates);
          }
          else {
            // not the last instruction of the block
            BBorInstInfo instInfo = new BBorInstInfo(infoItem.currentBB, preCond, 
                Predicate.NORMAL_SUCCESSOR, infoItem.sucessorBB,
                infoItem.sucessorInfo, methData, valPrefix, this);
            preCond = m_instHandler.handle(optAndStates, cgNode, preCond, inst, 
                instInfo, callStack, curInvokeDepth, usedPredicates);
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
        String constantStr = methData.getConstantInstructionStr(currInstIndex);
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
    
    // handle phi instructions last if any
    for (Iterator<SSAPhiInstruction> it = infoItem.currentBB.iteratePhis(); it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        preCond = m_instHandler.handle(optAndStates, cgNode, preCond, phiInst, 
            infoItem, callStack, curInvokeDepth, usedPredicates);
      }
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
      BBorInstInfo currentInfo, Predicate precond, MethodMetaData methData,
      int successorType, Stack<BBorInstInfo> dfsStack, int maxLoop, String valPrefix) {

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
      if (count != null) {
        if (count < maxLoop)
        {
          visitedList.add(basicBlock);
        }
      }
      else {
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
    
    // push the visited ones into the beginning of the stack
    for (ISSABasicBlock visited : visitedList) {
      // we don't check precond at the moment
      dfsStack.push(new BBorInstInfo(visited, precond, successorType, 
          currentInfo.currentBB, currentInfo, methData, valPrefix, this));
    }
    
    // push the non visited ones into the beginning of the stack
    for (ISSABasicBlock notvisited : notvisitedList) {
      // we don't check precond at the moment
      dfsStack.push(new BBorInstInfo(notvisited, precond, successorType, 
          currentInfo.currentBB, currentInfo, methData, valPrefix, this));
    }
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
  
  private boolean isCaught(Predicate predicate) {
    return  predicate.getVarMap().containsKey("Caught");
  }
  
  /**
   * Assuming there is at most one 'Caught ...' at a time.
   */
  private boolean isCatchNeverTriggered(Predicate predicate) {
    return  predicate.getVarMap().containsKey("Caught") && 
           !predicate.getVarMap().containsKey("ThrownInstCurrent");
  }
  
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

  private void printResult(Predicate precondtion) {
    if (precondtion != null) {
      System.out.println("SMT Solver Input-----------------------------------");
      System.out.println(precondtion.getLastSolverInput());
      
      System.out.println("SMT Solver Output----------------------------------");
      System.out.println(precondtion.getLastSolverOutput());
      
      System.out.println("Parsed SatModel----------------------------------");
      for (int i = 0, size = precondtion.getLastSatModel().size(); i < size; i++) {
        System.out.println(precondtion.getLastSatModel().get(i).toYicesExprString());
      }
      System.out.println();

      System.out.println("SMT Statements--------------------------------");
      HashSet<String> outputted = new HashSet<String>();
      List<List<String>> gotSMTStatements = precondtion.getSMTStatements();
      for (int i = gotSMTStatements.size() - 1; i >= 0; i--) {
        List<String> smtStatementTerms = gotSMTStatements.get(i);

        String smtStatement = "";
        for (int j = 0; j < smtStatementTerms.size(); j++) {
          if (smtStatement.length() > 0) {
            smtStatement += " or ";
          }

          smtStatement += smtStatementTerms.get(j++);
          smtStatement += " " + smtStatementTerms.get(j++);
          smtStatement += " " + smtStatementTerms.get(j);
        }

        if (!outputted.contains(smtStatement)) {
          System.out.println(smtStatement);
          outputted.add(smtStatement);
        }
      }
      System.out.println();
      
      System.out.println("SMT VarMap-------------------------------------");
      Hashtable<String, List<String>> gotVarMap = precondtion.getVarMap();
      Enumeration<String> keys = gotVarMap.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        List<String> vars = gotVarMap.get(key);
        for (String var : vars) {
          System.out.print(var + " = ");
        }
        System.out.println(key);
        //int bcIndex = ((IBytecodeMethod) cfg.getMethod()).getBytecodeIndex(6);
        //System.out.println(cfg.getMethod().getLocalVariableName(bcIndex, 4));
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
      AbstractHandler instHandler = new CompleteBackwardHandler();
      WeakestPrecondition wp = new WeakestPrecondition(args[0]/*jar file path*/, instHandler);
      
      // read stack frames
      CallStack callStack = new CallStack(true);
      for (int i = 1; i < args.length; i++) {
        String methName = args[i] /*method name*/;
        int lineNo      = Integer.parseInt(args[++i]) /*line number*/;
        
        callStack.addStackTrace(methName, lineNo);
      }
      
      // set options
      GlobalOptionsAndStates optAndStates = wp.new GlobalOptionsAndStates(callStack, false);
      optAndStates.maxDispatchTargets = 2;
      optAndStates.maxRetrieve        = 10;
      optAndStates.maxSmtCheck        = 5000;
      optAndStates.maxInvokeDepth     = 2;
      optAndStates.maxLoop            = 3;
      
      wp.compute(optAndStates, null);
      // wp.heapTracer();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private final WalaAnalyzer        m_walaAnalyzer;
  private final AbstractHandler     m_instHandler;
  private MethodMetaData            m_methMetaData;
  private WeakestPreconditionResult m_wpResult;

  private Hashtable<BBorInstInfo, SSAInstruction>                        m_callStackInvokes;
  private Hashtable<SimpleEntry<String, Predicate>, Stack<BBorInstInfo>> m_dfsStacks;
}
