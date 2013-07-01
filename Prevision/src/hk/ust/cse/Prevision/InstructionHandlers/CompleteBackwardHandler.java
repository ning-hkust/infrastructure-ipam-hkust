package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.BBorInstInfo;
import hk.ust.cse.Prevision_PseudoImpl.PseudoImplMap;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import javax.naming.TimeLimitExceededException;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IComparisonInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IShiftInstruction;
import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG.ExceptionHandlerBasicBlock;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class CompleteBackwardHandler extends AbstractBackwardHandler {
  
  public Formula handle_arraylength(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAArrayLengthInstruction arrayLengthInst                 = (SSAArrayLengthInstruction) inst;

    // the variable(result) define by the arraylength instruction
    String def      = getSymbol(arrayLengthInst.getDef(), methData, callSites, newDefMap);
    String arrayRef = getSymbol(arrayLengthInst.getArrayRef(), methData, callSites, newDefMap);
    
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      Reference arrayRefRef = findOrCreateReference(arrayRef, "Unknown-Type", callSites, currentBB, postCond);
      Reference nullRef     = findOrCreateReference("null", "", "", currentBB, postCond);
      
      // get the array length field
      List<Reference> lenRefs = arrayRefRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {
        Reference lenRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, lenRef.getInstances(), true, false);
        lenRefs = arrayRefRef.getFieldReferences("length");
      }
      Reference arrayLenRef = lenRefs.get(0); // simply use the first one
      Reference zeroRef     = findOrCreateReference("#!0", "I", "", currentBB, postCond);

      // additional condition to make sure: array.length >= 0
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayLenRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      // new condition: arrayRef != null
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, arrayRefRef);
      
      if (containsRef(def, callSites, newRefMap)) {
        // add new references to refMap
        Reference defRef = findOrCreateReference(def, "I", callSites, currentBB, postCond);
        
        Collection<Instance> defInstances = defRef.getInstances();
        List<Reference> fieldRefs = arrayRefRef.getFieldReferences("length");
        if (fieldRefs.size() == 0) {
          // set to the first instance
          Instance instance = arrayRefRef.getInstance();
          instance.setField("length", "I", callSites, defInstances, true, false);
          // update field instances values
          instance.getField("length").updateFieldInstancesValue(defInstances, postCond);
        }
        else {
          for (Reference fieldRef : fieldRefs) {
            try {
              fieldRef.assignInstance(defInstances, true);
              // update field instances values
              fieldRef.updateFieldInstancesValue(defInstances, postCond);
            } catch (Exception e) {e.printStackTrace();}
          }
        }
        defRef.putInstancesToOld();
        // defRef not longer useful
        if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
          newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
          // no need to fieldRef as we can access it through arrayRefRef
        }

        // since there is a new def, add to defMap
        addDefToDefMap(newDefMap, defRef);
      }
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: arrayRef == null
      arrayRefRef = findOrCreateReference(arrayRef, "Unknown-Type", callSites, currentBB, postCond);
      nullRef     = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, arrayRefRef);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  public Formula handle_arrayload(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAArrayLoadInstruction arrayLoadInst                     = (SSAArrayLoadInstruction) inst;

    // the variable(result) define by the arrayload instruction
    String def        = getSymbol(arrayLoadInst.getDef(), methData, callSites, newDefMap);
    String arrayRef   = getSymbol(arrayLoadInst.getArrayRef(), methData, callSites, newDefMap);
    String arrayIndex = getSymbol(arrayLoadInst.getIndex(), methData, callSites, newDefMap);

    String elemType   = arrayLoadInst.getElementType().getName().toString();
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      Reference arrayRefRef   = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, postCond);
      Reference arrayIndexRef = findOrCreateReference(arrayIndex, "I", callSites, currentBB, postCond);

      // add new references to refMap
      addRefToRefMap(newRefMap, arrayRefRef);
      addRefToRefMap(newRefMap, arrayIndexRef);
      
      Reference defRef = findOrCreateReference(def, elemType, callSites, currentBB, postCond);

      // merge with the same read appearing later
      Relation relation = postCond.getRelation("@@array");
      Reference laterReadRef = null;
      for (int i = 0, size = relation.getFunctionCount(); i < size; i++) {
        Instance[] domains = relation.getDomainValues().get(i);
        if (relation.isUpdate(i)) { // update
          if (domains[0].getLastReference() != null && domains[0].getLastRefName().equals("value") && 
              domains[0].getLastReference().getDeclaringInstance() != null && 
              domains[0].getLastReference().getDeclaringInstance().isConstant()) {
            continue;
          }
          break;
        }
        else {
          if (arrayRefRef.getInstances().contains(domains[0]) && 
              arrayIndexRef.getInstances().contains(domains[1])) {
            laterReadRef = newRefMap.get("").get("read_@@array_" + relation.getFunctionTimes().get(i));
            break;
          }
        }
      }
      
      Reference readRef = relation.read(new Instance[] {arrayRefRef.getInstance(), 
                                                        arrayIndexRef.getInstance()}, elemType, currentBB);
      addRefToRefMap(newRefMap, readRef); // necessary for deepClone
      
      // associate the two refs' instance together as the same one
      if (laterReadRef != null) {
        assignInstance(laterReadRef, readRef, newRefMap, newDefMap);
      }
      assignInstance(defRef, readRef, newRefMap, newDefMap);

      // new conditions: arrayIndex >= 0 && arrayIndex < arrayLength
      Reference zeroRef = findOrCreateReference("#!0", "I", "", currentBB, postCond);

      // get the array length field
      List<Reference> lenRefs = arrayRefRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {
        Reference lenRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, lenRef.getInstances(), true, false);
        lenRefs = arrayRefRef.getFieldReferences("length");
      }
      Reference arrayLenRef = lenRefs.get(0); // simply use the first one
      
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, arrayLenRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      // new condition: arrayRef != null
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      break;
//    case Formula.EXCEPTIONAL_SUCCESSOR:
//      TypeReference excepType = methData.getExceptionType(instInfo.currentBB, instInfo.previousBB);
//      String excepTypeStr = null;//(excepType != null) ? excepType.getName().toString() : 
//                                 //                 findCaughtExceptionTypeStr(postCond);
//      
//      if (excepTypeStr != null) {
//        if (excepTypeStr.equals("Ljava/lang/NullPointerException")) {
//          // new condition: arrayRef == null
//          arrayRefRef = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
//          nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
//          conditionList.add(new Condition(conditionTerms));
//          
//          // add new references to refMap
//          addRefToRefMap(newRefMap, arrayRefRef);
//          
//          // set caught variable into triggered variable, 
//          // indicating the caught exception is trigger by the instruction
//          //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
//        }
//        else if (excepTypeStr.equals("Ljava/lang/ArrayIndexOutOfBoundsException")) {
//          // new condition: arrayRef != null && (arrayIndex < 0 || arrayIndex >= arrayLength)
//          arrayRefRef    = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
//          nullRef        = findOrCreateReference("null", "", "", currentBB, newRefMap);
//          arrayIndexRef  = findOrCreateReference(arrayIndex, "I", callSites, currentBB, newRefMap);
//          arrayLenRef    = findOrCreateReference(arrayRef + ".length", "I", callSites, currentBB, newRefMap);
//          zeroRef        = findOrCreateReference("#!0", "I", "", currentBB, newRefMap);
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance()));
//          conditionList.add(new Condition(conditionTerms));
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, zeroRef.getInstance()));
//          conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, arrayLenRef.getInstance()));
//          conditionList.add(new Condition(conditionTerms));
//          
//          // add new references to refMap
//          addRefToRefMap(newRefMap, arrayRefRef);
//          addRefToRefMap(newRefMap, arrayIndexRef);
//          addRefToRefMap(newRefMap, arrayLenRef);
//          
//          // set caught variable into triggered variable, 
//          // indicating the caught exception is trigger by the instruction
//          //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ArrayIndexOutOfBoundsException");
//        }
//        else {
//          // cannot decide which kind of exception it is!
//        }
//      }
//      else {
//        System.err.println("Failed to obtain the exception type string!");
//      }
//      break;
    }
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  
  public Formula handle_arraystore(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAArrayStoreInstruction arrayStoreInst                   = (SSAArrayStoreInstruction) inst;

    String arrayRef   = getSymbol(arrayStoreInst.getArrayRef(), methData, callSites, postCond.getDefMap());
    String arrayIndex = getSymbol(arrayStoreInst.getIndex(), methData, callSites, postCond.getDefMap());
    String storeValue = getSymbol(arrayStoreInst.getValue(), methData, callSites, postCond.getDefMap());
    
    String elemType   = arrayStoreInst.getElementType().getName().toString();
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      Reference arrayRefRef   = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, postCond);
      Reference arrayIndexRef = findOrCreateReference(arrayIndex, "I", callSites, currentBB, postCond);

      // add new references to refMap
      addRefToRefMap(newRefMap, arrayRefRef);
      addRefToRefMap(newRefMap, arrayIndexRef);

      Reference storeValRef = findOrCreateReference(storeValue, elemType, callSites, currentBB, postCond);

      Relation relation = postCond.getRelation("@@array");
      relation.update(new Instance[] {arrayRefRef.getInstance(), arrayIndexRef.getInstance()}, storeValRef.getInstance());
      addRefToRefMap(newRefMap, storeValRef);
      
      // new conditions: arrayIndex >= 0 && arrayIndex < arrayLength
      Reference zeroRef = findOrCreateReference("#!0", "I", "", currentBB, postCond);

      // get the array length field
      List<Reference> lenRefs = arrayRefRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {
        Reference lenRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, lenRef.getInstances(), true, false);
        lenRefs = arrayRefRef.getFieldReferences("length");
      }
      Reference arrayLenRef = lenRefs.get(0); // simply use the first one
      
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, arrayLenRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      // new condition: arrayRef != null
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      break;
//    case Formula.EXCEPTIONAL_SUCCESSOR:
//      TypeReference excepType = methData.getExceptionType(instInfo.currentBB, instInfo.previousBB);
//      String excepTypeStr = null;//(excepType != null) ? excepType.getName().toString() : 
//                                 //                 findCaughtExceptionTypeStr(postCond);
//      
//      if (excepTypeStr != null) {
//        if (excepTypeStr.equals("Ljava/lang/NullPointerException")) {     
//          // new condition: arrayRef == null
//          arrayRefRef = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
//          nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
//          conditionList.add(new Condition(conditionTerms));
//          
//          // add new references to refMap
//          addRefToRefMap(newRefMap, arrayRefRef);
//          
//          // set caught variable into triggered variable, 
//          // indicating the caught exception is trigger by the instruction
//          //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
//        }
//        else if (excepTypeStr.equals("Ljava/lang/ArrayIndexOutOfBoundsException")) {    
//          // new condition: arrayRef != null && (arrayIndex < 0 || arrayIndex >= arrayLength)
//          arrayRefRef    = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
//          nullRef        = findOrCreateReference("null", "", "", currentBB, newRefMap);
//          arrayIndexRef  = findOrCreateReference(arrayIndex, "I", callSites, currentBB, newRefMap);
//          arrayLenRef    = findOrCreateReference(arrayRef + ".length", "I", callSites, currentBB, newRefMap);
//          zeroRef        = findOrCreateReference("#!0", "I", "", currentBB, newRefMap);
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance()));
//          conditionList.add(new Condition(conditionTerms));
//          conditionTerms = new ArrayList<ConditionTerm>();
//          conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, zeroRef.getInstance()));
//          conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, arrayLenRef.getInstance()));
//          conditionList.add(new Condition(conditionTerms));
//          
//          // add new references to refMap
//          addRefToRefMap(newRefMap, arrayRefRef);
//          addRefToRefMap(newRefMap, arrayIndexRef);
//          addRefToRefMap(newRefMap, arrayLenRef);
//          
//          // set caught variable into triggered variable, 
//          // indicating the caught exception is trigger by the instruction
//          //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ArrayIndexOutOfBoundsException");
//        }
//        else {
//          // cannot decide which kind of exception it is!
//        }
//      }
//      else {
//        System.err.println("Failed to obtain the exception type string!");
//      }
//      break;
    } 
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  public Formula handle_binaryop(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSABinaryOpInstruction binaryOpInst                       = (SSABinaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction    
    String def  = getSymbol(binaryOpInst.getDef(), methData, callSites, newDefMap);
    String var1 = getSymbol(binaryOpInst.getUse(0), methData, callSites, newDefMap);
    String var2 = getSymbol(binaryOpInst.getUse(1), methData, callSites, newDefMap);
    
    if (containsRef(def, callSites, newRefMap)) {
      Reference defRef = findOrCreateReference(def, "Unknown-Type", callSites, currentBB, postCond); // reference must exist
      Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, postCond);
      Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, postCond);
      
      Instance binaryOp = null;
      IBinaryOpInstruction.IOperator operator = binaryOpInst.getOperator();
      if (operator instanceof IBinaryOpInstruction.Operator) {
        switch ((IBinaryOpInstruction.Operator) operator) {
        case ADD:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.ADD, var2Ref.getInstance(), currentBB);
          break;
        case AND:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.AND, var2Ref.getInstance(), currentBB);
          break;
        case DIV:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.DIV, var2Ref.getInstance(), currentBB);
          break;
        case MUL:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.MUL, var2Ref.getInstance(), currentBB);
          break;
        case OR:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.OR, var2Ref.getInstance(), currentBB);
          break;
        case REM:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.REM, var2Ref.getInstance(), currentBB);
          break;
        case SUB:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.SUB, var2Ref.getInstance(), currentBB);
          break;
        case XOR:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.XOR, var2Ref.getInstance(), currentBB);
          break;
        }
      }
      else if (operator instanceof IShiftInstruction.Operator) {
        switch ((IShiftInstruction.Operator) operator) {
        case SHL:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.SHL, var2Ref.getInstance(), currentBB);
          break;
        case SHR:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.SHR, var2Ref.getInstance(), currentBB);
          break;
        case USHR:
          binaryOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.USHR, var2Ref.getInstance(), currentBB);
          break;
        }
      }
      
      // add new references to refMap
      addRefToRefMap(newRefMap, var1Ref);
      addRefToRefMap(newRefMap, var2Ref);
      
      // assign the instance to the def reference
      assignInstance(defRef, binaryOp, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }
  
  // handler for catch instruction
  public Formula handle_catch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAGetCaughtExceptionInstruction catchInst                = 
      ((ExceptionHandlerBasicBlock) instInfo.currentBB).getCatchInstruction();

    // the e defined by catch
    String def = getSymbol(catchInst.getDef(), methData, callSites, newDefMap);

    // get the declared type of the exception
    TypeReference excepType = methData.getExceptionType(instInfo.currentBB);
    String excepTypeStr = excepType.getName().toString();
    
    // create new reference of def
    Reference defRef = findOrCreateReference(def, excepTypeStr, callSites, currentBB, postCond);

    // create new instance of e
    long instanceID  = System.nanoTime();
    String freshInst = "FreshInstanceOf(" + excepTypeStr + "_" + instanceID + ")";
    Instance excep   = new Instance(freshInst, excepTypeStr, currentBB);
    
    // assign the instance to the def reference
    assignInstance(defRef, excep, newRefMap, newDefMap);
    
    // add a caught variable to indicate "coming from a catch block of 
    // some exception type", and expect to meet an exception triggering point
    //newVarMap = setExceptionCaught(postCond, newVarMap, excepTypeStr);

    return new Formula(postCond);
  }
  
  // handler for checkcast instruction
  public Formula handle_checkcast(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap(); 
    SSACheckCastInstruction checkcastInst                     = (SSACheckCastInstruction) inst;

    // the variable(result) define by the checkcast instruction
    String def = getSymbol(checkcastInst.getDef(), methData, callSites, newDefMap);
    String val = getSymbol(checkcastInst.getUse(0), methData, callSites, newDefMap);
    String declaredResultType = checkcastInst.getDeclaredResultType().getName().toString();

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: subTypeStr == true || val == null
      Reference valRef  = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, postCond);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new TypeConditionTerm(
          valRef.getInstance(), TypeConditionTerm.Comparator.OP_INSTANCEOF, declaredResultType)); 
      conditionTerms.add(new BinaryConditionTerm(valRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, valRef);
      
      // create new reference of def
      Reference defRef = findOrCreateReference(def, declaredResultType, callSites, currentBB, postCond);

      // associate the two refs' instance together as the same one
      assignInstance(defRef, valRef, newRefMap, newDefMap);
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be CCE */
      // new condition: val != null && subTypeStr == false
      valRef     = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, postCond);
      nullRef    = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(valRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new TypeConditionTerm(
          valRef.getInstance(), TypeConditionTerm.Comparator.OP_NOT_INSTANCEOF, declaredResultType)); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, valRef);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ClassCastException");
      break;
    }
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  
  public Formula handle_compare(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAComparisonInstruction compareInst                      = (SSAComparisonInstruction) inst;

    // the variable(result) define by the compare instruction    
    String def  = getSymbol(compareInst.getDef(), methData, callSites, newDefMap);
    String var1 = getSymbol(compareInst.getUse(0), methData, callSites, newDefMap);
    String var2 = getSymbol(compareInst.getUse(1), methData, callSites, newDefMap);
    
    if (containsRef(def, callSites, newRefMap)) {   
      Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, postCond);
      Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, postCond);
      Reference defRef  = findOrCreateReference(def, "Unknown-Type", callSites, currentBB, postCond); // reference must exist
      
      Instance compareOp = null;
      switch ((IComparisonInstruction.Operator) compareInst.getOperator()) {
      case CMP:   /* for long */
      case CMPL:  /* for float or double */
      case CMPG:  /* for float or double */
        compareOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.SUB, var2Ref.getInstance(), currentBB);
        break;
      }
      
      // add new references to refMap
      addRefToRefMap(newRefMap, var1Ref);
      addRefToRefMap(newRefMap, var2Ref);

      // assign the instance to the def reference
      assignInstance(defRef, compareOp, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }

  public Formula handle_conversion(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAConversionInstruction convInst                         = (SSAConversionInstruction) inst;

    // the variable(result) define by the conversion instruction
    String toVal    = getSymbol(convInst.getDef(), methData, callSites, newDefMap);
    String fromVal  = getSymbol(convInst.getUse(0), methData, callSites, newDefMap);
    String fromType = convInst.getFromType().getName().toString();
    String toType   = convInst.getToType().getName().toString();    

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    if (containsRef(toVal, callSites, newRefMap)) {
      Reference toValRef   = findOrCreateReference(toVal, toType, callSites, currentBB, postCond);
      Reference fromValRef = findOrCreateReference(fromVal, fromType, callSites, currentBB, postCond);
      
      if (fromType.equals("I") || fromType.equals("J") || fromType.equals("S")) { // from integer to float or integer
        // associate the two refs' instance together as the same one
        assignInstance(toValRef, fromValRef, newRefMap, newDefMap);
      }
      else if (fromType.equals("D") || fromType.equals("F")) {
        if (toType.equals("I") || toType.equals("J") || toType.equals("S")) { // from float to integer
          
          Reference convValRef = null;
          if (fromVal.startsWith("#!")) { // it is a constant number
            int index = fromVal.lastIndexOf('.');
            String convVal = (index >= 0) ? fromVal.substring(0, index) : fromVal;
            convValRef = findOrCreateReference(convVal, "I", callSites, currentBB, postCond);
          }
          else {
            // create a converted val
            String convVal = fromVal + "$1" /* first kind of conversion */;
            
            convValRef = findOrCreateReference(convVal, "I", callSites, currentBB, postCond);
            if (containsRef(convVal, callSites, newRefMap)) {
              // the converted integer should be: fromVal - 1 < convVal <= fromVal
              conditionTerms = new ArrayList<ConditionTerm>();
              conditionTerms.add(new BinaryConditionTerm(convValRef.getInstance(), 
                  Comparator.OP_SMALLER_EQUAL, fromValRef.getInstance())); 
              conditionList.add(new Condition(conditionTerms));
              
              Instance instance = new Instance(convValRef.getInstance(), 
                  INSTANCE_OP.ADD, new Instance("#!1", "I", currentBB), currentBB);
              conditionTerms = new ArrayList<ConditionTerm>();
              conditionTerms.add(new BinaryConditionTerm(instance, Comparator.OP_GREATER, fromValRef.getInstance())); 
              conditionList.add(new Condition(conditionTerms));

              // add new references to refMap
              addRefToRefMap(newRefMap, fromValRef);
              addRefToRefMap(newRefMap, convValRef);
            }
          }
          // associate the two refs' instance together as the same one
          assignInstance(toValRef, convValRef, newRefMap, newDefMap);
        }
        else if (toType.equals("D") || toType.equals("F")) { // from float to float
          // associate the two refs' instance together as the same one
          assignInstance(toValRef, fromValRef, newRefMap, newDefMap);
        }
      }
      else {
        // not implement
      }
    }
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  
  public Formula handle_conditional_branch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAConditionalBranchInstruction condBranchInst            = (SSAConditionalBranchInstruction) inst;
    
    // check whether or not the conditional branch has been taken
    // the branch instruction will always be the last instruction
    // of the current block, so we can check whether the branch has
    // been taken or not by checking the successor bb number
    boolean tookBranch = true;
    if (instInfo.previousBB == null) {
      // the first analyzing statement is a conditional statement,
      // we have no idea if the branch is taken or not
      tookBranch = false;
    }
    else if (instInfo.currentBB.getNumber() + 1 == instInfo.previousBB.getNumber()) {
      tookBranch = false;
    }

    // get the variables of the conditional branch,  
    // the variables might be constant numbers!
    String var1 = getSymbol(condBranchInst.getUse(0), methData, callSites, postCond.getDefMap());
    String var2 = getSymbol(condBranchInst.getUse(1), methData, callSites, postCond.getDefMap());
    
    Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, postCond);
    Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, postCond);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();

    // create conditional branch condition
    ConditionTerm term = null;
    Instance instance1 = var1Ref.getInstance();
    Instance instance2 = var2Ref.getInstance();
    switch ((IConditionalBranchInstruction.Operator) condBranchInst.getOperator()) {
    case EQ:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_INEQUAL, instance2);
      }
      break;
    case GE:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_GREATER_EQUAL, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_SMALLER, instance2);
      }
      break;
    case GT:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_GREATER, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_SMALLER_EQUAL, instance2);
      }
      break;
    case LE:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_SMALLER_EQUAL, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_GREATER, instance2);
      }
      break;
    case LT:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_SMALLER, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_GREATER_EQUAL, instance2);
      }
      break;
    case NE:
      if (tookBranch) {
        term = new BinaryConditionTerm(instance1, Comparator.OP_INEQUAL, instance2);
      }
      else {
        term = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);
      }
      break;
    }
    
    conditionTerms = new ArrayList<ConditionTerm>();
    conditionTerms.add(term); 
    conditionList.add(new Condition(conditionTerms));
    
    // add new references to refMap
    addRefToRefMap(newRefMap, var1Ref);
    addRefToRefMap(newRefMap, var2Ref);

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  public Formula handle_conditional_branch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  // handler for getfield instruction
  public Formula handle_getfield(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAGetInstruction getfieldInst                            = (SSAGetInstruction) inst;

    // the variable(result) define by the getfield instruction
    String def = getSymbol(getfieldInst.getDef(), methData, callSites, newDefMap);
    String ref = getSymbol(getfieldInst.getUse(0), methData, callSites, newDefMap);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:  
      // new condition: ref != null
      String refTypeName = getfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      Reference refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, postCond);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
      
      if (containsRef(def, callSites, newRefMap)) {
        // add new references to refMap
        String fieldType = getfieldInst.getDeclaredFieldType().getName().toString();
        String fieldName = getfieldInst.getDeclaredField().getName().toString();
        Reference defRef = findOrCreateReference(def, fieldType, callSites, currentBB, postCond);

        // since there is a new def, add to defMap
        addDefToDefMap(newDefMap, defRef);
        
        Collection<Instance> defInstances = defRef.getInstances();
        List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
        if (fieldRefs.size() == 0) {
          // set to the first instance
          Instance instance = refRef.getInstance();
          instance.setField(fieldName, fieldType, callSites, defInstances, true, false);
          // update field instances values
          instance.getField(fieldName).updateFieldInstancesValue(defInstances, postCond);
        }
        else {
          for (Reference fieldRef : fieldRefs) {
            try {
              fieldRef.assignInstance(defInstances, true);
              // update field instances values
              fieldRef.updateFieldInstancesValue(defInstances, postCond);
            } catch (Exception e) {e.printStackTrace();}
          }
        }
        defRef.putInstancesToOld();
        
        // defRef not longer useful
        if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
          newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
          // no need to fieldRef as we can access it through arrayRefRef
        }
      }
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: ref == null
      refTypeName = getfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, postCond);
      nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }
    
    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  // handler for getstatic instruction
  public Formula handle_getstatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAGetInstruction getstaticInst                           = (SSAGetInstruction) inst;

    String def = getSymbol(getstaticInst.getDef(), methData, callSites, newDefMap);

    if (containsRef(def, callSites, newRefMap)) {
      String fieldType = getstaticInst.getDeclaredFieldType().getName().toString();
      // get the class type that declared this field
      String declaredField = getstaticInst.getDeclaredField().getDeclaringClass().getName().toString();
      // get the name of the field
      declaredField += "." + getstaticInst.getDeclaredField().getName();
  
      // add new references to refMap
      Reference defRef   = findOrCreateReference(def, fieldType, callSites, currentBB, postCond);
      Reference fieldRef = findOrCreateReference(declaredField, fieldType, "", currentBB, postCond); // static field also goes to "" callSites
      
      // associate the two refs' instance together as the same one
      assignInstance(defRef, fieldRef, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }

  public Formula handle_goto(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  // handler for instanceof instruction
  public Formula handle_instanceof(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAInstanceofInstruction instanceofInst                   = (SSAInstanceofInstruction) inst;

    String def = getSymbol(instanceofInst.getDef(), methData, callSites, newDefMap);
    String ref = getSymbol(instanceofInst.getRef(), methData, callSites, newDefMap);

    // the variable define by the instanceofInst instruction
    if (containsRef(def, callSites, newRefMap)) {
      Reference refRef  = findOrCreateReference(ref, "Unknown-Type", callSites, currentBB, postCond);
      // add new references to refMap
      String fieldName = "__instanceof__" + instanceofInst.getCheckedType().getName();
      Reference defRef = findOrCreateReference(def, "Z", callSites, currentBB, postCond);

      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
      
      Collection<Instance> defInstances = defRef.getInstances();
      List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
      if (fieldRefs.size() == 0) {
        // set to the first instance
        Instance instance = refRef.getInstance();
        instance.setField(fieldName, "Z", callSites, defInstances, true, false);
        // update field instances values
        instance.getField(fieldName).updateFieldInstancesValue(defInstances, postCond);
      }
      else {
        for (Reference fieldRef : fieldRefs) {
          try {
            fieldRef.assignInstance(defInstances, true);
            // update field instances values
            fieldRef.updateFieldInstancesValue(defInstances, postCond);
          } catch (Exception e) {e.printStackTrace();}
        }
      }
      defRef.putInstancesToOld();
      
      // defRef not longer useful
      if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
        newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
        // no need to fieldRef as we can access it through arrayRefRef
      }
    }

    return new Formula(postCond);
  }
  
  public Formula handle_invokeinterface(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  public Formula handle_invokevirtual(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  public Formula handle_invokespecial(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }
  
  private Formula handle_invokenonstatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAInvokeInstruction invokeInst                           = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterface/invokespecial/invokevirtual instruction
    String def = getSymbol(invokeInst.getDef(), methData, callSites, newDefMap);
    String ref = getSymbol(invokeInst.getUse(0), methData, callSites, newDefMap);
    
    List<String> params = new ArrayList<String>();
    int count = invokeInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(getSymbol(invokeInst.getUse(i), methData, callSites, newDefMap));
    }
    
    String refType = invokeInst.getDeclaredTarget().getDeclaringClass().getName().toString();

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: ref != null
      Reference refRef  = findOrCreateReference(ref, refType, callSites, currentBB, postCond);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);

      // the variable define by the invokeinterface/invokespecial/invokevirtual instruction
      if (containsRef(def, callSites, newRefMap)) { // similar to getfield
        // add new references to refMap
        String invocationType = invokeInst.getDeclaredResultType().getName().toString();
        StringBuilder invocation = new StringBuilder();
        // get the name of the field
        invocation.append(invokeInst.getDeclaredTarget().getSelector().getName());
        // get the parameters
        invocation.append("(");
        for (int i = 0; i < params.size(); i++) {
          invocation.append(params.get(i));
          if (i != params.size() - 1) {
            invocation.append(", ");
          }
        }
        invocation.append(")");
        
        Reference defRef = findOrCreateReference(def, invocationType, callSites, currentBB, postCond);

        // since there is a new def, add to defMap
        addDefToDefMap(newDefMap, defRef);
        
        Collection<Instance> defInstances = defRef.getInstances();
        List<Reference> fieldRefs = refRef.getFieldReferences(invocation.toString());
        if (fieldRefs.size() == 0) {
          // set to the first instance
          Instance instance = refRef.getInstance();
          instance.setField(invocation.toString(), invocationType, callSites, defInstances, true, false);
        }
        else {
          for (Reference fieldRef : fieldRefs) {
            try {
              fieldRef.assignInstance(defInstances, true);
            } catch (Exception e) {e.printStackTrace();}
          }
        }
        defRef.putInstancesToOld();
        
        // defRef not longer useful
        if (findReference(defRef.getName(), defRef.getCallSites(), newRefMap) != null) {
          newRefMap.get(defRef.getCallSites()).remove(defRef.getName());
        }
      }
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: arrayRef == null
      refRef  = findOrCreateReference(ref, refType, callSites, currentBB, postCond);
      nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  // simple implementation, do not consider call graph
  public Formula handle_invokestatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAInvokeInstruction invokestaticInst                     = (SSAInvokeInstruction) inst;

    String def = getSymbol(invokestaticInst.getDef(), methData, callSites, newDefMap);
    
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(getSymbol(invokestaticInst.getUse(i), methData, callSites, newDefMap));
    }

    // the variable define by the invokestatic instruction
    MethodReference declTarget = invokestaticInst.getDeclaredTarget();
    String invocationType = invokestaticInst.getDeclaredResultType().getName().toString();
    if (containsRef(def, callSites, newRefMap)) {
      if (declTarget.getSignature().startsWith(("java.lang.Class.forName(Ljava/lang/String;"))) {
        handleClassForName(def, params.get(0), postCond, newRefMap, callSites, currentBB);
      }
      
      StringBuilder invocation = new StringBuilder();
      // get the fieldType of the declared field of the invokestatic instruction
      // get the class type that declared this field
      invocation.append(declTarget.getDeclaringClass().getName());
      // get the name of the field
      invocation.append("." + declTarget.getSelector().getName());
      // get the parameters
      invocation.append("(");
      for (int i = 0; i < params.size(); i++) {
        invocation.append(params.get(i));
        if (i != params.size() - 1) {
          invocation.append(", ");
        }
      }
      invocation.append(")");
      
      // create new reference of def
      Reference invocationRef = findOrCreateReference(invocation.toString(), invocationType, callSites, currentBB, postCond);
      Reference defRef        = findOrCreateReference(def, invocationType, callSites, currentBB, postCond);

      // associate the two refs' instance together as the same one
      assignInstance(defRef, invocationRef, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }
  
  public Formula handle_invokeinterface_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, postCond, inst, instInfo, callStack, curInvokeDepth);
  }

  public Formula handle_invokevirtual_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, postCond, inst, instInfo, callStack, curInvokeDepth);
  }

  public Formula handle_invokespecial_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, postCond, inst, instInfo, callStack, curInvokeDepth);
  }
  
  // go into invocation
  private Formula handle_invokenonstatic_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    Formula preCond                                           = null;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Formula newPostCond                                       = postCond.clone();
    Hashtable<String, Hashtable<String, Reference>> newRefMap = newPostCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = newPostCond.getDefMap();
    SSAInvokeInstruction invokeInst                           = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterface/invokevirtual/invokespecial instruction
    String def = getSymbol(invokeInst.getDef(), methData, callSites, newDefMap);
    String ref = getSymbol(invokeInst.getUse(0), methData, callSites, newDefMap);
    
    List<String> params = new ArrayList<String>();
    int count = invokeInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(getSymbol(invokeInst.getUse(i), methData, callSites, newDefMap));
    }

    String refType = invokeInst.getDeclaredTarget().getDeclaringClass().getName().toString();

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // check if method name is in the filter list, if so, not step in
      if (isMethodSigFiltered(invokeInst.getDeclaredTarget().getSignature())) {
        return handle_invokenonstatic(postCond, inst, instInfo); // use not step in version
      }
      
      Reference refRef  = findOrCreateReference(ref, refType, callSites, currentBB, newPostCond);
      Reference nullRef = null;

      String invocationType = invokeInst.getDeclaredResultType().getName().toString();
      Reference defRef = findOrCreateReference(def, invocationType, callSites, currentBB, newPostCond);

      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);

      // map parameters to method
      List<Reference> paramRefs = new ArrayList<Reference>();
      for (int i = 0, size = params.size(); i < size; i++) {
        String paramType = invokeInst.getDeclaredTarget().getParameterType(i).getName().toString();
        Reference paramRef = findOrCreateReference(params.get(i), paramType, callSites, currentBB, newPostCond);
        paramRefs.add(paramRef);
      }
      beforeInvocation(invokeInst, refRef, defRef, paramRefs, newRefMap);
      
      // different handling mechanisms for ordinary invocations and entering call stacks
      if (execOptions.isEnteringCallStack()) {
        // compute targeting method to enter call stack
        try {
          preCond = computeToEnterCallSite(invokeInst, instInfo, execOptions, caller, 
                                           callStack, curInvokeDepth, callSites, newPostCond);
        } catch (InvalidStackTraceException e) {
          // cannot find the callsite method (e.g., interface method)
          return handle_invokenonstatic(postCond, inst, instInfo);
        } catch (TimeLimitExceededException e) {
          return handle_invokenonstatic(postCond, inst, instInfo);
        }
      }
      else {
        // compute targeting method with startLine = -1 (from exit block)
        try {
          preCond = computeAtCallSite(invokeInst, instInfo, execOptions, caller, 
                                      callStack, curInvokeDepth, callSites, newPostCond);
        } catch (InvalidStackTraceException e) {
          // cannot find the callsite method (e.g., interface method)
          return handle_invokenonstatic(postCond, inst, instInfo);
        } catch (TimeLimitExceededException e) {
          return handle_invokenonstatic(postCond, inst, instInfo);
        }
      }

      // if succeed
      if (preCond != null) {
        afterInvocation(invokeInst, callSites, currentBB, ref, refType, def, params, preCond.getRefMap(), preCond.getDefMap());
        refRef = findOrCreateReference(ref, refType, callSites, currentBB, preCond);

        // add type condition: v1 instanceof the concrete method class
        if (instInfo.target != null && instInfo.target[0].equals(invokeInst) && instInfo.target[1] != null) {
          String methodSig = ((IR) (instInfo.target[1])).getMethod().getSignature();
          boolean usedPseudo = instInfo.executor.usePseudo() && PseudoImplMap.findPseudoImpl(methodSig) != null;
          if (!usedPseudo) {
            String declClass = ((IR) (instInfo.target[1])).getMethod().getDeclaringClass().getName().toString();
            TypeConditionTerm typeTerm = new TypeConditionTerm(
                refRef.getInstance(), TypeConditionTerm.Comparator.OP_INSTANCEOF, declClass);
            conditionList.add(new Condition(typeTerm));
          }
        }
        
        // new condition: ref != null
        nullRef = findOrCreateReference("null", "", "", currentBB, preCond);
        BinaryConditionTerm notNull = new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance());
        conditionList.add(new Condition(notNull));

        preCond.getConditionList().addAll(conditionList);
        // add new references to refMap
        addRefToRefMap(preCond.getRefMap(), refRef);
        return new Formula(preCond);
      }
      else {
        // an inner contradiction has been detected
        return preCond;
      }
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: ref == null
      refRef  = findOrCreateReference(ref, refType, callSites, currentBB, newPostCond);
      nullRef = findOrCreateReference("null", "", "", currentBB, newPostCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  // go into invocation
  public Formula handle_invokestatic_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    Formula preCond                                           = null;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Formula newPostCond                                       = postCond.clone();
    Hashtable<String, Hashtable<String, Reference>> newRefMap = newPostCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = newPostCond.getDefMap();
    SSAInvokeInstruction invokestaticInst                     = (SSAInvokeInstruction) inst;
    
    // check if method name is in the filter list, if so, not step in
    if (isMethodSigFiltered(invokestaticInst.getDeclaredTarget().getSignature())) {
      return handle_invokestatic(postCond, inst, instInfo); // use not step in version
    }
    
    // the variable(result) define by the invokestatic instruction
    String def = getSymbol(invokestaticInst.getDef(), methData, callSites, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(getSymbol(invokestaticInst.getUse(i), methData, callSites, newDefMap));
    }

    String invocationType = invokestaticInst.getDeclaredResultType().getName().toString();
    Reference defRef = findOrCreateReference(def, invocationType, callSites, currentBB, newPostCond);

    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    // map parameters to method
    List<Reference> paramRefs = new ArrayList<Reference>();
    for (int i = 0, size = params.size(); i < size; i++) {
      String paramType = invokestaticInst.getDeclaredTarget().getParameterType(i).getName().toString();
      Reference paramRef = findOrCreateReference(params.get(i), paramType, callSites, currentBB, newPostCond);
      paramRefs.add(paramRef);
    }
    beforeInvocation(invokestaticInst, null, defRef, paramRefs, newRefMap);
    
    // different handling mechanisms for ordinary invocations and entering call stacks
    if (execOptions.isEnteringCallStack()) {
      // compute targeting method to enter call stack
      try {
        preCond = computeToEnterCallSite(invokestaticInst, instInfo, execOptions, caller, 
                                         callStack, curInvokeDepth, callSites, newPostCond);
      } catch (InvalidStackTraceException e) {
        // cannot find the callsite method (e.g., interface method)
        return handle_invokestatic(postCond, inst, instInfo);
      } catch (TimeLimitExceededException e) {
        return handle_invokestatic(postCond, inst, instInfo);
      }
    }
    else {
      // compute targeting method with startLine = -1 (from exit block)
      try {
        preCond = computeAtCallSite(invokestaticInst, instInfo, execOptions, caller, 
                                    callStack, curInvokeDepth, callSites, newPostCond);
      } catch (InvalidStackTraceException e) {
        // cannot find the callsite method (e.g., interface method)
        return handle_invokestatic(postCond, inst, instInfo);
      } catch (TimeLimitExceededException e) {
        return handle_invokestatic(postCond, inst, instInfo);
      }
    }

    // if succeed
    if (preCond != null) {
      afterInvocation(invokestaticInst, callSites, currentBB, null, null, def, params, preCond.getRefMap(), preCond.getDefMap());
      return new Formula(preCond);
    }
    else {
      // an inner contradiction has been detected
      return preCond;
    }
  }
  
  public Formula handle_load_metadata(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSALoadMetadataInstruction loadMetaInst                   = (SSALoadMetadataInstruction) inst;

    // the variable(result) define by the load_metadata instruction
    String def = getSymbol(loadMetaInst.getDef(), methData, callSites, newDefMap);
    
    Object token = loadMetaInst.getToken();
    String metaDataType = loadMetaInst.getType().getName().toString(); 
    
    if (token instanceof TypeReference && metaDataType.equals("Ljava/lang/Class")) { // a loadClass operation
      if (containsRef(def, callSites, newRefMap)) {
        String loadClass = ((TypeReference) token).getName().toString() + ".class";
        
        Reference loadClassRef = findOrCreateReference(loadClass, metaDataType, callSites, currentBB, postCond);
        Reference defRef = findOrCreateReference(def, metaDataType, callSites, currentBB, postCond);

        // add new references to refMap
        addRefToRefMap(newRefMap, loadClassRef);
        
        // assign the instance to the def reference
        assignInstance(defRef, loadClassRef, newRefMap, newDefMap);
      }
  
      return new Formula(postCond);
    }
    else {
      return defaultHandler(postCond, inst, instInfo);
    }
  }
  
  public Formula handle_monitorenter(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  public Formula handle_monitorexit(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  public Formula handle_neg(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAUnaryOpInstruction unaryInst                           = (SSAUnaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def = getSymbol(unaryInst.getDef(), methData, callSites, newDefMap);
    String var = getSymbol(unaryInst.getUse(0), methData, callSites, newDefMap);
    
    if (containsRef(def, callSites, newRefMap)) {
      Reference varRef = findOrCreateReference(var, "I", callSites, currentBB, postCond);
      Reference defRef = findOrCreateReference(def, "I", callSites, currentBB, postCond); // reference must exist
      
      Instance unaryOp = null;
      switch ((IUnaryOpInstruction.Operator) unaryInst.getOpcode()) {
      case NEG:   /* the only one */
        unaryOp = new Instance(new Instance("#!0", "I", currentBB), INSTANCE_OP.SUB, varRef.getInstance(), currentBB);
        break;
      }

      // add new references to refMap
      addRefToRefMap(newRefMap, varRef);

      // assign the instance to the def reference
      assignInstance(defRef, unaryOp, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }

  public Formula handle_new(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSANewInstruction newInst                                 = (SSANewInstruction) inst;

    String def = getSymbol(newInst.getDef(), methData, callSites, newDefMap);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    
    String newType = newInst.getConcreteType().getName().toString();
    Reference defRef = findOrCreateReference(def, newType, callSites, currentBB, postCond);
    
    // for array types, we also need to substitute ".length" field
    if (newInst.getConcreteType().isArrayType()) {
      String valSize = getSymbol(newInst.getUse(0), methData, callSites, newDefMap);
      Reference valSizeRef = findOrCreateReference(valSize, "I", callSites, currentBB, postCond);
      Reference zeroRef = findOrCreateReference("#!0", "I", "", currentBB, postCond);
      
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(valSizeRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      addRefToRefMap(newRefMap, valSizeRef);
      
      List<Reference> lenRefs = defRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {
        if (defRef.getInstances().size() == 0) {
          try {
            // at least one instance to hold the field
            defRef.assignInstance(new Instance(callSites, currentBB), true);
          } catch (Exception e) {e.printStackTrace();}
        }
        Reference lenRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), defRef.getInstance(), true);
        defRef.getInstance().setField("length", "I", callSites, lenRef.getInstances(), true, false);
        lenRefs = defRef.getFieldReferences("length");
      }
      // substitute
      if (valSizeRef.getInstance().isBounded()) {
        for (Reference lenRef : lenRefs) {
          lenRef.setFieldInstancesValue(valSizeRef.getInstance(), postCond);
          lenRef.putInstancesToOld();
        }
      }
      else {
        boolean assignable = findReference(valSize, callSites, newRefMap) != null;
        for (Reference lenRef : lenRefs) {
          if (assignable) {
            try {
              valSizeRef.assignInstance(lenRef.getInstances(), true);
            } catch (Exception e) { e.printStackTrace();}
          }
          else {
            valSizeRef = new Reference(valSize, "I", callSites, lenRef.getInstances(), null, true);
            assignable = true;
          }
          lenRef.putInstancesToOld();
        }
      }
      postCond.addFieldAssignTime("length", System.nanoTime());
      
      // assign initial values to array elements, XXX currently only works for constant size array
      int size = valSize.startsWith("#!") ? Integer.parseInt(valSize.substring(2)) : 3;
      size = size > 10 ? 10 : size; // avoid large number of array stores which causes performance problem in solver
      
      TypeReference elemType = newInst.getConcreteType().getArrayElementType();
      String val = elemType.isPrimitiveType() ? "#!0" /* number or boolean(false)*/ : "null";
      Reference valRef = findOrCreateReference(val, elemType.getName().toString(), "", currentBB, postCond);
      for (int i = 0; i < size; i++) {
        Reference indexRef = findOrCreateReference("#!" + i, "I", "", currentBB, postCond);
        Relation relation = postCond.getRelation("@@array");
        relation.update(new Instance[] {defRef.getInstance(), indexRef.getInstance()}, valRef.getInstance());
      }
      addRefToRefMap(newRefMap, defRef);
    }
//    // initialize the default values of each member fields // done in the entry block of <init> already
//    else if (newInst.getConcreteType().isClassType()) {
//      
//      IClass newClass = instInfo.executor.getWalaAnalyzer().getClassHierarchy().lookupClass(newInst.getConcreteType());
//      if (newClass != null) {
//        Collection<IField> fields = newClass.getAllInstanceFields();
//        for (IField field : fields) {
//          String fieldType = field.getFieldTypeReference().getName().toString();
//          String fieldName = field.getName().toString();
//        
//          // put the default value according to the field type
//          String val = (field.getFieldTypeReference().isPrimitiveType()) ? "#!0" /* number or boolean(false)*/ : "null";
//          Instance valInstance = new Instance(val, fieldType, currentBB);
//
//          // find the fieldRef
//          List<Reference> fieldRefs = defRef.getFieldReferences(fieldName);
//          if (fieldRefs.size() == 0) {
//            if (defRef.getInstances().size() == 0) {
//              try {
//                // at least one instance to hold the field
//                defRef.assignInstance(new Instance(callSites, currentBB), true);
//              } catch (Exception e) {e.printStackTrace();}
//            }
//            Reference fieldRef = new Reference(fieldName, fieldType, callSites, 
//                new Instance(callSites, currentBB), defRef.getInstance(), true);
//            defRef.getInstance().setField(fieldName, fieldType, callSites, fieldRef.getInstances(), true, false);
//            fieldRefs = defRef.getFieldReferences(fieldName);
//          }
//          for (Reference fieldRef : fieldRefs) {
//            fieldRef.setFieldInstancesValue(valInstance, postCond);
//            fieldRef.putInstancesToOld();
//          }
//          postCond.addFieldAssignTime(fieldName, System.nanoTime());
//        }
//      }
//    }
    
    // the variable define by the new instruction
    long instanceID  = System.nanoTime();
    String freshInst = "FreshInstanceOf(" + newType + "_" + instanceID + ")";
    if (containsRef(def, callSites, newRefMap)) {
      // get the declared type of the new Instruction
      Instance newInstance = new Instance(freshInst, newType, currentBB);

      // assign the instance to the def reference
      assignInstance(defRef, newInstance, newRefMap, newDefMap);
    }

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  public Formula handle_phi(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  public Formula handle_phi(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, int phiVarID, ISSABasicBlock predBB) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    SSAPhiInstruction phiInst                                 = (SSAPhiInstruction) inst;
    
    if (phiVarID > 0) {
      String def = getSymbol(phiInst.getDef(), methData, callSites, newDefMap);
      String var = getSymbol(phiVarID, methData, callSites, newDefMap);
      Reference defRef = findOrCreateReference(def, "Unknown-Type", callSites, currentBB, postCond);
      Reference phiRef = findOrCreateReference(var, "Unknown-Type", callSites, predBB, postCond);

      // associate the two refs' instance together as the same one
      assignInstance(defRef, phiRef, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }
  
  // handler for pi instruction
  public Formula handle_pi(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                        = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                                = instInfo.callSites;
    ISSABasicBlock currentBB                                        = instInfo.currentBB;
    MethodMetaData methData                                         = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap       = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap         = postCond.getDefMap();
    SSAPiInstruction piInst                                         = (SSAPiInstruction) inst;

    if (instInfo.previousBB != null && piInst.getSuccessor() == instInfo.previousBB.getNumber()) {
      String def = getSymbol(piInst.getDef(), methData, callSites, newDefMap);
      String val = getSymbol(piInst.getVal(), methData, callSites, newDefMap);
      
      // add new references to refMap
      Reference defRef = findOrCreateReference(def, "Unknown-Type", callSites, currentBB, postCond);
      Reference valRef = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, postCond);
      
      // associate the two refs' instance together as the same one
      assignInstance(defRef, valRef, newRefMap, newDefMap);
    }

    return new Formula(postCond);
  }
  
  // handler for putfield instruction
  public Formula handle_putfield(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAPutInstruction putfieldInst                            = (SSAPutInstruction) inst;

    // the variable(result) define by the putfield instruction
    String ref = getSymbol(putfieldInst.getUse(0), methData, callSites, postCond.getDefMap());
    String val = getSymbol(putfieldInst.getUse(1), methData, callSites, postCond.getDefMap());

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: ref != null
      String refTypeName = putfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      Reference refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, postCond);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);
   
      String fieldType = putfieldInst.getDeclaredFieldType().getName().toString();
      String fieldName = putfieldInst.getDeclaredField().getName().toString();
      Reference valRef = findOrCreateReference(val, fieldType, callSites, currentBB, postCond);
      
      if (containsFieldName(fieldName, postCond)) {
        // find the fieldRef
        List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
        if (fieldRefs.size() == 0) {
          Reference fieldRef = new Reference(fieldName, fieldType, callSites, 
              new Instance(callSites, currentBB), refRef.getInstance(), true);
          refRef.getInstance().setField(fieldName, fieldType, callSites, fieldRef.getInstances(), true, false);
          fieldRefs = refRef.getFieldReferences(fieldName);
        }
        // substitute
        if (valRef.getInstance().isBounded()) {
          for (Reference fieldRef : fieldRefs) {
            fieldRef.setFieldInstancesValue(valRef.getInstance(), postCond);
            fieldRef.putInstancesToOld();
          }
        }
        else {
          boolean assignable = findReference(val, callSites, newRefMap) != null;
          for (Reference fieldRef : fieldRefs) {
            if (fieldRef.getInstances().size() > 0) {
              if (assignable) {
                try {
                  valRef.assignInstance(fieldRef.getInstances(), true);
                } catch (Exception e) {e.printStackTrace();}
              }
              else {
                valRef = new Reference(val, fieldType, callSites, fieldRef.getInstances(), null, true);
                assignable = true;
              }
              fieldRef.putInstancesToOld();
            }
          }
          addRefToRefMap(newRefMap, valRef);
        }
        postCond.addFieldAssignTime(fieldName, System.nanoTime());
      }
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: ref == null
      refTypeName = putfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, postCond);
      nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, refRef);

      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  
  // handler for putstatic instruction
  public Formula handle_putstatic(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAPutInstruction putstaticInst                           = (SSAPutInstruction) inst;

    String val = getSymbol(putstaticInst.getUse(0), methData, callSites, postCond.getDefMap());
    
    // get the class type that declared this field
    String declaredField = putstaticInst.getDeclaredField().getDeclaringClass().getName().toString();
    // get the name of the field
    declaredField += "." + putstaticInst.getDeclaredField().getName();
    
    // add new references to refMap
    String fieldType = putstaticInst.getDeclaredFieldType().getName().toString();
    Reference fieldRef = findOrCreateReference(declaredField, fieldType, "", currentBB, postCond);  // static field also goes to "" callSites
    Reference valRef   = findOrCreateReference(val, fieldType, callSites, currentBB, postCond);

    // associate the two refs' instance together as the same one
    assignInstance(fieldRef, valRef, newRefMap, postCond.getDefMap());

    return new Formula(postCond);
  }

  public Formula handle_return(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAReturnInstruction returnInst                           = (SSAReturnInstruction) inst;
    
    // the return value of the instruction
    String ret = getSymbol(returnInst.getResult(), methData, callSites, postCond.getDefMap());

    // substitute "RET" given by caller
    Reference returnRef = findOrCreateReference(ret, "Unknown-Type", callSites, currentBB, postCond);
    Reference retRef    = findOrCreateReference("RET", "Unknown-Type", callSites, currentBB, postCond);

    // associate the two refs' instance together as the same one
    assignInstance(retRef, returnRef, newRefMap, postCond.getDefMap());

    return new Formula(postCond);
  }

  public Formula handle_switch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSASwitchInstruction switchInst                           = (SSASwitchInstruction) inst;

    // get the variables of the switch statement,
    // the variables might be constant numbers!
    String var1 = getSymbol(switchInst.getUse(0), methData, callSites, postCond.getDefMap());
    Reference var1Ref = findOrCreateReference(var1, "I", callSites, currentBB, postCond);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();

    // create switch SMTStatement
    int label = instInfo.previousBB.getFirstInstructionIndex();
    int[] casesAndLables = switchInst.getCasesAndLabels();

    // if is default label
    if (switchInst.getDefault() == label) {
      // to reach default label, no case should be matched
      for (int i = 0; i < casesAndLables.length; i += 2) {
        // cases should always be constant number
        String caseNum = "#!" + casesAndLables[i];
        conditionTerms = new ArrayList<ConditionTerm>();
        conditionTerms.add(new BinaryConditionTerm(var1Ref.getInstance(), Comparator.OP_INEQUAL, new Instance(caseNum, "I", currentBB))); 
        conditionList.add(new Condition(conditionTerms));
      }
    }
    else {
      List<Integer> caseIndices = new ArrayList<Integer>();
      for (int i = 1; i < casesAndLables.length; i += 2) {
        // found the switch case that leads to the label
        if (casesAndLables[i] == label) {
          caseIndices.add(i);
        }
      }
      conditionTerms = new ArrayList<ConditionTerm>();
      for (Integer index : caseIndices) {
        // cases should always be constant number
        String caseNum = "#!" + casesAndLables[index - 1];
        conditionTerms.add(new BinaryConditionTerm(var1Ref.getInstance(), Comparator.OP_EQUAL, new Instance(caseNum, "I", currentBB))); 
      }
      conditionList.add(new Condition(conditionTerms));
    }
    // add new references to refMap
    addRefToRefMap(newRefMap, var1Ref);

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  

  public Formula handle_switch(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  // handler for throw instruction
  public Formula handle_throw(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    SSAThrowInstruction throwInst                             = (SSAThrowInstruction) inst;

    // the variable(result) thrown by throw instruction
    String exception = getSymbol(throwInst.getUse(0), methData, callSites, postCond.getDefMap());

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    
    // new condition: excepRef != null
    Reference excepRef = findOrCreateReference(exception, "Unknown-Type", callSites, currentBB, postCond);
    Reference nullRef  = findOrCreateReference("null", "", "", currentBB, postCond);
    conditionTerms = new ArrayList<ConditionTerm>();
    conditionTerms.add(new BinaryConditionTerm(excepRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
    conditionList.add(new Condition(conditionTerms));

    // add new references to refMap
    addRefToRefMap(newRefMap, excepRef);
    
    // add "ThrownInstCurrent " flag to varMap, indicating an exception is
    // thrown at the current method, but we will not check if it is the
    // exception we are looking for, because we cannot finalize exception 
    // variable at the moment. We will check it after we exit the current method
    //newVarMap = setExceptionThrownCurrent(postCond, newVarMap, exception);

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }

  public Formula handle_entryblock(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    postCond                                                  = postCond == instInfo.formula4BB ? postCond.clone() : postCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = postCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = postCond.getDefMap();
    
    // for the outermost frame, need to add this != null manually
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    if (instInfo.callSites.length() == 0 && !methData.isStatic()) {
      // new condition: this != null
      Reference thisRef = findOrCreateReference("v1", "Unknown-Type", callSites, currentBB, postCond);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, postCond);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(thisRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // add new references to refMap
      addRefToRefMap(newRefMap, thisRef);
    }

    // at the entry block, all parameters are defined, and parameters' types are confirmed
    for (int i = 0, count = methData.getIR().getNumberOfParameters(); i < count; i++) {
      String paramName = getSymbol(methData.getIR().getParameter(i), methData, callSites, postCond.getDefMap());
      String paramType = methData.getIR().getParameterType(i).getName().toString();
      Reference paramRef = findOrCreateReference(paramName, paramType, callSites, currentBB, postCond);
      paramRef.setType(paramType);

      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, paramRef);
    }
    
    // at the entry block of <init>, all fields declared in class is initialized to default values 
    if (methData.getName().equals("<init>")) {
      IClass declClass = methData.getIR().getMethod().getDeclaringClass();
      Collection<IField> fields = declClass.getDeclaredInstanceFields();
      for (IField field : fields) {
        String fieldType = field.getFieldTypeReference().getName().toString();
        String fieldName = field.getName().toString();
      
        // put the default value according to the field type
        String val = (field.getFieldTypeReference().isPrimitiveType()) ? "#!0" /* number or boolean(false)*/ : "null";
        Instance valInstance = new Instance(val, fieldType, currentBB);

        // find the fieldRef
        Reference thisRef = findOrCreateReference("v1", "Unknown-Type", callSites, currentBB, postCond);
        List<Reference> fieldRefs = thisRef.getFieldReferences(fieldName);
        if (fieldRefs.size() == 0) {
          if (thisRef.getInstances().size() == 0) {
            try {
              // at least one instance to hold the field
              thisRef.assignInstance(new Instance(callSites, currentBB), true);
            } catch (Exception e) {e.printStackTrace();}
          }
          Reference fieldRef = new Reference(fieldName, fieldType, callSites, 
              new Instance(callSites, currentBB), thisRef.getInstance(), true);
          thisRef.getInstance().setField(fieldName, fieldType, callSites, fieldRef.getInstances(), true, false);
          fieldRefs = thisRef.getFieldReferences(fieldName);
        }
        for (Reference fieldRef : fieldRefs) {
          fieldRef.setFieldInstancesValue(valInstance, postCond);
          fieldRef.putInstancesToOld();
        }
        postCond.addFieldAssignTime(fieldName, System.nanoTime());
      }
    }

    // at the entry block, check if the caught exception is thrown
    //newVarMap = checkExceptionThrown(postCond, newVarMap);
    
    // at the entry, set the equivalent not set instances
    postCond = setEquivalentInstances(postCond, callSites);

    // add new conditions to condition list
    postCond.getConditionList().addAll(conditionList);
    return new Formula(postCond);
  }
  
  public Formula handle_exitblock(Formula postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return postCond;
  }
}
