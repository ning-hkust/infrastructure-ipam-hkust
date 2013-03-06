package hk.ust.cse.Prevision.Optimization;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult.ConditionalBranchDefs;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalyzer;
import hk.ust.cse.Wala.WalaAnalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;

public class DefAnalyzerWrapper {
  
  public DefAnalyzerWrapper(String appJar, int maxLoopDepth) throws Exception {
    m_defAnalyzer  = new DefAnalyzer(appJar);
    m_maxLoopDepth = maxLoopDepth;
  }
  
  public DefAnalyzerWrapper(WalaAnalyzer walaAnalyzer, int maxLoopDepth) throws Exception {
    m_defAnalyzer  = new DefAnalyzer(walaAnalyzer);
    m_maxLoopDepth = maxLoopDepth;
  }

  // find defs at once for all methods within the including name
  public void computeDef(List<String> inclNames) {
    m_lastResult = m_defAnalyzer.findAllDefs(m_maxLoopDepth, inclNames);
  }

  public HashSet<String> getMethodFieldNames(IR ir) {
    HashSet<String> varsAndFieldNames = new HashSet<String>();
    
    // check if we have already computed defs for this method
    if (m_lastResult == null) {
      m_lastResult = new DefAnalysisResult();
    }
    if (m_lastResult.getMethodDefs(ir.getMethod()) == null) {
      m_defAnalyzer.findAllDefs(ir, m_maxLoopDepth, m_lastResult);
    }
    
    HashSet<String> methodDefs = m_lastResult.getMethodDefs(ir.getMethod());
    if (methodDefs != null) {
      for (String def : methodDefs) {
        int index = def.lastIndexOf('.');
        if (index >= 0) {
          // get the last field name
          String fieldName = def.substring(index + 1);
          varsAndFieldNames.add(fieldName);
        }
      }
    }
    return varsAndFieldNames;
  }
  
  public HashSet<String> getMergingBBVarsAndFieldNames(IR ir, ISSABasicBlock mergingBB) {
    HashSet<String> varsAndFieldNames = new HashSet<String>();
    
    // check if we have already computed defs for this method
    if (m_lastResult == null) {
      m_lastResult = new DefAnalysisResult();
    }
    if (m_lastResult.getMethodDefs(ir.getMethod()) == null) {
      m_defAnalyzer.findAllDefs(ir, m_maxLoopDepth, m_lastResult);
    }
    
    List<ConditionalBranchDefs> condDefsList = 
      m_lastResult.getCondBranchDefsForMergingBB(mergingBB);
    if (condDefsList != null) {
      for (ConditionalBranchDefs condDefs : condDefsList) {
        for (String def : condDefs.defs) {
          int index = def.lastIndexOf('.');
          if (index >= 0) {
            // get the last field name
            String fieldName = def.substring(index + 1);
            varsAndFieldNames.add(fieldName);
          }
          else {
            varsAndFieldNames.add("SSAVar:" + def);
          }
        }
      }
    }
    return varsAndFieldNames;
  }
  
  /**
   * @return if is skip-able, return the method entry block, null otherwise
   */
  public ISSABasicBlock findSkipToBasicBlocks(IR ir, Formula formula) {
    // check if we have already computed defs for this method
    if (m_lastResult == null) {
      m_lastResult = new DefAnalysisResult();
    }
    if (m_lastResult.getMethodDefs(ir.getMethod()) == null) {
      m_defAnalyzer.findAllDefs(ir, m_maxLoopDepth, m_lastResult);
    }
    
    HashSet<String> methodDefs = m_lastResult.getMethodDefs(ir.getMethod());
    HashSet<String> fieldNamesInFormula = findAllFieldNames(formula);
    
    ISSABasicBlock skipTo = ir.getControlFlowGraph().entry();
    for (String def : methodDefs) {
      int index = def.lastIndexOf('.');
      if (index >= 0) {
        // get the last field name
        String fieldName = def.substring(index + 1);
        if (fieldNamesInFormula.contains(fieldName)) {
          skipTo = null;
          break;
        }
      }
    }
    
    // do not skip methods with explicit throw statements
    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length && skipTo != null; i++) {
      skipTo = instructions[i] instanceof SSAThrowInstruction ? null : skipTo;
    }
    
