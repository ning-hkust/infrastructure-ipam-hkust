package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.BBorInstInfo;
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.Wala.WalaUtils;

import java.util.Hashtable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SymbolTable;


public abstract class AbstractForwardHandler extends AbstractHandler {
  
  protected void cinitClass(String className, Formula preCond, BBorInstInfo instInfo) {
    IClass cls = WalaUtils.getClass(instInfo.executor.getWalaAnalyzer(), className);
    if (cls != null) {
      IMethod clinit = null;
      for (IMethod method : cls.getAllMethods()) {
        if (method.getName().toString().contains("<clinit>")) {
          clinit = method;
          break;
        }
      }

      if (clinit != null) {
        StringBuilder invocation = new StringBuilder();
        invocation.append(clinit.getSignature());
        invocation.append("_");
        invocation.append(System.nanoTime());
        
        // check for previous cinits
        boolean cinited = false;
        Hashtable<String, Reference> refMap = preCond.getRefMap().get("");
        if (refMap != null) {
          String clinitSig = clinit.getSignature();
          for (String key : refMap.keySet()) {
            if (key.startsWith(clinitSig)) {
              cinited = true;
              break;
            }
          }
        }
        
        if (!cinited) {
          Instance instance = new Instance(instInfo.callSites, instInfo.currentBB);
          // static field also goes to "" callSites
          Reference fieldRef = new Reference(invocation.toString(), "V", "", instance, null, true); 
          addRefToRefMap(preCond.getRefMap(), fieldRef);
        }
      }
    }
  }
  
  /**
   * @return if variable "varID" is a constant, return the prefixed 
   * string representing that constant, otherwise return vVarID
   */
  public String getSymbol(int varID, MethodMetaData methData, String callSites, 
      Hashtable<String, Hashtable<String, Integer>> defCountMap, boolean forDef) {
    
    String var = null;
    SymbolTable symbolTable = methData.getSymbolTable();
    
    if (varID >= 0 && symbolTable.isConstant(varID)) {
      Object constant = symbolTable.getConstantValue(varID);
      var = (constant != null) ? getConstantPrefix(varID, methData) + constant.toString() : "null";
    }
    else {
      var = "v" + varID;

      // add defCount information
      Hashtable<String, Integer> methodDefCounts = defCountMap.get(callSites);
      if (methodDefCounts != null) {
        Integer defCount = methodDefCounts.get(var);
        if (defCount != null) {
          int num = (defCount.intValue() - (forDef ? 0 : 1));
          if (num > 0) {
            var += "@" + num;
          }
        }
      }
    }
    return var;
  }
}
