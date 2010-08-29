package hk.ust.cse.Prevision;

import hk.ust.cse.Prevision.Wala.Jar2IR;
import hk.ust.cse.Prevision.Wala.MethodMetaData;

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
import java.util.AbstractMap.SimpleEntry;
import java.util.jar.JarFile;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
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
    public GlobalOptionsAndStates(boolean inclInnerMostLine, boolean bUseSummary, 
        int maxRetrieve, int maxSmtCheck, int maxInvokeDepth, int maxLoop, CallStack fullCallStack) {
      // options
      this.inclInnerMostLine  = inclInnerMostLine;
      this.maxRetrieve        = maxRetrieve;
      this.maxSmtCheck        = maxSmtCheck;
      this.maxInvokeDepth     = maxInvokeDepth;
      this.maxLoop            = maxLoop;
      this.fullCallStack      = fullCallStack;
      
      // initialize summary
      if (bUseSummary) {
        this.summary = new Summary();
      }
      else {
        this.summary = null;
      }

      // states
      this.m_bEnteringCallStack = true; // at the beginning, we are entering call stack
    }

    public boolean isEnteringCallStack() {
      return m_bEnteringCallStack;
    }

    public void finishedEnteringCallStack() {
      m_bEnteringCallStack = false;
    }

    // global options
    public final boolean   inclInnerMostLine;
    public final int       maxRetrieve;
    public final int       maxSmtCheck;
    public final int       maxInvokeDepth;
    public final int       maxLoop;
    public final CallStack fullCallStack;
    public final Summary   summary;

    // global states
    private boolean  m_bEnteringCallStack;
  }

  public class BBorInstInfo {
    public BBorInstInfo(ISSABasicBlock currentBB, Predicate postCond,
        int sucessorType, ISSABasicBlock sucessorBB, BBorInstInfo sucessorInfo, 
        MethodMetaData methData, String valPrefix, WeakestPrecondition wp) {
      this.currentBB      = currentBB;
      this.postCond       = postCond;
      this.sucessorType   = sucessorType;
      this.sucessorBB     = sucessorBB;
      this.sucessorInfo   = sucessorInfo;
      this.methData       = methData;
      this.valPrefix      = valPrefix;
      this.wp             = wp;
    }
    
    public final Predicate postCond;
    public final int sucessorType;
    public final ISSABasicBlock currentBB;
    public final ISSABasicBlock sucessorBB;
    public final BBorInstInfo sucessorInfo;
    public final MethodMetaData methData;
    public final String valPrefix;
    public final WeakestPrecondition wp;
  }

  public WeakestPrecondition(JarFile jarFile) {
    m_jarFile = jarFile;

    // cache for IRs of this jar file
    m_IRCache = new AnalysisCache();
  }
  
  public WeakestPreconditionResult compute(GlobalOptionsAndStates optionsAndStates, 
      Predicate postCond) throws InvalidStackTraceException {
    return compute(optionsAndStates, postCond, null);
  }
  
  public WeakestPreconditionResult compute(GlobalOptionsAndStates optionsAndStates, 
      Predicate postCond, IR ir) throws InvalidStackTraceException {
    CallStack fullStack = optionsAndStates.fullCallStack;
    
    // compute from the outermost stack frame
    String methNameOrSig = fullStack.getCurMethodNameOrSign();
    int lineNo           = fullStack.getCurLineNo();
    
    // we only consider inclLine at the innermost call
    boolean inclLine = true;
    if (fullStack.getDepth() == 1) {
      inclLine = optionsAndStates.inclInnerMostLine;
    }
    
    return computeRec(optionsAndStates, methNameOrSig, lineNo, inclLine, fullStack, 0, "", postCond, ir);
  }
  
  WeakestPreconditionResult computeRec(GlobalOptionsAndStates optionsAndStates, 
      String methodNameOrSign, int nStartLine, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String valPrefix,
      Predicate postCond) throws InvalidStackTraceException {

    return computeRec(optionsAndStates, methodNameOrSign, nStartLine, inclLine,
        callStack, curInvokeDepth, valPrefix, postCond, null);
  }

  WeakestPreconditionResult computeRec(GlobalOptionsAndStates optionsAndStates, 
      String methodNameOrSign, int nStartLine, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String valPrefix,
      Predicate postCond, IR ir) throws InvalidStackTraceException {
    
    // start timing
    long start = System.currentTimeMillis();

    // get ir(ssa) for some method in jar file
    if (ir == null) {
      try {
        if (Utils.isMethodSignature(methodNameOrSign)) {
          ir = Jar2IR.getIR(m_jarFile.getName(), methodNameOrSign, m_IRCache);
        }
        else {
          ir = Jar2IR.getIR(m_jarFile.getName(), methodNameOrSign, nStartLine, m_IRCache);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // return if method is not found
    if (ir == null) {
      String msg = "Failed to locate " + methodNameOrSign;
      System.err.println(msg);
      throw new InvalidStackTraceException(msg);
    }

    // printIR(ir);
    // System.out.println(ir.toString());

    // init MethodMetaData, save it if it's not inside an invocation and it's at
    // the out most call
    MethodMetaData methMetaData = new MethodMetaData(ir);
    if (curInvokeDepth == 0 && callStack.isOutMostCall()) {
      m_methMetaData = methMetaData;

      // create container to save the invoke instructions at call stack points
      m_callStackInvokes = new Hashtable<BBorInstInfo, SSAInstruction>();

      // create dfs
      m_dfsStacks = new Hashtable<SimpleEntry<String, Predicate>, Stack<BBorInstInfo>>();
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
      ISSABasicBlock startFromBB = findBasicBlock(methMetaData.getcfg(), nStartLine);
      if (startFromBB == null) {
        String msg = "Line " + nStartLine + " does not contain valid instructions.";
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
    WeakestPreconditionResult wpResult = computeMethod(optionsAndStates, 
        methMetaData, dfsStack, nStartLine, inclLine, callStack, curInvokeDepth, valPrefix);

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
 
  private WeakestPreconditionResult computeMethod(GlobalOptionsAndStates optionsAndStates,
      MethodMetaData methData, Stack<BBorInstInfo> dfsStack, int startLine, boolean inclLine, 
      CallStack callStack, int curInvokeDepth, String valPrefix) throws InvalidStackTraceException {
    
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
    if (optionsAndStates.isEnteringCallStack() && callStack.getDepth() <= 1) {
      optionsAndStates.finishedEnteringCallStack();
    }

    // output method name
    System.out.println("Computing method: " + cfg.getMethod().getName());

    // start depth first search
    while (!dfsStack.empty()) {
      // for (int i = 0; i < dfsStack.size(); i++) {
      // System.out.print(dfsStack.get(i).currentBB.getNumber() + " ");
      // }
      // System.out.println();

      BBorInstInfo infoItem = dfsStack.pop();

      // if postCond is still satisfiable
      if (!infoItem.postCond.isContradicted()) {
        System.out.println("Computing BB" + infoItem.currentBB.getNumber());
        
        // get visited records
        Hashtable<ISSABasicBlock, Integer> visitedBB = infoItem.postCond.getVisitedRecord();
        
        // compute for this BB
        List<SimpleEntry<String, Predicate>> usedPredicates = new ArrayList<SimpleEntry<String, Predicate>>();
        Predicate precond = computeBB(optionsAndStates, methData, infoItem, startLine, 
            inclLine, callStack, starting, curInvokeDepth, valPrefix, usedPredicates);

        if (precond == null) {
          break;
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
          if (stack != null && stack.size() > 0) {
            canPop = false;
            break;
          }
        }
        // unpop it if can't pop
        if (!canPop) {
          dfsStack.push(infoItem);
        }

        if (!infoItem.currentBB.isEntryBlock()) {
          Collection<ISSABasicBlock> normPredBB =
            cfg.getNormalPredecessors(infoItem.currentBB);
          Collection<ISSABasicBlock> excpPredBB =
            cfg.getExceptionalPredecessors(infoItem.currentBB);
          
          // iterate all exceptional predecessors
          pushChildrenBlocks(excpPredBB, infoItem, precond, methData,
              Predicate.NPE_SUCCESSOR, dfsStack, optionsAndStates.maxLoop, valPrefix);
          
          // iterate all normal predecessors
          pushChildrenBlocks(normPredBB, infoItem, precond, methData,
              Predicate.NORMAL_SUCCESSOR, dfsStack, optionsAndStates.maxLoop, valPrefix);
        }
        else if (curInvokeDepth != 0 || !callStack.isOutMostCall()) {
          // we only do smtCheck() if it's not inside an invocation and
          // it's at the outermost invocation
          wpResult.addSatisfiable(precond);
          break; // return to InstHandler
        }
        else { // for entry block, no need to go further
          System.out.println("Performing SMT Check...");
          
          // output propagation path
          printPropagationPath(infoItem);
          
          // use SMT Solver to check precond and obtain a model
          Predicate.SMT_RESULT smtResult = precond.smtCheck();
          
          // limit maximum smt checks
          boolean canBreak = false;
          if (optionsAndStates.maxSmtCheck > 0 && ++smtChecked >= optionsAndStates.maxSmtCheck) {
            wpResult.setOverLimit(true);
            canBreak = true;
          }
          
          if (smtResult == Predicate.SMT_RESULT.SAT) {
            System.out.println("SMT Check succeeded!\n");
            
            // save the satisfiable precondition
            wpResult.addSatisfiable(precond);
            
            // limit maximum number of satisfiable preconditions to retrieve
            if (optionsAndStates.maxRetrieve > 0 && ++satRetrieved >= optionsAndStates.maxRetrieve) {
              wpResult.setReachMaximum(true);
              canBreak = true;
            }
          }
          else {
            System.out.println("SMT Check failed!\n");
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

  private Predicate computeBB(GlobalOptionsAndStates optionsAndStates, 
      MethodMetaData methData, BBorInstInfo infoItem, int startLine, boolean inclLine, 
      CallStack callStack, boolean[] starting, int curInvokeDepth, String valPrefix, 
      List<SimpleEntry<String, Predicate>> usedPredicates) throws InvalidStackTraceException {
    
    Predicate preCond = infoItem.postCond;
    SSAInstruction[] allInsts = methData.getcfg().getInstructions();
    
    // handle pi instructions first if any
    for (Iterator<SSAPiInstruction> it = infoItem.currentBB.iteratePis(); it.hasNext();) {
      SSAPiInstruction piInst = (SSAPiInstruction) it.next();
      if (piInst != null) {
        preCond = preCond.getPrecondtion(optionsAndStates, piInst, infoItem, callStack,
            curInvokeDepth, usedPredicates);
      }
    }

    // handle normal instructions
    boolean lastInst = true;
    int currInstIndex = infoItem.currentBB.getLastInstructionIndex();
    int firstInstIndex = infoItem.currentBB.getFirstInstructionIndex();
    while (currInstIndex >= 0 && currInstIndex >= firstInstIndex) {
      // get instruction
      SSAInstruction inst = allInsts[currInstIndex];

      int currLine = methData.getLineNumber(currInstIndex);
      
      // determine if entering call stack is still correct
      if (optionsAndStates.isEnteringCallStack()) {
        // determine if entering call stack is still correct
        if (callStack.getCurLineNo() == currLine) {
          if (inst instanceof SSAInvokeInstruction) {
            String methodNameOrSign = callStack.getNextMethodNameOrSign();
            
            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
            MethodReference mr = invokeInst.getDeclaredTarget();
            
            // FIXME: Should handle polymorphism! mr could be a method
            // of an interface while methodNameOrSign is the concrete method

            // determine if entering call stack is still correct
            if (!isTheSameMethod(mr, methodNameOrSign)) {
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
          String msg = "Failed to enter call stack: " + 
            callStack.getCurMethodNameOrSign() + " (Line " + callStack.getCurLineNo() + ")!";
          System.err.println(msg);
          throw new InvalidStackTraceException(msg);
        }
      }
      
      if (inst != null && (!starting[0] || (currLine - (inclLine ? 1 : 0)) < startLine)) {
        // get precond for this instruction
        if (lastInst) {
          preCond = preCond.getPrecondtion(optionsAndStates, inst, infoItem, 
              callStack, curInvokeDepth, usedPredicates);
        }
        else {
          // not the last instruction of the block
          BBorInstInfo instInfo = new BBorInstInfo(infoItem.currentBB, preCond, 
              Predicate.NORMAL_SUCCESSOR, infoItem.sucessorBB,
              infoItem.sucessorInfo, methData, valPrefix, this);
          preCond = preCond.getPrecondtion(optionsAndStates, inst, instInfo,
              callStack, curInvokeDepth, usedPredicates);
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
          preCond = InstHandler.handle_constant(preCond, null, infoItem, constantStr);
        }
      }

      // get previous inst
      currInstIndex--;
      lastInst = false;
    }
    
    // handle phi instructions last if any
    for (Iterator<SSAPhiInstruction> it = infoItem.currentBB.iteratePhis(); it.hasNext();) {
      SSAPhiInstruction phiInst = (SSAPhiInstruction) it.next();
      if (phiInst != null) {
        preCond = preCond.getPrecondtion(optionsAndStates, phiInst, infoItem, 
            callStack, curInvokeDepth, usedPredicates);
      }
    }
    
    // some wrap up at the entry block
    if (infoItem.currentBB.isEntryBlock()) {
      preCond = InstHandler.handle_entryblock(preCond, null, infoItem);
    }

    return preCond;
  }
  
  private boolean isTheSameMethod(MethodReference mr, String methodNameOrSign) {
    String invokingMethod = "";
    boolean isSignature = Utils.isMethodSignature(methodNameOrSign);
    if (isSignature) {
      // get invoking method signature
      invokingMethod = mr.getSignature();
    }
    else {
      String declaringClass = mr.getDeclaringClass().getName().toString();
      declaringClass = Utils.getClassTypeJavaStr(declaringClass);
      
      // get invoking method name
      invokingMethod = declaringClass + "." + mr.getName().toString();
    }

    methodNameOrSign = methodNameOrSign.replace('$', '.');
    return invokingMethod.equals(methodNameOrSign);
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
  private ISSABasicBlock findBasicBlock(SSACFG cfg, int lineNumber) {
    try {
      ISSABasicBlock foundBB = null;
      if (lineNumber > 0) {
        // try to retrieve the BB with the largest block number
        int largestBBNum         = -1;
        ISSABasicBlock largestBB  = null;

        int instCount = cfg.getInstructions().length;
        for (int i = 0; i < instCount; i++) {
          if (cfg.getInstructions()[i] != null) {
            int bcIndex = ((IBytecodeMethod) cfg.getMethod()).getBytecodeIndex(i);
            int line = ((IBytecodeMethod) cfg.getMethod()).getLineNumber(bcIndex);
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
      else {
        // get the exit block
        foundBB = cfg.getBasicBlock(cfg.getMaxNumber());
      }
      return foundBB;
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      return null;
    }
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
      JarFile jarFile = new JarFile(args[0]/*jar file path*/);
      WeakestPrecondition wp = new WeakestPrecondition(jarFile);
      
      // read stack frames
      CallStack callStack = new CallStack(true);
      for (int i = 1; i < args.length; i++) {
        String methName = args[i] /*method name*/;
        int lineNo      = Integer.parseInt(args[++i]) /*line number*/;
        
        callStack.addStackTrace(methName, lineNo);
      }
      
      // set options
      GlobalOptionsAndStates optionsAndStates = 
        wp.new GlobalOptionsAndStates(false, false, 10, 50, 1, 3, callStack);
      
      wp.compute(optionsAndStates, null);
      // wp.heapTracer();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private final JarFile             m_jarFile;
  private final AnalysisCache       m_IRCache;
  private MethodMetaData            m_methMetaData;
  private WeakestPreconditionResult m_wpResult;
  
  private Hashtable<BBorInstInfo, SSAInstruction>                        m_callStackInvokes;
  private Hashtable<SimpleEntry<String, Predicate>, Stack<BBorInstInfo>> m_dfsStacks;
}
