package hk.ust.cse.Prevision.InstructionHandler;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.Predicate;
import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.WeakestPrecondition.GlobalOptionsAndStates;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IComparisonInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IShiftInstruction;
import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
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
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.types.TypeReference;

public class CompleteBackwardHandler extends AbstractHandler {
  
  @SuppressWarnings("unchecked")
  public Predicate handle_arraylength(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAArrayLengthInstruction arrayLengthInst = (SSAArrayLengthInstruction) inst;

    // the variable(result) define by the arraylength instruction
    String def      = methData.getSymbol(arrayLengthInst.getDef(), instInfo.valPrefix, newDefMap);
    String arrayRef = methData.getSymbol(arrayLengthInst.getArrayRef(), instInfo.valPrefix, newDefMap);
    
    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayRef);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, null);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];

      // substitute def with arrayRef.length, because
      // def is not exist before this instruction
      String arrayLengthStr = arrayRef + ".length";
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, arrayLengthStr);
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayRef);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, null);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }
    
    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_arrayload(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAArrayLoadInstruction arrayLoadInst     = (SSAArrayLoadInstruction) inst;

    // the variable(result) define by the arrayload instruction
    String def        = methData.getSymbol(arrayLoadInst.getDef(), instInfo.valPrefix, newDefMap);
    String arrayRef   = methData.getSymbol(arrayLoadInst.getArrayRef(), instInfo.valPrefix, newDefMap);
    String arrayIndex = methData.getSymbol(arrayLoadInst.getIndex(), instInfo.valPrefix, newDefMap);
    
    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayRef);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);
      
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayIndex);
      smtStatement.add(">=");
      smtStatement.add("#!0");
      smtStatements.add(smtStatement);
      
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayIndex);
      smtStatement.add("<");
      smtStatement.add(arrayRef + ".length");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, arrayIndex);
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef + ".length", "#!0");

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];

      // substitute def with arrayRef[arrayIndex], because
      // def is not exist before this instruction
      StringBuilder arrayLoadStr = new StringBuilder();
      arrayLoadStr.append("(");
      arrayLoadStr.append(arrayLoadInst.getElementType().getName());
      arrayLoadStr.append(")");
      arrayLoadStr.append(arrayRef);
      arrayLoadStr.append("[");
      arrayLoadStr.append(arrayIndex);
      arrayLoadStr.append("]");
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, arrayLoadStr.toString());
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      TypeReference excepType = 
        methData.getExceptionType(instInfo.currentBB, instInfo.sucessorBB);
      
      String excepTypeStr = null;
      if (excepType != null) {
        excepTypeStr = excepType.getName().toString();
      }
      else {
        // try to find exception type string from postcond
        excepTypeStr = findCaughtExceptionTypeStr(postCond);
      }
      
      if (excepTypeStr != null) {
        if (excepTypeStr.equals("Ljava/lang/NullPointerException")) {
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayRef);
          smtStatement.add("==");
          smtStatement.add("null");
          smtStatements.add(smtStatement);

          // add new variables to varMap
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, null);
          
          // set caught variable into triggered variable, 
          // indicating the caught exception is trigger by the instruction
          newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
        }
        else if (excepTypeStr.equals("Ljava/lang/ArrayIndexOutOfBoundsException")) {
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayRef);
          smtStatement.add("!=");
          smtStatement.add("null");
          smtStatements.add(smtStatement);
          
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayIndex);
          smtStatement.add("<");
          smtStatement.add("#!0");
          smtStatement.add(arrayIndex);
          smtStatement.add(">=");
          smtStatement.add(arrayRef + ".length");
          smtStatements.add(smtStatement);
          
          // add new variables to varMap
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, arrayIndex);
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef + ".length", "#!0");
          
          // set caught variable into triggered variable, 
          // indicating the caught exception is trigger by the instruction
          newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ArrayIndexOutOfBoundsException");
        }
        else {
          // cannot decide which kind of exception it is!
        }
      }
      else {
        System.err.println("Failed to obtain the exception type string!");
      }
      break;
    }
    
    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  public Predicate handle_arraystore(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAArrayStoreInstruction arrayStoreInst   = (SSAArrayStoreInstruction) inst;

    String arrayRef   = methData.getSymbol(arrayStoreInst.getArrayRef(), instInfo.valPrefix, postCond.getDefMap());
    String arrayIndex = methData.getSymbol(arrayStoreInst.getIndex(), instInfo.valPrefix, postCond.getDefMap());
    String storeValue = methData.getSymbol(arrayStoreInst.getValue(), instInfo.valPrefix, postCond.getDefMap());
    
    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayRef);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayIndex);
      smtStatement.add(">=");
      smtStatement.add("#!0");
      smtStatements.add(smtStatement);

      smtStatement = new ArrayList<String>();
      smtStatement.add(arrayIndex);
      smtStatement.add("<");
      smtStatement.add(arrayRef + ".length");
      smtStatements.add(smtStatement);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, arrayIndex);
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef + ".length", "#!0");
      
      // substitute arrayRef[arrayIndex] with storeValue
      StringBuilder arrayStoreStr = new StringBuilder();
      arrayStoreStr.append("(");
      arrayStoreStr.append(arrayStoreInst.getElementType().getName());
      arrayStoreStr.append(")");
      arrayStoreStr.append(arrayRef);
      arrayStoreStr.append("[");
      arrayStoreStr.append(arrayIndex);
      arrayStoreStr.append("]");
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, arrayStoreStr.toString(), storeValue);
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      TypeReference excepType = 
        methData.getExceptionType(instInfo.currentBB, instInfo.sucessorBB);
      
      String excepTypeStr = null;
      if (excepType != null) {
        excepTypeStr = excepType.getName().toString();
      }
      else {
        // try to find exception type string from postcond
        excepTypeStr = findCaughtExceptionTypeStr(postCond);
      }
      
      if (excepTypeStr != null) {
        if (excepTypeStr.equals("Ljava/lang/NullPointerException")) {
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayRef);
          smtStatement.add("==");
          smtStatement.add("null");
          smtStatements.add(smtStatement);
  
          // add new variables to varMap
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, null);
          
          // set caught variable into triggered variable, 
          // indicating the caught exception is trigger by the instruction
          newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
        }
        else if (excepTypeStr.equals("Ljava/lang/ArrayIndexOutOfBoundsException")) {
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayRef);
          smtStatement.add("!=");
          smtStatement.add("null");
          smtStatements.add(smtStatement);
  
          smtStatement = new ArrayList<String>();
          smtStatement.add(arrayIndex);
          smtStatement.add("<");
          smtStatement.add("#!0");
          smtStatement.add(arrayIndex);
          smtStatement.add(">=");
          smtStatement.add(arrayRef + ".length");
          smtStatements.add(smtStatement);
          
          // add new variables to varMap
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, arrayIndex);
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef + ".length", "#!0");
          
          // set caught variable into triggered variable, 
          // indicating the caught exception is trigger by the instruction
          newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ArrayIndexOutOfBoundsException");
        }
        else {
          // cannot decide which kind of exception it is!
        }
      }
      else {
        System.err.println("Failed to obtain the exception type string!");
      }
      break;
    }
    
    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_binaryop(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSABinaryOpInstruction binaryOpInst       = (SSABinaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def  = methData.getSymbol(binaryOpInst.getDef(), instInfo.valPrefix, newDefMap);
    String var1 = methData.getSymbol(binaryOpInst.getUse(0), instInfo.valPrefix, newDefMap);
    String var2 = methData.getSymbol(binaryOpInst.getUse(1), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    if (newVarMap.containsKey(def)) {
      // create binaryOp SMTStatement
      String binaryOp = null;
      
      IBinaryOpInstruction.IOperator operator = binaryOpInst.getOperator();
      if (operator instanceof IBinaryOpInstruction.Operator) {
        switch ((IBinaryOpInstruction.Operator) operator) {
        case ADD:
          binaryOp = var1 + " + " + var2;
          break;
        case AND:
          binaryOp = var1 + " & " + var2;
          break;
        case DIV:
          binaryOp = var1 + " / " + var2;
          break;
        case MUL:
          binaryOp = var1 + " * " + var2;
          break;
        case OR:
          binaryOp = var1 + " | " + var2;
          break;
        case REM:
          binaryOp = var1 + " % " + var2;
          break;
        case SUB:
          binaryOp = var1 + " - " + var2;
          break;
        case XOR:
          binaryOp = var1 + " ^ " + var2;
          break;
        }
      }
      else if (operator instanceof IShiftInstruction.Operator) {
        switch ((IShiftInstruction.Operator) operator) {
        case SHL:
          binaryOp = var1 + " << " + var2;
          break;
        case SHR:
          binaryOp = var1 + " >> " + var2;
          break;
        case USHR:
          binaryOp = var1 + " >> " + var2;
          break;
        }
      }
      
      // def is not exist before binarayOp Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, binaryOp);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, var1, var2);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for catch instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_catch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                          = null;
    MethodMetaData methData                    = instInfo.methData;
    Hashtable<String, List<String>> newVarMap  = postCond.getVarMap();
    Hashtable<String, String> newPhiMap        = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap       = postCond.getDefMap();
    SSAGetCaughtExceptionInstruction catchInst = 
      ((ExceptionHandlerBasicBlock) instInfo.currentBB).getCatchInstruction();

    // the e defined by catch
    String def = methData.getSymbol(catchInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    // get the declared type of the exception
    TypeReference excepType = methData.getExceptionType(instInfo.currentBB);
    String excepTypeStr = excepType.getName().toString();
    
    // def is not exist before catch Instruction
    newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def,
        "FreshInstanceOf(" + excepTypeStr + ")");
    
    // add a caught variable to indicate "coming from a catch block of 
    // some exception type", and expect to meet an exception triggering point
    newVarMap = setExceptionCaught(postCond, newVarMap, excepTypeStr);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for checkcast instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_checkcast(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSACheckCastInstruction checkcastInst     = (SSACheckCastInstruction) inst;

    // the variable(result) define by the getfield instruction
    String def = methData.getSymbol(checkcastInst.getDef(), instInfo.valPrefix, newDefMap);
    String val = methData.getSymbol(checkcastInst.getUse(0), instInfo.valPrefix, newDefMap);
    String declaredResultType = checkcastInst.getDeclaredResultType().getName().toString();

    String subTypeStr = "subType(typeOf(" + val + ")," + declaredResultType + ")";

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(subTypeStr);
      smtStatement.add("==");
      smtStatement.add("true");
      smtStatement.add(val);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, val, subTypeStr);

      // def is not exist before checkcast Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, val);
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be CCE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(val);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      smtStatement = new ArrayList<String>();
      smtStatement.add(subTypeStr);
      smtStatement.add("==");
      smtStatement.add("false");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, val, subTypeStr);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/ClassCastException");
      break;
    }

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  @SuppressWarnings("unchecked")
  public Predicate handle_compare(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAComparisonInstruction compareInst      = (SSAComparisonInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def  = methData.getSymbol(compareInst.getDef(), instInfo.valPrefix, newDefMap);
    String var1 = methData.getSymbol(compareInst.getUse(0), instInfo.valPrefix, newDefMap);
    String var2 = methData.getSymbol(compareInst.getUse(1), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    if (newVarMap.containsKey(def)) {
      // create compareOp SMTStatement
      String compareOp = null;
      switch ((IComparisonInstruction.Operator) compareInst.getOperator()) {
      case CMP:   /* for long */
      case CMPL:  /* for float or double */
      case CMPG:  /* for float or double */
        compareOp = var1 + " - " + var2;
        break;
      }

      // def is not exist before binarayOp Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, compareOp);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, var1, var2);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_conversion(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAConversionInstruction convInst         = (SSAConversionInstruction) inst;

    // the variable(result) define by the conversion instruction
    String toVal    = methData.getSymbol(convInst.getDef(), instInfo.valPrefix, newDefMap);
    String fromVal  = methData.getSymbol(convInst.getUse(0), instInfo.valPrefix, newDefMap);
    String fromType = convInst.getFromType().getName().toString();
    String toType   = convInst.getToType().getName().toString();    
    
    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, toVal);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    if (newVarMap.containsKey(toVal)) {
      if (fromType.equals("I") || // from integer to float
          fromType.equals("J") || 
          fromType.equals("S")) {
        // add new variables to varMap
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, fromVal, null);
        
        // toVal is not exist before conversion Instruction
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, toVal, fromVal);
      }
      else if (fromType.equals("D") || // from float to integer
               fromType.equals("F")) {
        if (toType.equals("I") || 
            toType.equals("J") || 
            toType.equals("S")) {
          
          String convVal = null;
          if (fromVal.startsWith("#!")) { // it is a constant number
            int index = fromVal.lastIndexOf('.');
            if (index >= 0) {
              convVal = fromVal.substring(0, index);
            }
            else {
              convVal = fromVal;
            }
          }
          else {
            // create a converted val
            convVal = fromVal + "$1" /* first kind of conversion */;
            
            if (!newVarMap.containsKey(convVal)) {
              // the converted integer should be: fromVal - 1 < convVal <= fromVal
              List<String> smtStatement = null;
              smtStatement = new ArrayList<String>();
              smtStatement.add(convVal);
              smtStatement.add("<=");
              smtStatement.add(fromVal);
              smtStatements.add(smtStatement);
  
              smtStatement = new ArrayList<String>();
              smtStatement.add(convVal + " + #!1");
              smtStatement.add(">");
              smtStatement.add(fromVal);
              smtStatements.add(smtStatement);
              
              // add new variables to varMap
              newVarMap = addVars2VarMap(postCond, methData, newVarMap, fromVal, null);
              newVarMap = addVars2VarMap(postCond, methData, newVarMap, convVal, null);
              newVarMap = addVars2VarMap(postCond, methData, newVarMap, convVal + " + #!1", null);           
            }
          }
          
          // toVal is not exist before conversion Instruction
          newVarMap = substituteVarMapKey(postCond, methData, newVarMap, toVal, convVal);
        }
        else if (toType.equals("D") || 
                 toType.equals("F")) {
          // add new variables to varMap
          newVarMap = addVars2VarMap(postCond, methData, newVarMap, fromVal, null);
          
          // toVal is not exist before conversion Instruction
          newVarMap = substituteVarMapKey(postCond, methData, newVarMap, toVal, fromVal);
        }
      }
      else {
        // not implement
      }
    }
    
    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);
    
    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  public Predicate handle_conditional_branch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                              = null;
    MethodMetaData methData                        = instInfo.methData;
    Hashtable<String, List<String>> newVarMap      = postCond.getVarMap();
    SSAConditionalBranchInstruction condBranchInst = (SSAConditionalBranchInstruction) inst;

    // check whether or not the conditional branch has been taken
    // the branch instruction will always be the last instruction
    // of the current block, so we can check whether the branch has
    // been taken or not by checking the successor bb number
    boolean tookBranch = true;
    if (instInfo.sucessorBB == null) {
      // the first analyzing statement is a conditional statement,
      // we have no idea if the branch is taken or not
      tookBranch = false;
    }
    else if (instInfo.currentBB.getNumber() + 1 == instInfo.sucessorBB.getNumber()) {
      tookBranch = false;
    }

    // get the variables of the conditional branch,  
    // the variables might be constant numbers!
    String var1 = methData.getSymbol(condBranchInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());
    String var2 = methData.getSymbol(condBranchInst.getUse(1), instInfo.valPrefix, postCond.getDefMap());

    List<String> conditionalTerm = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();

    // create conditional branch SMTStatement
    conditionalTerm = new ArrayList<String>();
    conditionalTerm.add(var1);
    switch ((IConditionalBranchInstruction.Operator) condBranchInst.getOperator()) {
    case EQ:
      if (tookBranch) {
        conditionalTerm.add("==");
      }
      else {
        conditionalTerm.add("!=");
      }
      break;
    case GE:
      if (tookBranch) {
        conditionalTerm.add(">=");
      }
      else {
        conditionalTerm.add("<");
      }
      break;
    case GT:
      if (tookBranch) {
        conditionalTerm.add(">");
      }
      else {
        conditionalTerm.add("<=");
      }
      break;
    case LE:
      if (tookBranch) {
        conditionalTerm.add("<=");
      }
      else {
        conditionalTerm.add(">");
      }
      break;
    case LT:
      if (tookBranch) {
        conditionalTerm.add("<");
      }
      else {
        conditionalTerm.add(">=");
      }
      break;
    case NE:
      if (tookBranch) {
        conditionalTerm.add("!=");
      }
      else {
        conditionalTerm.add("==");
      }
      break;
    }
    conditionalTerm.add(var2);
    smtStatements.add(conditionalTerm);

    // add binaryOp statement
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);
    
    // add new variables to varMap
    newVarMap = addVars2VarMap(postCond, methData, newVarMap, var1, var2);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  // handler for getfield instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_getfield(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAGetInstruction getfieldInst            = (SSAGetInstruction) inst;

    // the variable(result) define by the getfield instruction
    String def = methData.getSymbol(getfieldInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(getfieldInst.getUse(0), instInfo.valPrefix, newDefMap);

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      // the variable define by the getfield instruction
      if (newVarMap.containsKey(def)) {
        // get the fieldType of the declared field of the getstatic instruction
        String declaredField = "(" + getfieldInst.getDeclaredFieldType().getName() + ")";
        // get the class type that declared this field
        declaredField += ref;
        // get the name of the field
        declaredField += "." + getfieldInst.getDeclaredField().getName();
        // def is not exist before getstatic Instruction
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, declaredField);
      }
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  // handler for getstatic instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_getstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAGetInstruction getstaticInst           = (SSAGetInstruction) inst;

    String def = methData.getSymbol(getstaticInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    // the variable define by the getstatic instruction
    if (newVarMap.containsKey(def)) {
      // get the fieldType of the declared field of the getstatic instruction
      String declaredField = "(" + getstaticInst.getDeclaredFieldType().getName() + ")";
      // get the class type that declared this field
      declaredField += getstaticInst.getDeclaredField().getDeclaringClass().getName();
      // get the name of the field
      declaredField += "." + getstaticInst.getDeclaredField().getName();
      // def is not exist before getstatic Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, declaredField);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  public Predicate handle_goto(Predicate postCond, SSAInstruction inst,
      BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  // handler for instanceof instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_instanceof(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAInstanceofInstruction instanceofInst   = (SSAInstanceofInstruction) inst;

    String def = methData.getSymbol(instanceofInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(instanceofInst.getRef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    // the variable define by the instanceofInst instruction
    if (newVarMap.containsKey(def)) {
      // get the ref
      String checkedType = ref + " isInstanceOf";
      // get the checkedType that ref is going to check against
      checkedType += "(" + instanceofInst.getCheckedType().getName() + ")";
      // def is not exist before instanceofInst Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, checkedType);
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  public Predicate handle_invokeinterface(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  public Predicate handle_invokevirtual(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  public Predicate handle_invokespecial(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }
  
  @SuppressWarnings("unchecked")
  private Predicate handle_invokenonstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAInvokeInstruction invokeInst           = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterface/invokespecial/invokevirtual instruction
    String def = methData.getSymbol(invokeInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokeInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokeInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokeInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      // the variable define by the invokeinterface/invokespecial/invokevirtual instruction
      if (newVarMap.containsKey(def)) {
        StringBuilder invocation = new StringBuilder();
        // get the fieldType of the declared field of the invokeinterface/invokespecial/invokevirtual instruction
        invocation.append("(" + invokeInst.getDeclaredResultType().getName() + ")");
        // get the class type that declared this field
        invocation.append(ref);
        // get the name of the field
        invocation.append("." + invokeInst.getDeclaredTarget().getSelector().getName());
        // get the parameters
        invocation.append("(");
        for (int i = 0; i < params.size(); i++) {
          invocation.append(params.get(i));
          if (i != params.size() - 1) {
            invocation.append(", ");
          }
        }
        invocation.append(");");
        
        // def is not exist before invokeinterface/invokespecial/invokevirtual Instruction
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, invocation.toString());
        // add new variables to varMap
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, params);
      }
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  // simple implementation, do not consider call graph
  @SuppressWarnings("unchecked")
  public Predicate handle_invokestatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAInvokeInstruction invokestaticInst     = (SSAInvokeInstruction) inst;

    String def = methData.getSymbol(invokestaticInst.getDef(), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(methData.getSymbol(invokestaticInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    // the variable define by the invokestatic instruction
    if (newVarMap.containsKey(def)) {
      StringBuilder invocation = new StringBuilder();
      // get the fieldType of the declared field of the invokestatic instruction
      invocation.append("(" + invokestaticInst.getDeclaredResultType().getName() + ")");
      // get the class type that declared this field
      invocation.append(invokestaticInst.getDeclaredTarget().getDeclaringClass().getName());
      // get the name of the field
      invocation.append("." + invokestaticInst.getDeclaredTarget().getSelector().getName());
      // get the parameters
      invocation.append("(");
      for (int i = 0; i < params.size(); i++) {
        invocation.append(params.get(i));
        if (i != params.size() - 1) {
          invocation.append(", ");
        }
      }
      invocation.append(");");

      // def is not exist before getstatic Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, invocation.toString());
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, params);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  public Predicate handle_invokeinterface_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokenonstatic_stepin(optionsAndStates, caller, postCond, 
        inst, instInfo, callStack, curInvokeDepth, usedPredicates);
  }

  public Predicate handle_invokevirtual_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokenonstatic_stepin(optionsAndStates, caller, postCond, 
        inst, instInfo, callStack, curInvokeDepth, usedPredicates);
  }

  public Predicate handle_invokespecial_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokenonstatic_stepin(optionsAndStates, caller, postCond, 
        inst, instInfo, callStack, curInvokeDepth, usedPredicates);
  }
  
  // go into invocation
  @SuppressWarnings("unchecked")
  private Predicate handle_invokenonstatic_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAInvokeInstruction invokeInst           = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterface/invokevirtual/invokespecial instruction
    String def = methData.getSymbol(invokeInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokeInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokeInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokeInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // add smtStatments to smtStatement list
      List<List<String>> newSMTStatements = addSMTStatments(
          postCond.getSMTStatements(), smtStatements);
      
      // compute valPrefix for the new method
      String newValPrefix = instInfo.valPrefix
          + String.format("%04d", invokeInst.getProgramCounter());

      // add ref (v1 == this)
      String newRef = "v" + newValPrefix + "1";

      // map parameters to method
      List<String> newParams = new ArrayList<String>();
      newVarMap = mapParamsToMethod(invokeInst, instInfo, methData, ref,
          def, newValPrefix, newRef, params, newParams, newVarMap, postCond);
      
      // create new postCond
      Predicate newPostCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
      
      // save used postCond, this will be used later to decide whether
      // or not we can pop the basic block(containing the invoke instruction)
      SimpleEntry<String, Predicate> used = 
        new SimpleEntry<String, Predicate>(newValPrefix, newPostCond);
      usedPredicates.add(used);
      
      // different handling mechanisms for ordinary invocations and entering call stacks
      if (optionsAndStates.isEnteringCallStack()) {
        // save this invoke instruction
        instInfo.wp.saveCallStackInvokeInst(instInfo, inst);

        // compute targeting method to enter call stack
        preCond = computeToEnterCallSite(invokeInst, instInfo, optionsAndStates, 
            caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
      }
      else {
        // compute targeting method with startLine = -1 (from exit block)
        preCond = computeAtCallSite(invokeInst, instInfo, optionsAndStates, 
            caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
      }

      // if succeed
      if (preCond != null) {
        newVarMap = preCond.getVarMap();

        // set params back
        newVarMap = mapParamsFromMethod(methData, ref, newRef, params, newParams, newVarMap, preCond);

        preCond = new Predicate(preCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
        return preCond;
      }
      else {
        preCond = handle_invokenonstatic(postCond, inst, instInfo);
        return preCond;
      }
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  // go into invocation
  @SuppressWarnings("unchecked")
  public Predicate handle_invokestatic_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {

    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAInvokeInstruction invokestaticInst     = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokespecial instruction
    String def = methData.getSymbol(invokestaticInst.getDef(), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(methData.getSymbol(invokestaticInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    // compute valPrefix for the new method
    String newValPrefix = instInfo.valPrefix
        + String.format("%04d", invokestaticInst.getProgramCounter());

    // map parameters to method
    List<String> newParams = new ArrayList<String>();
    newVarMap = mapParamsToMethod(invokestaticInst, instInfo, methData, null,
        def, newValPrefix, null, params, newParams, newVarMap, postCond);
    
    // create new postCond
    Predicate newPostCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    
    // save used postCond, this will be used later to decide whether
    // or not we can pop the basic block(containing the invoke instruction)
    SimpleEntry<String, Predicate> used = new SimpleEntry<String, Predicate>(newValPrefix, newPostCond);
    usedPredicates.add(used);
    
    // different handling mechanisms for ordinary invocations and entering call stacks
    if (optionsAndStates.isEnteringCallStack()) {
      // save this invoke instruction
      instInfo.wp.saveCallStackInvokeInst(instInfo, inst);

      // compute targeting method to enter call stack
      preCond = computeToEnterCallSite(invokestaticInst, instInfo, optionsAndStates, 
          caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
    }
    else {
      // compute targeting method with startLine = -1 (from exit block)
      preCond = computeAtCallSite(invokestaticInst, instInfo, optionsAndStates, 
          caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
    }

    // if succeed
    if (preCond != null) {
      newVarMap = preCond.getVarMap();

      // set params back
      newVarMap = mapParamsFromMethod(methData, null, null, params, newParams, newVarMap, preCond);

      preCond = new Predicate(preCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
      return preCond;
    }
    else {
      preCond = handle_invokestatic(postCond, inst, instInfo);
      return preCond;
    }
  }
  
  public Predicate handle_monitorenter(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  public Predicate handle_monitorexit(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return defaultHandler(postCond, inst, instInfo);
  }
  
  @SuppressWarnings("unchecked")
  public Predicate handle_neg(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAUnaryOpInstruction unaryInst           = (SSAUnaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def = methData.getSymbol(unaryInst.getDef(), instInfo.valPrefix, newDefMap);
    String var = methData.getSymbol(unaryInst.getUse(0), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];
    
    if (newVarMap.containsKey(def)) {
      // create unaryOp SMTStatement
      String unaryOp = null;
      switch ((IUnaryOpInstruction.Operator) unaryInst.getOpcode()) {
      case NEG:   /* the only one */
        unaryOp = "#!0" + " - " + var;
        break;
      }

      // def is not exist before binarayOp Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, unaryOp);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, var, "#!0");
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_new(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSANewInstruction newInst                 = (SSANewInstruction) inst;
    
    String def = methData.getSymbol(newInst.getDef(), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    // the variable define by the new instruction
    if (newVarMap.containsKey(def)) {
      // get the declared type of the new Instruction
      String declaredType = newInst.getConcreteType().getName().toString();
      String freshInst    = "FreshInstanceOf(" + declaredType + ")";

      // def is not exist before new Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, freshInst);
    }
    
    // for array types, we also need to substitute ".length" variables
    if (newInst.getConcreteType().isArrayType()) {
      // name of ".length" variables
      String defLength = def + ".length";
      
      // substitute ".length" variables with the size variable
      String valSize = methData.getSymbol(newInst.getUse(0), instInfo.valPrefix, newDefMap);
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, defLength, valSize);
    }
    // initialize the default values of each member fields
    else if (newInst.getConcreteType().isClassType()) {
      
      IClass newClass = instInfo.wp.getWalaAnalyzer().getClassHierarchy().lookupClass(newInst.getConcreteType());
      if (newClass != null) {
        // get the declared type of the new Instruction
        String declaredType = newInst.getConcreteType().getName().toString();
        String freshInst    = "FreshInstanceOf(" + declaredType + ")";
        
        Collection<IField> fields = newClass.getAllInstanceFields();
        for (IField field : fields) {
          // put the default value according to the field type
          String ref = freshInst;
          String val = (field.getFieldTypeReference().isPrimitiveType()) ? "#!0" /* number or boolean(false)*/ : "null"; 

          for (String var : newVarMap.get(ref)) {
            // get the fieldType of the declared field
            String declaredField = "(" + field.getFieldTypeReference().getName() + ")";
            // get the class type that declared this field
            declaredField += var;
            // get the name of the field
            declaredField += "." + field.getName();
            // the member field
            newVarMap = substituteVarMapKey(postCond, methData, newVarMap, declaredField, val);
          }
        }
      }
    }
    
    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_phi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAPhiInstruction phiInst                 = (SSAPhiInstruction) inst;

    String def = methData.getSymbol(phiInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    if (newVarMap.containsKey(def)) {
      int len = phiInst.getNumberOfUses();
      for (int i = 0; i < len; i++) {
        int varID = phiInst.getUse(i);
        if (varID > 0) {
          String var = methData.getSymbol(varID, instInfo.valPrefix, newDefMap);
          //if (var.startsWith("v")) {
            if (newPhiMap == postCond.getPhiMap()) {
              newPhiMap = postCond.getPhiMapClone();
            }
            
            // add to phiMap
            // constants (and null) now are also added to phiMap. phiMap 
            // assignment for constants will be done in 
            // WeakestPrecondition.computeBB(), when we find the 
            // corresponding ShrikeCFG's ConstantInstruction
            newPhiMap.put(var, def);
          //}
        }
      }
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for pi instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_pi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();
    SSAPiInstruction piInst                   = (SSAPiInstruction) inst;

    String def = methData.getSymbol(piInst.getDef(), instInfo.valPrefix, newDefMap);
    String val = methData.getSymbol(piInst.getVal(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    // def is not exist before pi Instruction
    newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, val);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for putfield instruction
  public Predicate handle_putfield(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAPutInstruction putfieldInst            = (SSAPutInstruction) inst;

    // the variable(result) define by the putfield instruction
    String ref = methData.getSymbol(putfieldInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());
    String val = methData.getSymbol(putfieldInst.getUse(1), instInfo.valPrefix, postCond.getDefMap());

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    switch (instInfo.sucessorType) {
    case Predicate.NORMAL_SUCCESSOR:
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      for (String var : newVarMap.get(ref)) {
        // get the fieldType of the declared field of the putfield instruction
        String declaredField = "(" + putfieldInst.getDeclaredFieldType().getName() + ")";
        // get the class type that declared this field
        declaredField += var;
        // get the name of the field
        declaredField += "." + putfieldInst.getDeclaredField().getName();
        // declaredField is not exist before putfield Instruction
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, declaredField, val);      
      }
      break;
    case Predicate.EXCEPTIONAL_SUCCESSOR:
      /* can only be NPE */
      smtStatement = new ArrayList<String>();
      smtStatement.add(ref);
      smtStatement.add("==");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // set caught variable into triggered variable, 
      // indicating the caught exception is trigger by the instruction
      newVarMap = setExceptionTriggered(postCond, newVarMap, "Ljava/lang/NullPointerException");
      break;
    }

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }
  
  // handler for putstatic instruction
  public Predicate handle_putstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAPutInstruction putstaticInst           = (SSAPutInstruction) inst;

    String val = methData.getSymbol(putstaticInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());

    // get the fieldType of the declared field of the putstatic instruction
    String declaredField = "(" + putstaticInst.getDeclaredFieldType().getName() + ")";
    // get the class type that declared this field
    declaredField += putstaticInst.getDeclaredField().getDeclaringClass().getName();
    // get the name of the field
    declaredField += "." + putstaticInst.getDeclaredField().getName();
    // def is not exist before putstatic Instruction
    newVarMap = substituteVarMapKey(postCond, methData, newVarMap, declaredField, val);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  public Predicate handle_return(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAReturnInstruction returnInst           = (SSAReturnInstruction) inst;
    
    // the return value of the instruction
    String ret = methData.getSymbol(returnInst.getResult(), instInfo.valPrefix, postCond.getDefMap());

    // substitute "RET" given by caller
    newVarMap = substituteVarMapKey(postCond, methData, newVarMap, "RET", ret);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  public Predicate handle_switch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSASwitchInstruction switchInst           = (SSASwitchInstruction) inst;

    // get the variables of the switch statement,
    // the variables might be constant numbers!
    String var1 = methData.getSymbol(switchInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());

    List<String> switchTerm = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();

    // create switch SMTStatement
    int label = instInfo.sucessorBB.getFirstInstructionIndex();
    int[] casesAndLables = switchInst.getCasesAndLabels();

    List<String> newVars = new ArrayList<String>();
    // if is default label
    if (switchInst.getDefault() == label) {
      // to reach default label, no case should be matched
      for (int i = 0; i < casesAndLables.length; i += 2) {
        // cases should always be constant number
        String caseNum = "#!" + casesAndLables[i];
        switchTerm = new ArrayList<String>();
        switchTerm.add(var1);
        switchTerm.add("!=");
        switchTerm.add(caseNum);
        smtStatements.add(switchTerm);
        newVars.add(caseNum);
      }
    }
    else {
      for (int i = 1; i < casesAndLables.length; i += 2) {
        // found the switch case that leads to the label
        if (casesAndLables[i] == label) {
          // cases should always be constant number
          String caseNum = "#!" + casesAndLables[i - 1];
          switchTerm = new ArrayList<String>();
          switchTerm.add(var1);
          switchTerm.add("==");
          switchTerm.add(caseNum);
          smtStatements.add(switchTerm);
          newVars.add(caseNum);
          break;
        }
      }
    }
    newVars.add(var1);

    // add binaryOp statment
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    // add new variables to varMap
    newVarMap = addVars2VarMap(postCond, methData, newVarMap, newVars);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }
  
  // handler for throw instruction
  public Predicate handle_throw(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAThrowInstruction throwInst             = (SSAThrowInstruction) inst;

    // the variable(result) thrown by throw instruction
    String exception = methData.getSymbol(throwInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();

    smtStatement = new ArrayList<String>();
    smtStatement.add(exception);
    smtStatement.add("!=");
    smtStatement.add("null");
    smtStatements.add(smtStatement);

    // add new variables to varMap
    newVarMap = addVars2VarMap(postCond, methData, newVarMap, exception, null);
    
    // add "ThrownInstCurrent " flag to varMap, indicating an exception is
    // thrown at the current method, but we will not check if it is the
    // exception we are looking for, because we cannot finalize exception 
    // variable at the moment. We will check it after we exit the current method
    newVarMap = setExceptionThrownCurrent(postCond, newVarMap, exception);

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public Predicate handle_entryblock(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();

    // at the entry block, all parameters are defined
    Hashtable<String, String> paramMap = methData.getParamMap();
    Enumeration<String> keys = paramMap.keys();
    while (keys.hasMoreElements()) {
      String valnum = (String) keys.nextElement();
      
      // add valPrefix
      valnum = "v" + instInfo.valPrefix + valnum.substring(1);

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, valnum);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
    }
    
    // at the entry block, check if the caught exception is thrown
    newVarMap = checkExceptionThrown(postCond, newVarMap);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for ShrikeCFG ConstantInstruction instruction
  @SuppressWarnings("unchecked")
  public Predicate handle_constant(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, String constantStr) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap       = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap      = postCond.getDefMap();

    // assign concrete variable to phi variable
    Hashtable<String, ?>[] rets =
      (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, constantStr);
    newVarMap = (Hashtable<String, List<String>>) rets[0];
    newPhiMap = (Hashtable<String, String>) rets[1];
    newDefMap = (Hashtable<String, Integer>) rets[2];

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
}
