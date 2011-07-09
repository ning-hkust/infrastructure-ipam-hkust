package hk.ust.cse.StaticAnalysis.DefAnalyzer;

import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult.ConditionalBranchDefs;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaAnalyzer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

public class DefAnalyzer {
  
  public DefAnalyzer(String appJar) throws Exception {
    m_walaAnalyzer   = new WalaAnalyzer(appJar);
    m_includingNames = new ArrayList<String>();
    m_dummy          = new Hashtable<String, Integer>();
  }
  
  public void addIncludeName(String name) {
    m_includingNames.add(name);
  }
  
  public DefAnalysisResult findAllDefs(int maxLookDepth) {
    DefAnalysisResult result = new DefAnalysisResult();
    
    Iterator<IClass> classes = m_walaAnalyzer.getClassHierarchy().iterator();
    while (classes.hasNext()) {
      IClass aClass = (IClass) classes.next();
      Iterator<IMethod> methods = aClass.getAllMethods().iterator();
      while (methods.hasNext()) {
        IMethod method = methods.next();
        if (!method.isAbstract() && !method.isNative() && containsName(method.getSignature())) {
          IR ir = Jar2IR.getIR(m_walaAnalyzer, method.getSignature()); // getIR
          if (ir != null) {
            getAllDefs(ir, 0, maxLookDepth, result);
          }
        }
      }        
    }
    return result;
  }
  
  private void getAllDefs(IR ir, int curDepth, int maxDepth, DefAnalysisResult result) {
    MethodMetaData methData = new MethodMetaData(ir);
    SSACFG cfg = methData.getcfg();
    
    List<ConditionalBranchDefs> currentCondBranchDefs = new ArrayList<ConditionalBranchDefs>();
    Hashtable<String, String> varMappings = new Hashtable<String, String>();
    
    SSAInstruction[] insts = getInstructions(ir);
    for (int i = 0; i < insts.length; i++) {
      if (insts[i] == null) {
        continue;
      }
      
      // check if the current instruction is out of some conditional branches
      int instIndex = methData.getInstructionIndex(insts[i]);
      ISSABasicBlock currentBlock = null;
      if (instIndex >= 0) {
        currentBlock = cfg.getBlockForInstruction(instIndex);
        for (int j = 0; j < currentCondBranchDefs.size(); j++) {
          ConditionalBranchDefs condBranchDefs = currentCondBranchDefs.get(j);
          if (condBranchDefs.endingBlock.getNumber() <= currentBlock.getNumber()) {
            currentCondBranchDefs.remove(j--);
          }
        }
      }
      
      if (insts[i] instanceof SSAPutInstruction) {
        SSAPutInstruction putfieldInst = (SSAPutInstruction) insts[i];

        // get the class type that declared this field
        String declaredField = putfieldInst.isStatic() ? 
            putfieldInst.getDeclaredField().getDeclaringClass().getName().toString() : 
            getVarName(methData, putfieldInst.getUse(0), varMappings);
        // get the name of the field
        declaredField += "." + putfieldInst.getDeclaredField().getName();
        
        // save field to all conditional branches
        result.addCondBranchDef(currentCondBranchDefs, declaredField);
        
        // save field to method defs
        result.addMethodDef(ir.getMethod(), declaredField);
      }
      else if (insts[i] instanceof SSANewInstruction) {
        SSANewInstruction newInst = (SSANewInstruction) insts[i];
        
        String def = getVarName(methData, newInst.getDef(), varMappings);
        
        // for array types, we have ".length" variables
        if (newInst.getConcreteType().isArrayType()) {
          // name of ".length" variables
          String defLength = def + ".length";
          result.addCondBranchDef(currentCondBranchDefs, defLength);
          // save length field to method defs
          result.addMethodDef(ir.getMethod(), defLength);
        }
        // each member fields
        else if (newInst.getConcreteType().isClassType()) {
          IClass newClass = m_walaAnalyzer.getClassHierarchy().lookupClass(newInst.getConcreteType());
          if (newClass != null) {
            List<String> defs = new ArrayList<String>();
            Collection<IField> fields = newClass.getAllInstanceFields();
            for (IField field : fields) {
              // get the class type that declared this field
              String declaredField = def;
              // get the name of the field
              declaredField += "." + field.getName();
              // the member field
              defs.add(declaredField);
            }
            result.addCondBranchDef(currentCondBranchDefs, defs);
            
            // save fields to method defs
            result.addMethodDef(ir.getMethod(), defs);
          }
        }
      }
      else if (insts[i] instanceof SSAArrayStoreInstruction) {
        SSAArrayStoreInstruction arrayStoreInst = (SSAArrayStoreInstruction) insts[i];

        String arrayRef = getVarName(methData, arrayStoreInst.getArrayRef(), varMappings);
        
        // save array ref to all conditional branches
        result.addCondBranchDef(currentCondBranchDefs, arrayRef);
        
        // save array ref to method defs
        result.addMethodDef(ir.getMethod(), arrayRef);
      }
      else if (insts[i] instanceof SSAInvokeInstruction && curDepth < maxDepth) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) insts[i];
        
        IR ir2 = Jar2IR.getIR(m_walaAnalyzer, invokeInst.getDeclaredTarget().getSignature());
        if (ir2 != null) {
          // recursively call getAllDefs
          DefAnalysisResult invokeResult = new DefAnalysisResult();
          getAllDefs(ir2, curDepth + 1, maxDepth, invokeResult);
          
          // translate result
          List<String> methodDefs = new ArrayList<String>();
          int count = invokeInst.getNumberOfParameters();
          for (int j = 0; j < count; j++) {
            String calleeVarName = "v" + (j + 1);
            String callerVarName = getVarName(methData, invokeInst.getUse(j), varMappings);
            
            HashSet<String> defs = invokeResult.getMethodDefs(ir2.getMethod());
            if (defs != null) {
              for (String def : defs) {
                if (def.equals(calleeVarName)) {
                  methodDefs.add(callerVarName);
                }
                else if (def.startsWith(calleeVarName + ".")) {
                  methodDefs.add(callerVarName + def.substring(def.indexOf('.')));
                }                
                else if (def.startsWith(calleeVarName + "[")) { // the index is not being translated
                  methodDefs.add(callerVarName + def.substring(def.indexOf('[')));
                }
              }
            }
          }
          
          // save callee defs to all conditional branches
          result.addCondBranchDef(currentCondBranchDefs, methodDefs);
          
          // save callee defs to method defs
          result.addMethodDef(ir.getMethod(), methodDefs);
        }
      }
      else if (insts[i] instanceof SSAConditionalBranchInstruction) {
        // save current conditional branch
        ISSABasicBlock mergingBB = findMergingBB(cfg, currentBlock);
        if (mergingBB != null) {
          ConditionalBranchDefs condBranchDefs = result.new ConditionalBranchDefs(
              cfg, currentBlock, currentCondBranchDefs, mergingBB);
          if (condBranchDefs.endingBlock != null) {
            currentCondBranchDefs.add(condBranchDefs);
          }
        }
      }

