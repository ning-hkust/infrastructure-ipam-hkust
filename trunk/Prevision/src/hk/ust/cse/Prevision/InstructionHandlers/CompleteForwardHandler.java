package hk.ust.cse.Prevision.InstructionHandlers;

import hk.ust.cse.Prevision.Misc.CallStack;
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
import hk.ust.cse.Wala.MethodMetaData;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.cfg.Util;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IComparisonInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IShiftInstruction;
import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
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
import com.ibm.wala.types.TypeReference;

public class CompleteForwardHandler extends AbstractForwardHandler {
  
  public Formula handle_arraylength(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAArrayLengthInstruction arrayLengthInst                 = (SSAArrayLengthInstruction) inst;

    // the variable(result) define by the arraylength instruction
    String def      = getSymbol(arrayLengthInst.getDef(), methData, callSites, newDefMap, true);
    String arrayRef = getSymbol(arrayLengthInst.getArrayRef(), methData, callSites, newDefMap, false);
    
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: arrayRef != null
      Reference arrayRefRef = findOrCreateReference(arrayRef, "Unknown-Type", callSites, currentBB, newRefMap);
      Reference nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      List<Reference> fieldRefs = arrayRefRef.getFieldReferences("length");
      if (fieldRefs.size() == 0) {        
        Reference fieldRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, fieldRef.getInstances(), true, true);
        fieldRefs = arrayRefRef.getFieldReferences("length");
      }   
      Reference defRef = new Reference(def, "I", callSites, fieldRefs.get(0).getInstance(), null, false);
      
      Reference arrayLenRef = fieldRefs.get(0); // simply use the first one
      Reference zeroRef     = findOrCreateReference("#!0", "I", "", currentBB, newRefMap);

