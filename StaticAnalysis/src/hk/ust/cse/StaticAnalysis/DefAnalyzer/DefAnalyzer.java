package hk.ust.cse.StaticAnalysis.DefAnalyzer;

import hk.ust.cse.StaticAnalysis.DefAnalyzer.DefAnalysisResult.ConditionalBranchDefs;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaAnalyzer;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.ibm.wala.ssa.SymbolTable;

public class DefAnalyzer {
  
  public DefAnalyzer(String appJar) throws Exception {
    m_walaAnalyzer   = new WalaAnalyzer(appJar);
  }
  
  // find defs at once for all methods within the including name
  public DefAnalysisResult findAllDefs(int maxLookDepth, List<String> inclNames) {
    DefAnalysisResult result = new DefAnalysisResult();
    
    Iterator<IClass> classes = m_walaAnalyzer.getClassHierarchy().iterator();
    while (classes.hasNext()) {
      IClass aClass = (IClass) classes.next();
      Iterator<IMethod> methods = aClass.getAllMethods().iterator();
      while (methods.hasNext()) {
        IMethod method = methods.next();
        if (!method.isAbstract() && !method.isNative() && containsName(method.getSignature(), inclNames)) {
          IR ir = Jar2IR.getIR(m_walaAnalyzer, method.getSignature()); // getIR
          if (ir != null) {
            System.out.println("Finding all defs for: " + method.getSignature());
            getAllDefs(ir, 0, maxLookDepth, result);
          }
        }
      }        
    }
    return result;
  }

  // find all defs for ir and put them in result
  public void findAllDefs(IR ir, int maxLookDepth, DefAnalysisResult result) {
    if (ir != null && !ir.getMethod().isAbstract() && !ir.getMethod().isNative()) {
      System.out.println("Finding all defs for: " + ir.getMethod().getSignature());
      getAllDefs(ir, 0, maxLookDepth, result);
    }
  }
  