      // add def to conditional branches and method
      if (insts[i].getNumberOfDefs() > 0) {
        String def = getVarName(methData, insts[i].getDef(), varMappings);
        if (def.startsWith("v") && !def.equals("v-1")) {
          result.addCondBranchDef(currentCondBranchDefs, def);
          result.addMethodDef(ir.getMethod(), def);
        }
      }
      
      // handle variable transfer
      if (insts[i] instanceof SSAPiInstruction) {
        SSAPiInstruction piInst = (SSAPiInstruction) insts[i];
        String def = getVarName(methData, piInst.getDef(), varMappings);
        String val = getVarName(methData, piInst.getVal(), varMappings);
        varMappings.put(def, val);
      }
      else if (insts[i] instanceof SSACheckCastInstruction) {
        SSACheckCastInstruction checkcastInst = (SSACheckCastInstruction) insts[i];
        String def = getVarName(methData, checkcastInst.getDef(), varMappings);
        String val = getVarName(methData, checkcastInst.getUse(0), varMappings);
        varMappings.put(def, val);
      }
      else if (insts[i] instanceof SSAGetInstruction) {
        SSAGetInstruction getfieldInst = (SSAGetInstruction) insts[i];
        if (!getfieldInst.isStatic()) {
          String def = getVarName(methData, getfieldInst.getDef(), varMappings);
          String ref = getVarName(methData, getfieldInst.getUse(0), varMappings);
          String declaredField = ref + "." + getfieldInst.getDeclaredField().getName();
          varMappings.put(def, declaredField);
        }
      }
      else if (insts[i] instanceof SSAArrayLoadInstruction) {
        SSAArrayLoadInstruction arrayLoadInst = (SSAArrayLoadInstruction) insts[i];
        String def        = getVarName(methData, arrayLoadInst.getDef(), varMappings);
        String arrayRef   = getVarName(methData, arrayLoadInst.getArrayRef(), varMappings);
        String arrayIndex = getVarName(methData, arrayLoadInst.getIndex(), varMappings);
        String arrarStr   = getVarName(methData, arrayRef + "[" + arrayIndex + "]", varMappings);
        varMappings.put(def, arrarStr);
      }
      else if (insts[i] instanceof SSAArrayStoreInstruction) {
        SSAArrayStoreInstruction arrayStoreInst = (SSAArrayStoreInstruction) insts[i];
        String arrayRef   = getVarName(methData, arrayStoreInst.getArrayRef(), varMappings);
        String arrayIndex = getVarName(methData, arrayStoreInst.getIndex(), varMappings);
        String storeValue = getVarName(methData, arrayStoreInst.getValue(), varMappings);
        String arrarStr   = getVarName(methData, arrayRef + "[" + arrayIndex + "]", varMappings);
        varMappings.put(arrarStr, storeValue);
      }
    }
  }
  
  private String getVarName(MethodMetaData methData, int varID, Hashtable<String, String> varMapping) {
    String varName = methData.getSymbol(varID, "", m_dummy);
    String mapped = varMapping.get(varName);
    return mapped != null ? mapped : varName;
  }
  
  private String getVarName(MethodMetaData methData, String varName, Hashtable<String, String> varMapping) {
    String mapped = varMapping.get(varName);
    return mapped != null ? mapped : varName;
  }
  
  private SSAInstruction[] getInstructions(IR ir) {
    List<SSAInstruction> insts = new ArrayList<SSAInstruction>();
    
    SSAInstruction[] instSet = ir.getInstructions();
    int maxNumer = ir.getControlFlowGraph().getMaxNumber();
    for (int i = 0; i <= maxNumer; i++) {
      ISSABasicBlock basicBlock = ir.getControlFlowGraph().getBasicBlock(i);
      
      // add phi instructions
      Iterator<SSAPhiInstruction> phis = basicBlock.iteratePhis();
      while (phis.hasNext()) {
        SSAPhiInstruction phiInst = (SSAPhiInstruction) phis.next();
        insts.add(phiInst);
      }
      
      int index1 = basicBlock.getFirstInstructionIndex();
      int index2 = basicBlock.getLastInstructionIndex();
      if (index1 >= 0) {
        for (int j = index1; j <= index2; j++) {
          insts.add(instSet[j]);
        }
      }
      
      // add pi instructions
      Iterator<SSAPiInstruction> pis = basicBlock.iteratePis();
      while (pis.hasNext()) {
        SSAPiInstruction piInst = (SSAPiInstruction) pis.next();
        insts.add(piInst);
      }
    }
    return insts.toArray(new SSAInstruction[0]);
  }
  
  private ISSABasicBlock findMergingBB(SSACFG cfg, ISSABasicBlock condBranchBB) {
    Iterator<ISSABasicBlock> succNodes = cfg.getSuccNodes(condBranchBB);
    ISSABasicBlock succNode1 = succNodes.next();
    ISSABasicBlock succNode2 = succNodes.next();
    // make sure succNode1 is smaller
    if (succNode1.getNumber() > succNode2.getNumber()) {
      ISSABasicBlock tmp = succNode1;
      succNode1 = succNode2;
      succNode2 = tmp;
    }
    
    // find the merging block
    ISSABasicBlock mergingBlock = null;
    ISSABasicBlock firstBranchLastBB = cfg.getBasicBlock(succNode2.getNumber() - 1);
    Iterator<ISSABasicBlock> bbs = cfg.getSuccNodes(firstBranchLastBB);
    if (bbs.hasNext()) {
      mergingBlock = bbs.next();
    }
    return mergingBlock;
  }
  
  private boolean containsName(String name) {
    boolean ret = false;
    
    for (int i = 0, size = m_includingNames.size(); i < size; i++) {
      if (name.startsWith(m_includingNames.get(i))) {
        ret = true;
        break;
      }
    }
    return ret;
  }
  
  public static void main(String[] args) throws Exception {
    DefAnalyzer defAnalyzer = new DefAnalyzer("./test_programs/test_program.jar");
    defAnalyzer.addIncludeName("test_program.");
    DefAnalysisResult result = defAnalyzer.findAllDefs(5);
  
    Collection<ConditionalBranchDefs> condDefs = result.m_defsForCondBranch.values();
    for (ConditionalBranchDefs defs : condDefs) {
      for (String def : defs.defs) {
        System.out.println(def);
      }
      System.out.println("================================");
    }
    
//    IR ir = Jar2IR.getIR(defAnalyzer.m_walaAnalyzer, "test_program.func1", 7);
//    List<String> defs = result.getMethodDefs(ir.getMethod());
//    for (String def : defs) {
//      System.out.println(def);
//    }
  }
  
  private final WalaAnalyzer m_walaAnalyzer;
  private final List<String> m_includingNames;
  private final Hashtable<String, Integer> m_dummy;
}
