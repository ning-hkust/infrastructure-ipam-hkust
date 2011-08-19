package hk.ust.cse.StaticAnalysis.DefAnalyzer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;

public class DefAnalysisResult {

  public class ConditionalBranchDefs {
    public ConditionalBranchDefs(SSACFG cfg, ISSABasicBlock condBranchBB, 
        List<ConditionalBranchDefs> upperCondBranchesDefs, ISSABasicBlock mergingBB) {
      
      // the conditional branch instruction is always the last of a basic block
      startingBlock = condBranchBB;
      startingInst  = (SSAConditionalBranchInstruction) condBranchBB.getLastInstruction();
      mergingBlock  = mergingBB;
      
      // compute ending block
      ISSABasicBlock endingBB = mergingBB;
      for (ConditionalBranchDefs upperCondBranchDefs : upperCondBranchesDefs) {
        Iterator<ISSABasicBlock> succNodes = cfg.getSuccNodes(upperCondBranchDefs.startingBlock);
        ISSABasicBlock succNode1 = succNodes.next();
        ISSABasicBlock succNode2 = succNodes.next();
        // make sure succNode1 is smaller
        if (succNode1.getNumber() > succNode2.getNumber()) {
          ISSABasicBlock tmp = succNode1;
          succNode1 = succNode2;
          succNode2 = tmp;
        }
        if (condBranchBB.getNumber() < succNode2.getNumber() && 
            endingBB.getNumber() > succNode2.getNumber()) {
          endingBB = succNode2;
        }
      }
      endingBlock = endingBB;
    
      // assign defs to this conditional branch
      defs = new HashSet<String>();
    }
    
    public final SSAConditionalBranchInstruction startingInst;
    public final ISSABasicBlock  startingBlock;
    public final ISSABasicBlock  mergingBlock;
    public final ISSABasicBlock  endingBlock;
    public final HashSet<String> defs;
  }
  
  public DefAnalysisResult() {
    m_methodDefs           = new Hashtable<IMethod, HashSet<String>>();
    m_defsForCondBranch    = new Hashtable<ISSABasicBlock, ConditionalBranchDefs>();
    m_condDefsForMergingBB = new Hashtable<ISSABasicBlock, List<ConditionalBranchDefs>>();
  }
  
  void addMethodDef(IMethod method) {
    HashSet<String> oriDefs = m_methodDefs.get(method);
    if (oriDefs == null) {
      oriDefs = new HashSet<String>();
      m_methodDefs.put(method, oriDefs);
    }
  }
  
  void addMethodDef(IMethod method, String def) {
    List<String> defs = new ArrayList<String>();
    defs.add(def);
    addMethodDef(method, defs);
  }
  
  void addMethodDef(IMethod method, List<String> defs) {
    HashSet<String> oriDefs = m_methodDefs.get(method);
    if (oriDefs == null) {
      oriDefs = new HashSet<String>();
      m_methodDefs.put(method, oriDefs);
    }
    oriDefs.addAll(defs);
  }
  
  void addCondBranchDef(ConditionalBranchDefs condBranchDefs) {
    ConditionalBranchDefs oriCondBranchDefs = m_defsForCondBranch.get(condBranchDefs.startingBlock);
    if (oriCondBranchDefs == null) {
      // add ConditionalBranchDefs to m_defsForCondBranch
      m_defsForCondBranch.put(condBranchDefs.startingBlock, condBranchDefs);
      
      // add ConditionalBranchDefs to m_condDefsForMergingBB
      List<ConditionalBranchDefs> condDefsList = m_condDefsForMergingBB.get(condBranchDefs.mergingBlock);
      if (condDefsList == null) {
        condDefsList = new ArrayList<ConditionalBranchDefs>();
        m_condDefsForMergingBB.put(condBranchDefs.mergingBlock, condDefsList);
      }
      condDefsList.add(condBranchDefs);
    }
  }
  
  void addCondBranchDef(List<ConditionalBranchDefs> currentCondBranchDefs, String def) {
    List<String> defs = new ArrayList<String>();
    defs.add(def);
    addCondBranchDef(currentCondBranchDefs, defs);
  }
  
  void addCondBranchDef(List<ConditionalBranchDefs> currentCondBranchDefs, List<String> defs) {
    for (ConditionalBranchDefs condBranchDefs : currentCondBranchDefs) {
      ConditionalBranchDefs oriCondBranchDefs = m_defsForCondBranch.get(condBranchDefs.startingBlock);
      if (oriCondBranchDefs == null) {
        // add ConditionalBranchDefs to m_defsForCondBranch
        m_defsForCondBranch.put(condBranchDefs.startingBlock, condBranchDefs);
        
        // add ConditionalBranchDefs to m_condDefsForMergingBB
        List<ConditionalBranchDefs> condDefsList = m_condDefsForMergingBB.get(condBranchDefs.mergingBlock);
        if (condDefsList == null) {
          condDefsList = new ArrayList<ConditionalBranchDefs>();
          m_condDefsForMergingBB.put(condBranchDefs.mergingBlock, condDefsList);
        }
        condDefsList.add(condBranchDefs);
        
        oriCondBranchDefs = condBranchDefs;
      }
      oriCondBranchDefs.defs.addAll(defs);
    }
  }
  
  public ConditionalBranchDefs getCondBranchDefs(ISSABasicBlock condBranchBB) {
    return m_defsForCondBranch.get(condBranchBB);
  }
  
  public List<ConditionalBranchDefs> getCondBranchDefsForMergingBB(ISSABasicBlock mergingBB) {
    return m_condDefsForMergingBB.get(mergingBB);
  }
  
  public HashSet<String> getMethodDefs(IMethod method) {
    return m_methodDefs.get(method);
  }
  
  private final Hashtable<IMethod, HashSet<String>>                    m_methodDefs;
  private final Hashtable<ISSABasicBlock, ConditionalBranchDefs>       m_defsForCondBranch;
  private final Hashtable<ISSABasicBlock, List<ConditionalBranchDefs>> m_condDefsForMergingBB;
}