      // additional condition to make sure: array.length >= 0
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayLenRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      
      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: arrayRef == null
      arrayRefRef = findOrCreateReference(arrayRef, "Unknown-Type", callSites, currentBB, newRefMap);
      nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }
    
    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  public Formula handle_arrayload(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAArrayLoadInstruction arrayLoadInst                     = (SSAArrayLoadInstruction) inst;

    // the variable(result) define by the arrayload instruction
    String def        = getSymbol(arrayLoadInst.getDef(), methData, callSites, newDefMap, true);
    String arrayRef   = getSymbol(arrayLoadInst.getArrayRef(), methData, callSites, newDefMap, false);
    String arrayIndex = getSymbol(arrayLoadInst.getIndex(), methData, callSites, newDefMap, false);

    String elemType   = arrayLoadInst.getElementType().getName().toString();
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: arrayRef != null
      Reference arrayRefRef = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
      Reference nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // new conditions: arrayIndex >= 0 && arrayIndex < arryLength
      Reference arrayIndexRef = findOrCreateReference(arrayIndex, "I", callSites, currentBB, newRefMap);
      Reference zeroRef       = findOrCreateReference("#!0", "I", "", currentBB, newRefMap);

      // get the array length field
      List<Reference> lenRefs = arrayRefRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {        
        Reference fieldRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, fieldRef.getInstances(), true, true);
        lenRefs = arrayRefRef.getFieldReferences("length");
      }   
      Reference arrayLenRef = lenRefs.get(0); // simply use the first one
      
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, arrayLenRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      Relation relation = preCond.getRelation("@@array");
      Reference readRef = relation.read(new Instance[] {arrayRefRef.getInstance(), 
                                        arrayIndexRef.getInstance()}, elemType, currentBB);    
      addRefToRefMap(newRefMap, readRef); // necessary for deepClone
      
      Reference defRef = new Reference(def, elemType, callSites, readRef.getInstance(), null, false);

      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
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
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }
  
  public Formula handle_arraystore(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAArrayStoreInstruction arrayStoreInst                   = (SSAArrayStoreInstruction) inst;

    String arrayRef   = getSymbol(arrayStoreInst.getArrayRef(), methData, callSites, preCond.getDefMap(), false);
    String arrayIndex = getSymbol(arrayStoreInst.getIndex(), methData, callSites, preCond.getDefMap(), false);
    String storeValue = getSymbol(arrayStoreInst.getValue(), methData, callSites, preCond.getDefMap(), false);
    
    String elemType   = arrayStoreInst.getElementType().getName().toString();
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: arrayRef != null
      Reference arrayRefRef = findOrCreateReference(arrayRef, "[" + elemType, callSites, currentBB, newRefMap);
      Reference nullRef     = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayRefRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // new conditions: arrayIndex >= 0 && arrayIndex < arryLength
      Reference arrayIndexRef = findOrCreateReference(arrayIndex, "I", callSites, currentBB, newRefMap);
      Reference zeroRef       = findOrCreateReference("#!0", "I", "", currentBB, newRefMap);
      
      // get the array length field
      List<Reference> lenRefs = arrayRefRef.getFieldReferences("length");
      if (lenRefs.size() == 0) {        
        Reference fieldRef = new Reference("length", "I", callSites, 
            new Instance(callSites, currentBB), arrayRefRef.getInstance(), true);
        arrayRefRef.getInstance().setField("length", "I", callSites, fieldRef.getInstances(), true, true);
        lenRefs = arrayRefRef.getFieldReferences("length");
      }   
      Reference arrayLenRef = lenRefs.get(0); // simply use the first one
      
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_GREATER_EQUAL, zeroRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(arrayIndexRef.getInstance(), Comparator.OP_SMALLER, arrayLenRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      Reference storeValRef = findOrCreateReference(storeValue, elemType, callSites, currentBB, newRefMap);

      Relation relation = preCond.getRelation("@@array");
      relation.update(new Instance[] {arrayRefRef.getInstance(), arrayIndexRef.getInstance()}, storeValRef.getInstance());
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
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  public Formula handle_binaryop(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSABinaryOpInstruction binaryOpInst                       = (SSABinaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction    
    String def  = getSymbol(binaryOpInst.getDef(), methData, callSites, newDefMap, true);
    String var1 = getSymbol(binaryOpInst.getUse(0), methData, callSites, newDefMap, false);
    String var2 = getSymbol(binaryOpInst.getUse(1), methData, callSites, newDefMap, false);
    
    Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, newRefMap);
    
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

    Reference defRef = new Reference(def, "Unknown-Type", callSites, binaryOp, null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }
  
  // handler for catch instruction
  public Formula handle_catch(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAGetCaughtExceptionInstruction catchInst                = 
      ((ExceptionHandlerBasicBlock) instInfo.currentBB).getCatchInstruction();

    // the e defined by catch
    String def = getSymbol(catchInst.getDef(), methData, callSites, newDefMap, true);

    // get the declared type of the exception
    TypeReference excepType = methData.getExceptionType(instInfo.currentBB);
    String excepTypeStr = excepType.getName().toString();

    // create new instance of e
    long instanceID  = System.nanoTime();
    String freshInst = "FreshInstanceOf(" + excepTypeStr + "_" + instanceID + ")";
    Instance excep   = new Instance(freshInst, excepTypeStr, currentBB);
    
    Reference defRef = new Reference(def, excepTypeStr, callSites, excep, null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);
    
    // add a caught variable to indicate "coming from a catch block of 
    // some exception type", and expect to meet an exception triggering point
    //newVarMap = setExceptionCaught(postCond, newVarMap, excepTypeStr);

    return new Formula(preCond);
  }
  
  // handler for checkcast instruction
  public Formula handle_checkcast(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap(); 
    SSACheckCastInstruction checkcastInst                     = (SSACheckCastInstruction) inst;

    // the variable(result) define by the checkcast instruction
    String def = getSymbol(checkcastInst.getDef(), methData, callSites, newDefMap, true);
    String val = getSymbol(checkcastInst.getUse(0), methData, callSites, newDefMap, false);
    String declaredResultType = checkcastInst.getDeclaredResultType().getName().toString();

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: subTypeStr == true || val == null
      Reference valRef  = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, newRefMap);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new TypeConditionTerm(
          valRef.getInstance(), TypeConditionTerm.Comparator.OP_INSTANCEOF, declaredResultType)); 
      conditionTerms.add(new BinaryConditionTerm(valRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));

      Reference defRef = new Reference(def, declaredResultType, callSites, valRef.getInstance(), null, false);

      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be CCE */
      // new condition: val != null && subTypeStr == false
      valRef     = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, newRefMap);
      nullRef    = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(valRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance()));
      conditionList.add(new Condition(conditionTerms));
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new TypeConditionTerm(
          valRef.getInstance(), TypeConditionTerm.Comparator.OP_NOT_INSTANCEOF, declaredResultType)); 
      conditionList.add(new Condition(conditionTerms));
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ClassCastException");
      break;
    }
    
    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }
  
  public Formula handle_compare(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAComparisonInstruction compareInst                      = (SSAComparisonInstruction) inst;

    // the variable(result) define by the compare instruction    
    String def  = getSymbol(compareInst.getDef(), methData, callSites, newDefMap, true);
    String var1 = getSymbol(compareInst.getUse(0), methData, callSites, newDefMap, false);
    String var2 = getSymbol(compareInst.getUse(1), methData, callSites, newDefMap, false);
    
    Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, newRefMap);
    
    Instance compareOp = null;
    switch ((IComparisonInstruction.Operator) compareInst.getOperator()) {
    case CMP:   /* for long */
    case CMPL:  /* for float or double */
    case CMPG:  /* for float or double */
      compareOp = new Instance(var1Ref.getInstance(), INSTANCE_OP.SUB, var2Ref.getInstance(), currentBB);
      break;
    }
    
    Reference defRef = new Reference(def, "Unknown-Type", callSites, compareOp, null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }

  public Formula handle_conversion(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAConversionInstruction convInst                         = (SSAConversionInstruction) inst;

    // the variable(result) define by the conversion instruction
    String toVal    = getSymbol(convInst.getDef(), methData, callSites, newDefMap, true);
    String fromVal  = getSymbol(convInst.getUse(0), methData, callSites, newDefMap, false);
    String fromType = convInst.getFromType().getName().toString();
    String toType   = convInst.getToType().getName().toString();    

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    
    Reference fromValRef = findOrCreateReference(fromVal, fromType, callSites, currentBB, newRefMap);
    Reference toValRef   = null;
    
    if (fromType.equals("I") || fromType.equals("J") || fromType.equals("S")) { // from integer to float or integer
      // associate the two refs' instance together as the same one
      toValRef = new Reference(toVal, toType, callSites, fromValRef.getInstance(), null, false);
    }
    else if (fromType.equals("D") || fromType.equals("F")) {
      if (toType.equals("I") || toType.equals("J") || toType.equals("S")) { // from float to integer
        
        Reference convValRef = null;
        if (fromVal.startsWith("#!")) { // it is a constant number
          int index = fromVal.lastIndexOf('.');
          String convVal = (index >= 0) ? fromVal.substring(0, index) : fromVal;
          convValRef = findOrCreateReference(convVal, "I", callSites, currentBB, newRefMap);
        }
        else {
          // create a converted val
          String convVal = fromVal + "$1" /* first kind of conversion */;
          
          convValRef = findOrCreateReference(convVal, "I", callSites, currentBB, newRefMap);
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
          addRefToRefMap(newRefMap, convValRef);
        }
        toValRef = new Reference(toVal, toType, callSites, convValRef.getInstance(), null, false);
      }
      else if (toType.equals("D") || toType.equals("F")) { // from float to float
        // associate the two refs' instance together as the same one
        toValRef = new Reference(toVal, toType, callSites, fromValRef.getInstance(), null, false);
      }
    }
    else {
      // not implement
    }

    // add new references to refMap
    addRefToRefMap(newRefMap, toValRef);
    
    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  public Formula handle_conditional_branch(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  public Formula handle_conditional_branch(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAConditionalBranchInstruction condBranchInst            = (SSAConditionalBranchInstruction) inst;

    // check whether or not the conditional branch has been taken
    // the branch instruction will always be the last instruction
    // of the current block, so we can check whether the branch has
    // been taken or not by checking the successor bb number
    boolean tookBranch = true;
    if (instInfo.currentBB.getNumber() + 1 == successor.getNumber()) {
      tookBranch = false;
    }

    // get the variables of the conditional branch,  
    // the variables might be constant numbers!
    String var1 = getSymbol(condBranchInst.getUse(0), methData, callSites, preCond.getDefMap(), false);
    String var2 = getSymbol(condBranchInst.getUse(1), methData, callSites, preCond.getDefMap(), false);
    
    Reference var1Ref = findOrCreateReference(var1, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference var2Ref = findOrCreateReference(var2, "Unknown-Type", callSites, currentBB, newRefMap);

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

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  // handler for getfield instruction
  public Formula handle_getfield(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAGetInstruction getfieldInst                            = (SSAGetInstruction) inst;

    // the variable(result) define by the getfield instruction
    String def = getSymbol(getfieldInst.getDef(), methData, callSites, newDefMap, true);
    String ref = getSymbol(getfieldInst.getUse(0), methData, callSites, newDefMap, false);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:  
      // new condition: ref != null
      String refTypeName = getfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      Reference refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, newRefMap);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      String fieldType = getfieldInst.getDeclaredFieldType().getName().toString();
      String fieldName = getfieldInst.getDeclaredField().getName().toString();

      List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
      if (fieldRefs.size() == 0) {        
        Reference fieldRef = new Reference(fieldName, fieldType, callSites, 
            new Instance(callSites, currentBB), refRef.getInstance(), true);
        refRef.getInstance().setField(fieldName, fieldType, callSites, fieldRef.getInstances(), true, true);
        fieldRefs = refRef.getFieldReferences(fieldName);
      }
      Reference defRef = new Reference(def, fieldType, callSites, fieldRefs.get(0).getInstance(), null, false);

      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
      
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: ref == null
      refTypeName = getfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, newRefMap);
      nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }
    
    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  // handler for getstatic instruction
  public Formula handle_getstatic(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAGetInstruction getstaticInst                           = (SSAGetInstruction) inst;

    String def = getSymbol(getstaticInst.getDef(), methData, callSites, newDefMap, true);

    // invoke static constructor
    String declClassType = getstaticInst.getDeclaredField().getDeclaringClass().getName().toString();
    cinitClass(declClassType, preCond, instInfo);

    String refTypeName = getstaticInst.getDeclaredField().getDeclaringClass().getName().toString();
    Reference refRef  = findOrCreateReference(refTypeName, refTypeName, "", currentBB, newRefMap); // static field also goes to "" callSites
    
    String fieldType = getstaticInst.getDeclaredFieldType().getName().toString();
    String fieldName = getstaticInst.getDeclaredField().getName().toString();

    List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
    if (fieldRefs.size() == 0) {        
      Reference fieldRef = new Reference(fieldName, fieldType, callSites, 
          new Instance(callSites, currentBB), refRef.getInstance(), true);
      refRef.getInstance().setField(fieldName, fieldType, callSites, fieldRef.getInstances(), true, true);
      fieldRefs = refRef.getFieldReferences(fieldName);
    }
    Reference defRef = new Reference(def, fieldType, callSites, fieldRefs.get(0).getInstance(), null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, refRef);
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }

  public Formula handle_goto(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  // handler for instanceof instruction
  public Formula handle_instanceof(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAInstanceofInstruction instanceofInst                   = (SSAInstanceofInstruction) inst;

    String def = getSymbol(instanceofInst.getDef(), methData, callSites, newDefMap, true);
    String ref = getSymbol(instanceofInst.getRef(), methData, callSites, newDefMap, false);

    Reference refRef  = findOrCreateReference(ref, "Unknown-Type", callSites, currentBB, newRefMap);
    String fieldName = "__instanceof__" + instanceofInst.getCheckedType().getName();

    List<Reference> fieldRefs = refRef.getFieldReferences(fieldName);
    if (fieldRefs.size() == 0) {        
      Reference fieldRef = new Reference(fieldName, "Z", callSites, 
          new Instance(callSites, currentBB), refRef.getInstance(), true);
      refRef.getInstance().setField(fieldName, "Z", callSites, fieldRef.getInstances(), true, true);
      fieldRefs = refRef.getFieldReferences(fieldName);
    }
    Reference defRef = new Reference(def, "Z", callSites, fieldRefs.get(0).getInstance(), null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);
    
    return new Formula(preCond);
  }
  
  public Formula handle_invokeinterface(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(preCond, inst, instInfo);
  }

  public Formula handle_invokevirtual(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(preCond, inst, instInfo);
  }

  public Formula handle_invokespecial(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(preCond, inst, instInfo);
  }
  
  /**
   * @param preCond
   * @param inst
   * @param instInfo
   * @return
   */
  private Formula handle_invokenonstatic(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAInvokeInstruction invokeInst                           = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterface/invokespecial/invokevirtual instruction
    String def = getSymbol(invokeInst.getDef(), methData, callSites, newDefMap, true);
    String ref = getSymbol(invokeInst.getUse(0), methData, callSites, newDefMap, false);
    
    // including "this"
    List<String> params = new ArrayList<String>();
    int count = invokeInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(getSymbol(invokeInst.getUse(i), methData, callSites, newDefMap, false));
    }
    
    String refType = invokeInst.getDeclaredTarget().getDeclaringClass().getName().toString();

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: ref != null
      Reference refRef  = findOrCreateReference(ref, refType, callSites, currentBB, newRefMap);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // the variable define by the invokeinterface/invokespecial/invokevirtual instruction
      // add new references to refMap
      String invocationType = invokeInst.getDeclaredResultType().getName().toString();
      StringBuilder invocation = new StringBuilder();
      invocation.append(invokeInst.getDeclaredTarget().getSignature());
      invocation.append("_");
      invocation.append(System.nanoTime());
      
      // put parameter instances in field slots
      Instance instance = new Instance(callSites, currentBB);
      for (int i = 0; i < params.size(); i++) {
        // should already exist
        Reference paramRef = findOrCreateReference(params.get(i), "Unknown-Type", callSites, currentBB, newRefMap);
        
        // if parameter is primitive (or null), no need make _undecidable_
        boolean primitiveParam = Utils.isPrimitiveType(paramRef.getType()) || paramRef.getInstance().isConstant() || 
                                      (paramRef.getInstance().isBounded() && !paramRef.getInstance().isAtomic());
        
        // same argument might be used for multiple parameters, e.g., this.setOffset(this, myNewPosition);
        String undecidableName = "_undecidable_" + invocation;
        if (!primitiveParam && paramRef.getInstance().getField(undecidableName) == null) {
          // create a new instance that contains the old parameter instance in its field
          Instance newParamInstance = new Instance(callSites, currentBB);
          boolean setLastRef = paramRef.getInstance().getLastReference() == paramRef;
          newParamInstance.setField(undecidableName, "Unknown-Type", callSites, paramRef.getInstance(), setLastRef, true);
          
          // new parameter reference
          paramRef = new Reference(paramRef.getName(), paramRef.getType(), 
            paramRef.getCallSites(), newParamInstance, paramRef.getDeclaringInstance(), true);
          
          // add new references to refMap
          addRefToRefMap(newRefMap, paramRef);
        }
        
        // save the parameter-argument mapping
        String fieldName = "v" + (i + 1) + "_" + paramRef.getName();
        instance.setField(fieldName, paramRef.getType(), callSites, paramRef.getInstances(), false, true);
      }
      
      // set the invocation as a field of the receiver reference
      Reference fieldRef = new Reference(invocation.toString(), invocationType, 
          callSites, instance, refRef.getInstance(), true);
      refRef = findOrCreateReference(ref, refType, callSites, currentBB, newRefMap);
      refRef.getInstance().setField(invocation.toString(), invocationType, callSites, fieldRef.getInstances(), true, true);
      
      if (!invocationType.equals("V")) { // not void return
        Reference defRef = new Reference(def, invocationType, callSites, fieldRef.getInstance(), null, false);

        // add new references to refMap
        addRefToRefMap(newRefMap, defRef);
        // since there is a new def, add to defMap
        addDefToDefMap(newDefMap, defRef);
      }

      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: arrayRef == null
      refRef  = findOrCreateReference(ref, refType, callSites, currentBB, newRefMap);
      nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  // simple implementation, do not consider call graph
  public Formula handle_invokestatic(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAInvokeInstruction invokestaticInst                     = (SSAInvokeInstruction) inst;

    String def = getSymbol(invokestaticInst.getDef(), methData, callSites, newDefMap, true);
    
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(getSymbol(invokestaticInst.getUse(i), methData, callSites, newDefMap, false));
    }

    // the variable define by the invokestatic instruction
    String invocationType = invokestaticInst.getDeclaredResultType().getName().toString();
    StringBuilder invocation = new StringBuilder();
    invocation.append(invokestaticInst.getDeclaredTarget().getSignature());
    invocation.append("_");
    invocation.append(System.nanoTime());
    
    // put parameter instances in field slots
    Instance instance = new Instance(callSites, currentBB);
    for (int i = 0; i < params.size(); i++) {
      // should already exist
      Reference paramRef = findOrCreateReference(params.get(i), "Unknown-Type", callSites, currentBB, newRefMap);
      
      // if parameter is primitive (or null), no need make _undecidable_
      boolean primitiveParam = Utils.isPrimitiveType(paramRef.getType()) || paramRef.getInstance().isConstant() || 
                                    (paramRef.getInstance().isBounded() && !paramRef.getInstance().isAtomic());
      
      // same argument might be used for multiple parameters, e.g., setOffset(this, this);
      String undecidableName = "_undecidable_" + invocation;
      if (!primitiveParam && paramRef.getInstance().getField(undecidableName) == null) {
        // create a new instance that contains the old parameter instance in its field
        Instance newParamInstance = new Instance(callSites, currentBB);
        boolean setLastRef = paramRef.getInstance().getLastReference() == paramRef;
        newParamInstance.setField(undecidableName, "Unknown-Type", callSites, paramRef.getInstance(), setLastRef, true);
        
        // new parameter reference
        paramRef = new Reference(paramRef.getName(), paramRef.getType(), 
          paramRef.getCallSites(), newParamInstance, paramRef.getDeclaringInstance(), true);
        
        // add new references to refMap
        addRefToRefMap(newRefMap, paramRef);
      }

      // save the parameter-argument mapping
      String fieldName = "v" + (i + 1) + "_" + paramRef.getName();
      instance.setField(fieldName, paramRef.getType(), callSites, paramRef.getInstances(), false, true);
    }

    Reference fieldRef = new Reference(invocation.toString(), invocationType, "", instance, null, true); // static field also goes to "" callSites
    addRefToRefMap(newRefMap, fieldRef);
    
    // not void return
    if (!invocationType.equals("V")) {
      Reference defRef = new Reference(def, invocationType, callSites, fieldRef.getInstance(), null, false);
      
      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);
    }

    return new Formula(preCond);
  }
  
  public Formula handle_invokeinterface_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, preCond, inst, instInfo, callStack, curInvokeDepth);
  }

  public Formula handle_invokevirtual_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, preCond, inst, instInfo, callStack, curInvokeDepth);
  }

  public Formula handle_invokespecial_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return handle_invokenonstatic_stepin(execOptions, caller, preCond, inst, instInfo, callStack, curInvokeDepth);
  }
  
  //XXX we use the composition way to create inter-procedural summary
  // go into invocation
  private Formula handle_invokenonstatic_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return null;
  }

  //XXX we use the composition way to create inter-procedural summary
  // go into invocation
  public Formula handle_invokestatic_stepin(ExecutionOptions execOptions, CGNode caller, 
      Formula postCond, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, int curInvokeDepth) {
    return null;
  }
  
  public Formula handle_load_metadata(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSALoadMetadataInstruction loadMetaInst                   = (SSALoadMetadataInstruction) inst;

    // the variable(result) define by the load_metadata instruction
    String def = getSymbol(loadMetaInst.getDef(), methData, callSites, newDefMap, true);
    
    Object token = loadMetaInst.getToken();
    String metaDataType = loadMetaInst.getType().getName().toString(); 
    
    if (token instanceof TypeReference && metaDataType.equals("Ljava/lang/Class")) { // a loadClass operation
      String loadClass = ((TypeReference) token).getName().toString() + ".class";
      Reference loadClassRef = findOrCreateReference(loadClass, metaDataType, callSites, currentBB, newRefMap);

      Reference defRef = new Reference(def, metaDataType, callSites, loadClassRef.getInstance(), null, false);

      // add new references to refMap
      addRefToRefMap(newRefMap, defRef);
      // since there is a new def, add to defMap
      addDefToDefMap(newDefMap, defRef);

      return new Formula(preCond);
    }
    else {
      return defaultHandler(preCond, inst, instInfo);
    }
  }
  
  public Formula handle_monitorenter(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  public Formula handle_monitorexit(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  public Formula handle_neg(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAUnaryOpInstruction unaryInst                           = (SSAUnaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def = getSymbol(unaryInst.getDef(), methData, callSites, newDefMap, true);
    String var = getSymbol(unaryInst.getUse(0), methData, callSites, newDefMap, false);
    
    Reference varRef = findOrCreateReference(var, "I", callSites, currentBB, newRefMap);
    
    Instance unaryOp = null;
    switch ((IUnaryOpInstruction.Operator) unaryInst.getOpcode()) {
    case NEG:   /* the only one */
      unaryOp = new Instance(new Instance("#!0", "I", currentBB), INSTANCE_OP.SUB, varRef.getInstance(), currentBB);
      break;
    }

    Reference defRef = new Reference(def, "I", callSites, unaryOp, null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }

  public Formula handle_new(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSANewInstruction newInst                                 = (SSANewInstruction) inst;

    String def = getSymbol(newInst.getDef(), methData, callSites, newDefMap, true);
    
    String newType = newInst.getConcreteType().getName().toString();
    
    // the variable define by the new instruction
    long instanceID  = System.nanoTime();
    String freshInst = "FreshInstanceOf(" + newType + "_" + instanceID + ")";
    Instance newInstance = new Instance(freshInst, newType, currentBB);
    
    Reference defRef = new Reference(def, newType, callSites, newInstance, null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);
    
    // for array types, we also need to substitute ".length" field
    if (newInst.getConcreteType().isArrayType()) {
      String valSize = getSymbol(newInst.getUse(0), methData, callSites, newDefMap, false);
      Reference valSizeRef = findOrCreateReference(valSize, "I", callSites, currentBB, newRefMap);
      defRef.getInstance().setField("length", "I", callSites, valSizeRef.getInstances(), false, true);
      
      // assign initial values to array elements, XXX currently only works for constant size array
      if (valSize.startsWith("#!")) {
        TypeReference elemType = newInst.getConcreteType().getArrayElementType();
        String val = elemType.isPrimitiveType() ? "#!0" /* number or boolean(false) */ : "null";
        Reference valRef = findOrCreateReference(val, elemType.getName().toString(), "", currentBB, newRefMap);
        
        int size = Integer.parseInt(valSize.substring(2));
        for (int i = 0; i < size; i++) {
          Reference indexRef = findOrCreateReference("#!" + i, "I", "", currentBB, newRefMap);
          Relation relation = preCond.getRelation("@@array");
          relation.update(new Instance[] {defRef.getInstance(), indexRef.getInstance()}, valRef.getInstance());
        }
      }
    }
//    // initialize the default values of each member fields // done in the entry block of <init> already
//    else if (newInst.getConcreteType().isClassType()) {
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
//          Reference valRef = new Reference(val, fieldType, callSites, valInstance, null, true);
//          defRef.getInstance().setField(fieldName, fieldType, callSites, valRef.getInstances(), false, true);
//        }
//      }
//    }

    return new Formula(preCond);
  }

  public Formula handle_phi(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();
    SSAPhiInstruction phiInst                                 = (SSAPhiInstruction) inst;

    // decide phi according to predecessor
    int index = Util.whichPred(methData.getcfg(), instInfo.previousBB, instInfo.currentBB);

    String def = getSymbol(phiInst.getDef(), methData, callSites, newDefMap, true);
    String var = getSymbol(phiInst.getUse(index), methData, callSites, newDefMap, false);

    Reference phiRef = findOrCreateReference(var, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference defRef = new Reference(def, "Unknown-Type", callSites, phiRef.getInstance(), null, false);
    
    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }
  
  public Formula handle_phi(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, int phiVarID, ISSABasicBlock predBB) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  // handler for pi instruction
  public Formula handle_pi(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                         = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                                = instInfo.callSites;
    ISSABasicBlock currentBB                                        = instInfo.currentBB;
    MethodMetaData methData                                         = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap       = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap         = preCond.getDefMap();
    SSAPiInstruction piInst                                         = (SSAPiInstruction) inst;

    String def = getSymbol(piInst.getDef(), methData, callSites, newDefMap, true);
    String val = getSymbol(piInst.getVal(), methData, callSites, newDefMap, false);
    
    // add new references to refMap
    Reference valRef = findOrCreateReference(val, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference defRef = new Reference(def, "Unknown-Type", callSites, valRef.getInstance(), null, false);

    // add new references to refMap
    addRefToRefMap(newRefMap, defRef);
    // since there is a new def, add to defMap
    addDefToDefMap(newDefMap, defRef);

    return new Formula(preCond);
  }
  
  // handler for putfield instruction
  public Formula handle_putfield(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAPutInstruction putfieldInst                            = (SSAPutInstruction) inst;

    // the variable(result) define by the putfield instruction
    String ref = getSymbol(putfieldInst.getUse(0), methData, callSites, preCond.getDefMap(), false);
    String val = getSymbol(putfieldInst.getUse(1), methData, callSites, preCond.getDefMap(), false);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    switch (instInfo.controlType) {
    case Formula.NORMAL_SUCCESSOR:
      // new condition: ref != null
      String refTypeName = putfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      Reference refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, newRefMap);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      String fieldType = putfieldInst.getDeclaredFieldType().getName().toString();
      String fieldName = putfieldInst.getDeclaredField().getName().toString();
      Reference valRef = findOrCreateReference(val, fieldType, callSites, currentBB, newRefMap);
      
      // we maintain all previous instances that were at this field
      Reference fieldRef = refRef.getInstance().getField(fieldName);
      if (fieldRef != null) {
        fieldRef.putInstancesToOld();
      }
      refRef.getInstance().setField(fieldName, fieldType, callSites, valRef.getInstances(), false, false);
      break;
    case Formula.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      // new condition: ref == null
      refTypeName = putfieldInst.getDeclaredField().getDeclaringClass().getName().toString();
      refRef  = findOrCreateReference(ref, refTypeName, callSites, currentBB, newRefMap);
      nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(refRef.getInstance(), Comparator.OP_EQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));

      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      //newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }
  
  // handler for putstatic instruction
  public Formula handle_putstatic(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAPutInstruction putstaticInst                           = (SSAPutInstruction) inst;

    String val = getSymbol(putstaticInst.getUse(0), methData, callSites, preCond.getDefMap(), false);

    String refTypeName = putstaticInst.getDeclaredField().getDeclaringClass().getName().toString();
    Reference refRef  = findOrCreateReference(refTypeName, refTypeName, "", currentBB, newRefMap); // static field also goes to "" callSites
    String fieldType = putstaticInst.getDeclaredFieldType().getName().toString();
    String fieldName = putstaticInst.getDeclaredField().getName().toString();
    Reference valRef = findOrCreateReference(val, fieldType, callSites, currentBB, newRefMap);
    
    // we maintain all previous instances that were at this field
    Reference fieldRef = refRef.getInstance().getField(fieldName);
    if (fieldRef != null) {
      fieldRef.putInstancesToOld();
    }
    refRef.getInstance().setField(fieldName, fieldType, callSites, valRef.getInstances(), false, false);
    
    // add new references to refMap
    addRefToRefMap(newRefMap, refRef);
    
    return new Formula(preCond);
  }

  public Formula handle_return(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAReturnInstruction returnInst                           = (SSAReturnInstruction) inst;
    
    if (!returnInst.returnsVoid()) {
      // the return value of the instruction
      String ret = getSymbol(returnInst.getResult(), methData, callSites, preCond.getDefMap(), false);

      String returnType = methData.getIR().getMethod().getReturnType().getName().toString();
      
      // add "RET"
      Reference returnRef = findOrCreateReference(ret, "Unknown-Type", callSites, currentBB, newRefMap);
      Reference retRef    = new Reference("RET", returnType, callSites, returnRef.getInstance(), null, false);
  
      // add new references to refMap
      addRefToRefMap(newRefMap, retRef);      
    }
    return new Formula(preCond);
  }

  public Formula handle_switch(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
  
  public Formula handle_switch(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo, ISSABasicBlock successor) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSASwitchInstruction switchInst                           = (SSASwitchInstruction) inst;

    // get the variables of the switch statement,
    // the variables might be constant numbers!
    String var1 = getSymbol(switchInst.getUse(0), methData, callSites, preCond.getDefMap(), false);
    Reference var1Ref = findOrCreateReference(var1, "I", callSites, currentBB, newRefMap);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();

    // create switch SMTStatement
    int label = successor.getFirstInstructionIndex();
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

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }
  
  // handler for throw instruction
  public Formula handle_throw(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    SSAThrowInstruction throwInst                             = (SSAThrowInstruction) inst;

    // the variable(result) thrown by throw instruction
    String exception = getSymbol(throwInst.getUse(0), methData, callSites, preCond.getDefMap(), false);

    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    
    // new condition: excepRef != null
    Reference excepRef = findOrCreateReference(exception, "Unknown-Type", callSites, currentBB, newRefMap);
    Reference nullRef  = findOrCreateReference("null", "", "", currentBB, newRefMap);
    conditionTerms = new ArrayList<ConditionTerm>();
    conditionTerms.add(new BinaryConditionTerm(excepRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
    conditionList.add(new Condition(conditionTerms));
    
    // add "ThrownInstCurrent " flag to varMap, indicating an exception is
    // thrown at the current method, but we will not check if it is the
    // exception we are looking for, because we cannot finalize exception 
    // variable at the moment. We will check it after we exit the current method
    //newVarMap = setExceptionThrownCurrent(postCond, newVarMap, exception);

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }

  public Formula handle_entryblock(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    preCond                                                   = preCond == instInfo.formula4BB ? preCond.clone() : preCond;
    String callSites                                          = instInfo.callSites;
    ISSABasicBlock currentBB                                  = instInfo.currentBB;
    MethodMetaData methData                                   = instInfo.methData;
    Hashtable<String, Hashtable<String, Reference>> newRefMap = preCond.getRefMap();
    Hashtable<String, Hashtable<String, Integer>> newDefMap   = preCond.getDefMap();

    // at the entry block, all parameters are defined, and parameters' types are confirmed
    for (int i = 0, count = methData.getIR().getNumberOfParameters(); i < count; i++) {
      String paramName = getSymbol(methData.getIR().getParameter(i), methData, callSites, preCond.getDefMap(), false);
      String paramType = methData.getIR().getParameterType(i).getName().toString();
      Reference paramRef = findOrCreateReference(paramName, paramType, callSites, currentBB, newRefMap);
      paramRef.setType(paramType);

      // add new references to refMap
      addRefToRefMap(newRefMap, paramRef);
      
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
        Reference valRef = new Reference(val, fieldType, callSites, valInstance, null, true);
        
        Reference thisRef = findOrCreateReference("v1", "Unknown-Type", callSites, currentBB, newRefMap);
        thisRef.getInstance().setField(fieldName, fieldType, callSites, valRef.getInstances(), false, true);
      }
    }
    
    // for the outermost frame, need to add this != null manually
    List<ConditionTerm> conditionTerms = null;
    List<Condition> conditionList = new ArrayList<Condition>();
    if (instInfo.callSites.length() == 0 && !methData.isStatic()) {
      // new condition: this != null
      Reference thisRef = findOrCreateReference("v1", "Unknown-Type", callSites, currentBB, newRefMap);
      Reference nullRef = findOrCreateReference("null", "", "", currentBB, newRefMap);
      conditionTerms = new ArrayList<ConditionTerm>();
      conditionTerms.add(new BinaryConditionTerm(thisRef.getInstance(), Comparator.OP_INEQUAL, nullRef.getInstance())); 
      conditionList.add(new Condition(conditionTerms));
    }

    // add new conditions to condition list
    preCond.getConditionList().addAll(conditionList);
    return new Formula(preCond);
  }
  
  public Formula handle_exitblock(Formula preCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(preCond, inst, instInfo);
  }
}
