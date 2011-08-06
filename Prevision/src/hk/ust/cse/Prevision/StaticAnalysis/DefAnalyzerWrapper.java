package hk.ust.cse.Prevision.StaticAnalysis;

import hk.ust.cse.Prevision.Predicate;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult.ConditionalBranchDefs;
import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalyzer;

import java.util.ArrayList;
import java.util.Collection;
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

  @SuppressWarnings("unchecked")
  public List<ISSABasicBlock>[] findSkipToBasicBlocks(SSACFG cfg, ISSABasicBlock mergingBB, Predicate predicate) {
    List<ISSABasicBlock> skipToPreds  = new ArrayList<ISSABasicBlock>();
    List<ISSABasicBlock> notSkipPreds = new ArrayList<ISSABasicBlock>();
    
    Collection<ISSABasicBlock> normPreds = cfg.getNormalPredecessors(mergingBB);
    List<ConditionalBranchDefs> skippables = findSkippableBranches(mergingBB, predicate);
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
  
  private List<ConditionalBranchDefs> findSkippableBranches(ISSABasicBlock mergingBB, Predicate predicate) {
    List<ConditionalBranchDefs> skippableList = new ArrayList<ConditionalBranchDefs>();
    
    if (m_lastResult != null) {
      List<ConditionalBranchDefs> condDefsList = 
        m_lastResult.getCondBranchDefsForMergingBB(mergingBB);
      
      if (condDefsList != null) {
        for (ConditionalBranchDefs condDefs : condDefsList) {
          if (isCondBranchSkippable(condDefs, predicate)) {
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
  
  private boolean isCondBranchSkippable(ConditionalBranchDefs condBranchDefs, Predicate predicate) {
    Hashtable<String, List<String>> varMap = predicate.getVarMap();
    Hashtable<String, String> phiMap = predicate.getPhiMap();
    
    boolean isSkippable = true;
    Iterator<String> iter = condBranchDefs.defs.iterator();
    while (iter.hasNext()) {
      String def = (String) iter.next();
      
      if (varMap.get(def) != null || phiMap.get(def) != null) {
        isSkippable = false;
        break;
      }
    }
    return isSkippable;
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
