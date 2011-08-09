package hk.ust.cse.Prevision.StaticAnalysis;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult.ConditionalBranchDefs;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAReturnInstruction;

public class DefAnalyzerWrapper {
  
  public DefAnalyzerWrapper(String appJar) throws Exception {
    m_defAnalyzer = new DefAnalyzer(appJar);
  }
  
  public void addIncludeName(String name) {
    m_defAnalyzer.addIncludeName(name);
  }
  
  public void computeDef(int maxLoopDepth) {
    m_lastResult = m_defAnalyzer.findAllDefs(maxLoopDepth);
  }

  /**
   * @return if is skip-able, return the skip to block, null otherwise
   */
  public ISSABasicBlock findSkipToBasicBlocks(SSACFG cfg, ISSABasicBlock mergingBB, 
      ISSABasicBlock normPred, Formula formula, String callSites) {
    
    List<ISSABasicBlock>[] preds = findSkipToBasicBlocks(cfg, mergingBB, 
        Arrays.asList(new ISSABasicBlock[] {normPred}), formula, callSites);
    return (preds[0].size() > 0) ? preds[0].get(0) : null;
  }
  
  public List<ISSABasicBlock>[] findSkipToBasicBlocks(SSACFG cfg, ISSABasicBlock mergingBB, 
      Formula formula, String callSites) {
    
    Collection<ISSABasicBlock> normPreds = cfg.getNormalPredecessors(mergingBB);
    return findSkipToBasicBlocks(cfg, mergingBB, normPreds, formula, callSites);
  }
  
  @SuppressWarnings("unchecked")
  public List<ISSABasicBlock>[] findSkipToBasicBlocks(SSACFG cfg, ISSABasicBlock mergingBB, 
      Collection<ISSABasicBlock> normPreds, Formula formula, String callSites) {
    
    List<ISSABasicBlock> skipToPreds  = new ArrayList<ISSABasicBlock>();
    List<ISSABasicBlock> notSkipPreds = new ArrayList<ISSABasicBlock>();
    
    List<ConditionalBranchDefs> skippables = findSkippableBranches(mergingBB, formula, callSites);
    for (ISSABasicBlock normPred : normPreds) {
      if (mergingBB.isExitBlock() && normPred.getLastInstructionIndex() >= 0 && 
          normPred.getLastInstruction() instanceof SSAReturnInstruction) {
        // if it is exit -> return, do not skip!
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
  
  private List<ConditionalBranchDefs> findSkippableBranches(ISSABasicBlock mergingBB, Formula formula, String callSites) {
    List<ConditionalBranchDefs> skippableList = new ArrayList<ConditionalBranchDefs>();
    
    if (m_lastResult != null) {
      List<ConditionalBranchDefs> condDefsList = 
        m_lastResult.getCondBranchDefsForMergingBB(mergingBB);
      
      if (condDefsList != null) {
        HashSet<String> allFieldNames = findAllFieldNames(formula);

        Hashtable<String, Reference> methodRefs = formula.getRefMap().get(callSites);
        for (ConditionalBranchDefs condDefs : condDefsList) {
          if (isCondBranchSkippable(condDefs, methodRefs, allFieldNames)) {
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
  
  private boolean isCondBranchSkippable(ConditionalBranchDefs condBranchDefs, 
      Hashtable<String, Reference> methodRefs, HashSet<String> allFieldNames) {
    
    boolean isSkippable = true;
    Iterator<String> iter = condBranchDefs.defs.iterator();
    while (iter.hasNext()) {
      String def = (String) iter.next();
      
      int index = def.lastIndexOf('.');
      if (index < 0) {
        if (methodRefs.containsKey(def)) {
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
  
  public HashSet<String> findAllFieldNames(Formula formula) {
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
  
  private final DefAnalyzer m_defAnalyzer;
  private DefAnalysisResult m_lastResult;
}