    return skipTo;
  }

  /**
   * @return if is skip-able, return the skip to block, null otherwise
   */
  public ISSABasicBlock findSkipToBasicBlocks(IR ir, ISSABasicBlock mergingBB, 
      ISSABasicBlock normPred, Formula formula, String callSites) {
    
    List<ISSABasicBlock>[] preds = findSkipToBasicBlocks(ir, mergingBB, 
        Arrays.asList(new ISSABasicBlock[] {normPred}), formula, callSites);
    return (preds[0].size() > 0) ? preds[0].get(0) : null;
  }
  
  public List<ISSABasicBlock>[] findSkipToBasicBlocks(IR ir, ISSABasicBlock mergingBB, 
      Formula formula, String callSites) {
    
    Collection<ISSABasicBlock> normPreds = ir.getControlFlowGraph().getNormalPredecessors(mergingBB);
    return findSkipToBasicBlocks(ir, mergingBB, normPreds, formula, callSites);
  }
  
  @SuppressWarnings("unchecked")
  public List<ISSABasicBlock>[] findSkipToBasicBlocks(IR ir, ISSABasicBlock mergingBB, 
      Collection<ISSABasicBlock> normPreds, Formula formula, String callSites) {
    
    List<ISSABasicBlock> skipToPreds  = new ArrayList<ISSABasicBlock>();
    List<ISSABasicBlock> notSkipPreds = new ArrayList<ISSABasicBlock>();
    
    // check if we have already computed defs for this method
    if (m_lastResult == null) {
      m_lastResult = new DefAnalysisResult();
    }
    if (m_lastResult.getMethodDefs(ir.getMethod()) == null) {
      m_defAnalyzer.findAllDefs(ir, m_maxLoopDepth, m_lastResult);
    }
    
    // find skippable
    Hashtable<String, Reference> methodRefs = formula.getRefMap().get(callSites);
    List<ConditionalBranchDefs> skippables = findSkippableBranches(ir, mergingBB, formula, callSites);
    for (ISSABasicBlock normPred : normPreds) {
      if (mergingBB.isExitBlock() && normPred.getLastInstructionIndex() >= 0 && 
          normPred.getLastInstruction() instanceof SSAReturnInstruction && 
          methodRefs != null && methodRefs.containsKey("RET")) {
        // if it is exit -> return, do not skip, we want to concretes 'RET' in return
        notSkipPreds.add(normPred);
      }
      else {
        boolean inAnySkippables = false;
        for (ConditionalBranchDefs skippable : skippables) { // check if in any skippable
          if (isBBInCondBranch(skippable, normPred)) {
            if (!skipToPreds.contains(skippable.startingBlock)) {
              skipToPreds.add(skippable.startingBlock);
            }
            inAnySkippables = true;
            break;
          }
        }
        if (!inAnySkippables) {
          notSkipPreds.add(normPred);
        }
      }
    }
    return new List[] {skipToPreds, notSkipPreds};
  }
  
  private List<ConditionalBranchDefs> findSkippableBranches(IR ir, ISSABasicBlock mergingBB, Formula formula, String callSites) {
    List<ConditionalBranchDefs> skippableList = new ArrayList<ConditionalBranchDefs>();
    
    if (m_lastResult != null) {
      List<ConditionalBranchDefs> condDefsList = 
        m_lastResult.getCondBranchDefsForMergingBB(mergingBB);
      
      if (condDefsList != null) {
        HashSet<String> allFieldNames = findAllFieldNames(formula);

        Hashtable<String, Reference> methodRefs = formula.getRefMap().get(callSites);
        for (ConditionalBranchDefs condDefs : condDefsList) {
          if (!isCondBranchThrowsException(ir, condDefs) && 
              isCondBranchSkippable(condDefs, methodRefs, allFieldNames)) {
            addSkippableCondBranch(skippableList, condDefs);
          }
        }
      }
    }
    return skippableList;
  }
  
  private boolean isBBInCondBranch(ConditionalBranchDefs condDefs, ISSABasicBlock basicBlock) {
    return condDefs.startingBlock.getNumber() <= basicBlock.getNumber() && 
           condDefs.endingBlock.getNumber() > basicBlock.getNumber();
  }
  
  private boolean isCondBranchThrowsException(IR ir, ConditionalBranchDefs condBranchDefs) {
    boolean throwing = false;
    int startingBB = condBranchDefs.startingBlock.getNumber();
    int mergingBB  = condBranchDefs.mergingBlock.getNumber();
    for (int i = startingBB; i < mergingBB && !throwing; i++) {
      ISSABasicBlock bb = ir.getControlFlowGraph().getBasicBlock(i);
      if (bb != null) {
        Iterator<SSAInstruction> iter = bb.iterator();
        while (iter.hasNext() && !throwing) {
          SSAInstruction instruction = (SSAInstruction) iter.next();
          throwing = instruction != null && instruction instanceof SSAThrowInstruction;
        }
      }
    }
    return throwing;
  }
  
  private boolean isCondBranchSkippable(ConditionalBranchDefs condBranchDefs, 
      Hashtable<String, Reference> methodRefs, HashSet<String> allFieldNames) {
    
    boolean isSkippable = true;
    Iterator<String> iter = condBranchDefs.defs.iterator();
    while (iter.hasNext()) {
      String def = (String) iter.next();
      
      int index = def.lastIndexOf('.');
      if (index < 0) {
        if (methodRefs != null && methodRefs.containsKey(def)) {
          isSkippable = false;
          break;
        }
      }
      else {
        // get the last field name
        String fieldName = def.substring(index + 1);
        if (allFieldNames.contains(fieldName)) {
          isSkippable = false;
          break;
        }
      }
    }
    return isSkippable;
  }
  
  private HashSet<String> findAllFieldNames(Formula formula) {
    List<String> allFieldNames = new ArrayList<String>();
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    for (Hashtable<String, Reference> methodRefs : refMap.values()) {
      for (Reference ref : methodRefs.values()) {
        findAllFieldNames(ref, allFieldNames, false);
      }
    }
    return new HashSet<String>(allFieldNames);
  }
  
  private void findAllFieldNames(Reference ref, List<String> allFieldNames, boolean isField) {
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
  
  private void addSkippableCondBranch(List<ConditionalBranchDefs> skippableList, ConditionalBranchDefs condDefs) {
    int starting = condDefs.startingBlock.getNumber();
    int ending   = condDefs.endingBlock.getNumber();
    
    boolean isCovered = false;
    for (int i = 0; i < skippableList.size(); i++) {
      ConditionalBranchDefs skippable = skippableList.get(i);
      if (skippable.startingBlock.getNumber() >=  starting && 
          skippable.endingBlock.getNumber() <= ending) {
        skippableList.remove(i--);
      }
      else if (skippable.startingBlock.getNumber() <= starting && 
               skippable.endingBlock.getNumber() >= ending) {
        isCovered = true;
        break;
      }
    }
    
    if (!isCovered) {
      skippableList.add(condDefs);
    }
  }

  private final int         m_maxLoopDepth;
  private final DefAnalyzer m_defAnalyzer;
  private DefAnalysisResult m_lastResult;
}