  private void getAllDefs(IR ir, int curDepth, int maxDepth, DefAnalysisResult result) {
    MethodMetaData methData = new MethodMetaData(ir);
    SSACFG cfg = methData.getcfg();
    
    List<ConditionalBranchDefs> currentCondBranchDefs = new ArrayList<ConditionalBranchDefs>();
    Hashtable<String, String[]> varMappings = new Hashtable<String, String[]>();
    
    // add method first in case there is no def in method
    result.addMethodDef(ir.getMethod());
    
    Hashtable<SSAInstruction, ISSABasicBlock> instBBMapping = new Hashtable<SSAInstruction, ISSABasicBlock>();
    SSAInstruction[] insts = getInstructions(ir, instBBMapping);
    for (int i = 0; i < insts.length; i++) {
      if (insts[i] == null) {
        continue;
      }
      
      // check if the current instruction is out of some conditional branches
      ISSABasicBlock currentBlock = instBBMapping.get(insts[i]);
      if (currentBlock != null) {
        for (int j = 0; j < currentCondBranchDefs.size(); j++) {
          ConditionalBranchDefs condBranchDefs = currentCondBranchDefs.get(j);
          if (condBranchDefs.endingBlock.getNumber() <= currentBlock.getNumber()) {
            currentCondBranchDefs.remove(j--);
          }
        }
      }
      
      if (insts[i] instanceof SSAPutInstruction) {
        SSAPutInstruction putfieldInst = (SSAPutInstruction) insts[i];
        
        List<String> varNames = new ArrayList<String>();
        if (putfieldInst.isStatic()) {
          varNames.add(putfieldInst.getDeclaredField().getDeclaringClass().getName().toString());
        }
        else {
          varNames.addAll(Arrays.asList(getVarName(methData, putfieldInst.getUse(0), varMappings)));
        }
        List<String> declaredFields = new ArrayList<String>();
        for (String varName : varNames) {
          // get the fieldType of the declared field of the putfield instruction
          String declaredField = "(" + putfieldInst.getDeclaredFieldType().getName() + ")";
          // get the class type that declared this field
          declaredField += varName;
          // get the name of the field
          declaredField += "." + putfieldInst.getDeclaredField().getName();
          declaredFields.add(declaredField);
        }
        // save field to all conditional branches
        result.addCondBranchDef(currentCondBranchDefs, declaredFields);
        // save field to method defs
        result.addMethodDef(ir.getMethod(), declaredFields);
      }
      else if (insts[i] instanceof SSANewInstruction) {
        SSANewInstruction newInst = (SSANewInstruction) insts[i];
        
        String def = getVarName(methData, newInst.getDef(), varMappings)[0];
        
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
              // get the fieldType of the declared field
              String declaredField = "(" + field.getFieldTypeReference().getName() + ")";
              // get the class type that declared this field
              declaredField += def;
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
        
        String[] arrayRefs = getVarName(methData, arrayStoreInst.getArrayRef(), varMappings);
        // save array ref to all conditional branches
        result.addCondBranchDef(currentCondBranchDefs, Arrays.asList(arrayRefs));
        // save array ref to method defs
        result.addMethodDef(ir.getMethod(), Arrays.asList(arrayRefs));
      }
      else if (insts[i] instanceof SSAInvokeInstruction && curDepth < maxDepth) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) insts[i];
        
        IR ir2 = Jar2IR.getIR(m_walaAnalyzer, invokeInst.getDeclaredTarget().getSignature());
        if (ir2 != null) {
          HashSet<String> defs = null;
          if (curDepth < maxDepth) {
            // try to get cached result first
            defs = result.getMethodDefs(ir2.getMethod());
            if (defs == null) {
              // recursively call getAllDefs
              DefAnalysisResult invokeResult = new DefAnalysisResult();
              getAllDefs(ir2, curDepth + 1, maxDepth, invokeResult);
              defs = invokeResult.getMethodDefs(ir2.getMethod());
            }
          }
          
          // translate result
          List<String> methodDefs = new ArrayList<String>();
          int count = invokeInst.getNumberOfParameters();
          for (int j = 0; j < count; j++) {
            String calleeVarName    = "v" + (j + 1);
            String[] callerVarNames = getVarName(methData, invokeInst.getUse(j), varMappings);
            
            if (defs != null) {
              for (String def : defs) {
                int index = def.indexOf(')');
                String defVarType = def.substring(0, index + 1);
                String defVarName = def.substring(index + 1);
                if (defVarName.equals(calleeVarName)) {
                  for (String callerVarName : callerVarNames) {
                    methodDefs.add(defVarType + callerVarName);
                  }
                }
                else if (defVarName.startsWith(calleeVarName + ".")) {
                  for (String callerVarName : callerVarNames) {
                    methodDefs.add(defVarType + callerVarName + defVarName.substring(defVarName.indexOf('.')));
                  }
                }                
                else if (defVarName.startsWith(calleeVarName + "[")) { // the index is not being translated
                  for (String callerVarName : callerVarNames) {
                    methodDefs.add(defVarType + callerVarName + defVarName.substring(defVarName.indexOf('[')));
                  }
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
            
            // it is possible that this conditional branch has no defs at 
            // all, so we always add it first at the beginning
            result.addCondBranchDef(condBranchDefs);
          }
        }
      }

      // add def to conditional branches and method
      if (insts[i].getNumberOfDefs() > 0) {
        String[] defs = getVarName(methData, insts[i].getDef(), varMappings);
        for (String def : defs) {
          if (def.startsWith("v") && !def.equals("v-1")) {
            result.addCondBranchDef(currentCondBranchDefs, def);
            result.addMethodDef(ir.getMethod(), def);
          }
        }
      }
      
      // handle variable transfer
      if (insts[i] instanceof SSAPiInstruction) {
        SSAPiInstruction piInst = (SSAPiInstruction) insts[i];
        String def    = getVarName(methData, piInst.getDef(), varMappings)[0];
        String[] vals = getVarName(methData, piInst.getVal(), varMappings);
        varMappings.put(def, vals);
      }
      else if (insts[i] instanceof SSAPhiInstruction) {
        SSAPhiInstruction phiInst = (SSAPhiInstruction) insts[i];
        String def = getVarName(methData, phiInst.getDef(), varMappings)[0];
        List<String> phiVarList = new ArrayList<String>();
        for (int j = 0, len = phiInst.getNumberOfUses(); j < len; j++) {
          String[] phiVars = getVarName(methData, phiInst.getUse(j), varMappings);
          phiVarList.addAll(Arrays.asList(phiVars));
        }
        varMappings.put(def, phiVarList.toArray(new String[0]));
      }
      else if (insts[i] instanceof SSACheckCastInstruction) {
        SSACheckCastInstruction checkcastInst = (SSACheckCastInstruction) insts[i];
        String def    = getVarName(methData, checkcastInst.getDef(), varMappings)[0];
        String[] vals = getVarName(methData, checkcastInst.getUse(0), varMappings);
        varMappings.put(def, vals);
      }
      else if (insts[i] instanceof SSAGetInstruction) {
        SSAGetInstruction getfieldInst = (SSAGetInstruction) insts[i];
        if (!getfieldInst.isStatic()) {
          String def    = getVarName(methData, getfieldInst.getDef(), varMappings)[0];
          String[] refs = getVarName(methData, getfieldInst.getUse(0), varMappings);
          List<String> declaredFields = new ArrayList<String>();
          for (String ref : refs) {
            String declaredField = ref + "." + getfieldInst.getDeclaredField().getName();
            declaredFields.add(declaredField);
          }
          varMappings.put(def, declaredFields.toArray(new String[0]));
        }
      }
      else if (insts[i] instanceof SSAArrayLoadInstruction) {
        SSAArrayLoadInstruction arrayLoadInst = (SSAArrayLoadInstruction) insts[i];
        String def           = getVarName(methData, arrayLoadInst.getDef(), varMappings)[0];
        String[] arrayRefs   = getVarName(methData, arrayLoadInst.getArrayRef(), varMappings);
        String[] arrayIndexs = getVarName(methData, arrayLoadInst.getIndex(), varMappings);
        
        List<String> arrayStrList = new ArrayList<String>();
        for (String arrayRef : arrayRefs) {
          for (String arrayIndex : arrayIndexs) {
            String[] arrayStrs = getVarName(methData, arrayRef + "[" + arrayIndex + "]", varMappings);
            arrayStrList.addAll(Arrays.asList(arrayStrs));
          }
        }
        varMappings.put(def, arrayStrList.toArray(new String[0]));
      }
      else if (insts[i] instanceof SSAArrayStoreInstruction) {
        SSAArrayStoreInstruction arrayStoreInst = (SSAArrayStoreInstruction) insts[i];
        String[] arrayRefs   = getVarName(methData, arrayStoreInst.getArrayRef(), varMappings);
        String[] arrayIndexs = getVarName(methData, arrayStoreInst.getIndex(), varMappings);
        String[] storeValues = getVarName(methData, arrayStoreInst.getValue(), varMappings);
        List<String> arrayStrList = new ArrayList<String>();
        for (String arrayRef : arrayRefs) {
          for (String arrayIndex : arrayIndexs) {
            String[] arrayStrs = getVarName(methData, arrayRef + "[" + arrayIndex + "]", varMappings);
            arrayStrList.addAll(Arrays.asList(arrayStrs));
          }
        }
        for (String arrayStr : arrayStrList) {
          String[] mapped = varMappings.get(arrayStr);
          if (mapped == null) {
            varMappings.put(arrayStr, storeValues);
          }
          else {
            List<String> values = new ArrayList<String>();
            values.addAll(Arrays.asList(mapped));
            values.addAll(Arrays.asList(storeValues));
            varMappings.put(arrayStr, values.toArray(new String[0]));
          }
        }
      }
    }
  }
  
  private String[] getVarName(MethodMetaData methData, int varID, Hashtable<String, String[]> varMapping) {
    String varName = getSymbol(varID, methData);
    String mapped[] = varMapping.get(varName);
    return mapped != null ? mapped : new String[]{varName};
  }
  
  private String[] getVarName(MethodMetaData methData, String varName, Hashtable<String, String[]> varMapping) {
    String[] mapped = varMapping.get(varName);
    return mapped != null ? mapped : new String[]{varName};
  }
  
  private SSAInstruction[] getInstructions(IR ir, Hashtable<SSAInstruction, ISSABasicBlock> instBBMapping) {
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
        instBBMapping.put(phiInst, basicBlock);
      }
      
      int index1 = basicBlock.getFirstInstructionIndex();
      int index2 = basicBlock.getLastInstructionIndex();
      if (index1 >= 0) {
        for (int j = index1; j <= index2; j++) {
          if (instSet[j] != null) {
            insts.add(instSet[j]);
            instBBMapping.put(instSet[j], basicBlock);
          }
        }
      }
      
      // add pi instructions
      Iterator<SSAPiInstruction> pis = basicBlock.iteratePis();
      while (pis.hasNext()) {
        SSAPiInstruction piInst = (SSAPiInstruction) pis.next();
        insts.add(piInst);
        instBBMapping.put(piInst, basicBlock);
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
  
  /**
   * @return if variable "varID" is a constant, return the prefixed 
   * string representing that constant, otherwise return vVarID
   */
  private String getSymbol(int varID, MethodMetaData methData) {
    String var = null;
    SymbolTable symbolTable = methData.getSymbolTable();
    
    if (varID >= 0 && symbolTable.isConstant(varID)) {
      Object constant = symbolTable.getConstantValue(varID);
      var = (constant != null) ? getConstantPrefix(varID, methData) + constant.toString() : "null";
    }
    else {
      var = "v" + varID;
    }
    return var;
  }
  
  private String getConstantPrefix(int varID, MethodMetaData methData) {
    if (!methData.getSymbolTable().isConstant(varID)) {
      return "";
    }
    else if (methData.getSymbolTable().isNumberConstant(varID)) {
      return "#!";
    }
    else if (methData.getSymbolTable().isStringConstant(varID)) {
      return "##";
    }
    else {
      return "#?";
    }
  }
  
  private boolean containsName(String name, List<String> inclNames) {
    boolean ret = false;
    
    for (int i = 0, size = inclNames.size(); i < size; i++) {
      if (name.startsWith(inclNames.get(i))) {
        ret = true;
        break;
      }
    }
    return ret;
  }
  
  public static void main(String[] args) throws Exception {
    List<String> inclNames = new ArrayList<String>();
    inclNames.add("test_program.");
//    DefAnalyzer defAnalyzer = new DefAnalyzer("./test_programs/test_program.jar");
//    DefAnalysisResult result = defAnalyzer.findAllDefs(5, inclNames);
//  
//    Collection<ConditionalBranchDefs> condDefs = result.m_defsForCondBranch.values();
//    for (ConditionalBranchDefs defs : condDefs) {
//      for (String def : defs.defs) {
//        System.out.println(def);
//      }
//      System.out.println("================================");
//    }
    
//    IR ir = Jar2IR.getIR(defAnalyzer.m_walaAnalyzer, "test_program.func1", 7);
//    System.out.println("Defs for method: " + ir.getMethod().getName().toString());
//    Collection<String> defs = result.getMethodDefs(ir.getMethod());
//    for (String def : defs) {
//      System.out.println(def);
//    }
  }
  
  private final WalaAnalyzer m_walaAnalyzer;
}
