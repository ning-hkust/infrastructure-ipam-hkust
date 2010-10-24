package hk.ust.cse.StaticAnalysis.BranchAnalyzer;

import hk.ust.cse.Prevision.Wala.Jar2IR;
import hk.ust.cse.Prevision.Wala.MethodMetaData;
import hk.ust.cse.Prevision.Wala.WalaAnalyzer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;

public class BranchAnalyzer {
  public BranchAnalyzer(String appJar) throws Exception {
    m_walaAnalyzer = new WalaAnalyzer(appJar);
  }
  
  /**
   * @return: null if cannot find the branch line at the method
   */
  public List<List<Integer>> findBranchesFirstLine(String methodName, int branchLineNo) {
    List<List<Integer>> branchesFirstLine = null;
    
    // getIR
    IR ir = Jar2IR.getIR(m_walaAnalyzer, methodName, branchLineNo);
    if (ir == null) {
      return null;
    }

    MethodMetaData methMetaData = new MethodMetaData(ir);
    
    // find the last branch instruction at this line
    int lastBranchInstIndex = -1;
    SSAInstruction[] insts = ir.getInstructions();
    for (int i = 0; i < insts.length; i++) {
      int currentLine = methMetaData.getLineNumber(i);
      if (currentLine < branchLineNo) { // have not reach the branch line yet
        continue;
      }
      else if (currentLine > branchLineNo) { // already passed the branch line
        break;
      }
      else if (!(insts[i] instanceof SSAConditionalBranchInstruction) && 
               !(insts[i] instanceof SSASwitchInstruction)) {
        continue;
      }
      
      lastBranchInstIndex = i;
    }
    
    if (lastBranchInstIndex >= 0) {
      if (insts[lastBranchInstIndex] instanceof SSAConditionalBranchInstruction) {
        branchesFirstLine = getConditionalBranchesFirstLine(lastBranchInstIndex, methMetaData);
      }
      else if (insts[lastBranchInstIndex] instanceof SSASwitchInstruction) {
        branchesFirstLine = getSwitchBranchesFirstLine(
            (SSASwitchInstruction)insts[lastBranchInstIndex], methMetaData);
      }
    }
    
    return branchesFirstLine;
  }
  
  private List<List<Integer>> getConditionalBranchesFirstLine(
      int lastBranchInst, MethodMetaData methMetaData) {
    List<Integer> branchesFirstLineIndex = new ArrayList<Integer>();
    List<Integer> branchesFirstLineNo    = new ArrayList<Integer>();
    
    ISSABasicBlock currentBB = methMetaData.getcfg().getBlockForInstruction(lastBranchInst);
    Collection<ISSABasicBlock> succBBs = methMetaData.getcfg().getNormalSuccessors(currentBB);
    assert(succBBs.size() == 2);
    
    Iterator<ISSABasicBlock> iter = succBBs.iterator();
    while (iter.hasNext()) {
      ISSABasicBlock bb = (ISSABasicBlock) iter.next();
      
      int index = bb.getFirstInstructionIndex();
      branchesFirstLineIndex.add(index);
      branchesFirstLineNo.add(methMetaData.getLineNumber(index));
    }
    
    List<List<Integer>> ret = new ArrayList<List<Integer>>();
    ret.add(branchesFirstLineIndex);
    ret.add(branchesFirstLineNo);
    return ret;
  }
  
  private List<List<Integer>> getSwitchBranchesFirstLine(
      SSASwitchInstruction switchInst, MethodMetaData methMetaData) {
    List<Integer> branchesFirstLineIndex = new ArrayList<Integer>();
    List<Integer> branchesFirstLineNo    = new ArrayList<Integer>();
    
    int[] labels = switchInst.getCasesAndLabels();
    int defaultLable = switchInst.getDefault();
    
    // branches of each label
    for (int i = 1; i < labels.length; i+=2) {
      if (labels[i] >= 0) {
        branchesFirstLineIndex.add(labels[i]);
        branchesFirstLineNo.add(methMetaData.getLineNumber(labels[i]));
      }
    }
    
    // default branch
    if (defaultLable >= 0) {
      branchesFirstLineIndex.add(defaultLable);
      branchesFirstLineNo.add(methMetaData.getLineNumber(defaultLable));
    }
    
    List<List<Integer>> ret = new ArrayList<List<Integer>>();
    ret.add(branchesFirstLineIndex);
    ret.add(branchesFirstLineNo);
    return ret;
  }
  
  public static void main(String[] args) throws Exception {
    BranchAnalyzer branchAnalyzer = new BranchAnalyzer("./test_programs/test_program.jar");
    
    List<List<Integer>> rets = 
      branchAnalyzer.findBranchesFirstLine("test_program.func12", 104);
    
    List<Integer> branchesFirstLineIndex = rets.get(0);
    List<Integer> branchesFirstLineNo    = rets.get(1);
    
    for (int i = 0, size = branchesFirstLineIndex.size(); i < size; i++) {
      System.out.println("Branch " + i + ": ");
      System.out.println("Index: " + branchesFirstLineIndex.get(i));
      System.out.println("Line: " + branchesFirstLineNo.get(i));
      System.out.println();
    }
  }
  
  private final WalaAnalyzer m_walaAnalyzer;
}
