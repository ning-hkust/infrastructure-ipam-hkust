package hk.ust.cse.Prevision;

import hk.ust.cse.Prevision.Wala.CallGraph;
import hk.ust.cse.Prevision.Wala.MethodMetaData;
import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.WeakestPrecondition.GlobalOptionsAndStates;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;

import com.ibm.wala.classLoader.CallSiteReference;
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
import com.ibm.wala.ssa.SSACFG.ExceptionHandlerBasicBlock;
import com.ibm.wala.types.TypeReference;

public class InstHandler {
  
  @SuppressWarnings("unchecked")
  public static Predicate handle_arraylength(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);

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
  public static Predicate handle_arrayload(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAArrayLoadInstruction arrayLoadInst = (SSAArrayLoadInstruction) inst;

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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);

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
  
  public static Predicate handle_arraystore(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAArrayStoreInstruction arrayStoreInst = (SSAArrayStoreInstruction) inst;

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

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(),
        postCond.getDefMap());
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public static Predicate handle_binaryop(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSABinaryOpInstruction binaryOpInst = (SSABinaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def  = methData.getSymbol(binaryOpInst.getDef(), instInfo.valPrefix, newDefMap);
    String var1 = methData.getSymbol(binaryOpInst.getUse(0), instInfo.valPrefix, newDefMap);
    String var2 = methData.getSymbol(binaryOpInst.getUse(1), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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
  public static Predicate handle_catch(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAGetCaughtExceptionInstruction catchInst = 
      ((ExceptionHandlerBasicBlock) instInfo.currentBB).getCatchInstruction();

    // the e defined by catch
    String def = methData.getSymbol(catchInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
    // get the declared type of the exception
    TypeReference excepType = methData.getExceptionType(instInfo.currentBB);
    String excepTypeStr = excepType.getName().toString();
    
    // the variable define by the new instruction
    if (newVarMap.containsKey(def)) {
      // def is not exist before catch Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def,
          "FreshInstanceOf(" + excepTypeStr + ")");
    }
    
    // add a caught variable to indicate "coming from a catch block of 
    // some exception type", and expect to meet an exception triggering point
    newVarMap = setExceptionCaught(postCond, newVarMap, excepTypeStr);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for checkcast instruction
  @SuppressWarnings("unchecked")
  public static Predicate handle_checkcast(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSACheckCastInstruction checkcastInst = (SSACheckCastInstruction) inst;

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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, val, subTypeStr);

      // the variable define by the checkcast instruction
      if (newVarMap.containsKey(def)) {
        // def is not exist before checkcast Instruction
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, val);
      }
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
  public static Predicate handle_compare(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAComparisonInstruction compareInst = (SSAComparisonInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def  = methData.getSymbol(compareInst.getDef(), instInfo.valPrefix, newDefMap);
    String var1 = methData.getSymbol(compareInst.getUse(0), instInfo.valPrefix, newDefMap);
    String var2 = methData.getSymbol(compareInst.getUse(1), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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
  public static Predicate handle_conversion(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAConversionInstruction convInst = (SSAConversionInstruction) inst;

    // the variable(result) define by the conversion instruction
    String toVal    = methData.getSymbol(convInst.getDef(), instInfo.valPrefix, newDefMap);
    String fromVal  = methData.getSymbol(convInst.getUse(0), instInfo.valPrefix, newDefMap);
    String fromType = convInst.getFromType().getName().toString();
    String toType   = convInst.getToType().getName().toString();    
    
    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, toVal);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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
  
  public static Predicate handle_conditional_branch(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
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

    // add binaryOp statment
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);
    
    // add new variables to varMap
    newVarMap = addVars2VarMap(postCond, methData, newVarMap, var1, var2);

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), 
        postCond.getDefMap());
    return preCond;
  }

  // handler for getfield instruction
  @SuppressWarnings("unchecked")
  public static Predicate handle_getfield(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAGetInstruction getfieldInst = (SSAGetInstruction) inst;

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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
      
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
  public static Predicate handle_getstatic(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAGetInstruction getstaticInst = (SSAGetInstruction) inst;

    String def = methData.getSymbol(getstaticInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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

  public static Predicate handle_goto(Predicate postCond, SSAInstruction inst,
      BBorInstInfo instInfo) {
    Predicate preCond = null;

    // not implement
    preCond = new Predicate(postCond.getSMTStatements(), postCond.getVarMap(),
        postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }
  
  // handler for instanceof instruction
  @SuppressWarnings("unchecked")
  public static Predicate handle_instanceof(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInstanceofInstruction instanceofInst = (SSAInstanceofInstruction) inst;

    String def = methData.getSymbol(instanceofInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(instanceofInst.getRef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

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
  
  // simple implementation, do not consider call graph
  @SuppressWarnings("unchecked")
  public static Predicate handle_invokeinterface(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokeinterfaceInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokeinterfaceInst instruction
    String def = methData.getSymbol(invokeinterfaceInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokeinterfaceInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokeinterfaceInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokeinterfaceInst.getUse(i), instInfo.valPrefix, newDefMap));
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      // the variable define by the invokeinterfaceInst instruction
      if (newVarMap.containsKey(def)) {
        StringBuilder invocation = new StringBuilder();
        // get the fieldType of the declared field of the invokevirtual instruction
        invocation.append("(" + invokeinterfaceInst.getDeclaredResultType().getName() + ")");
        // get the class type that declared this field
        invocation.append(ref);
        // get the name of the field
        invocation.append("." + invokeinterfaceInst.getDeclaredTarget().getSelector().getName());
        // get the parameters
        invocation.append("(");
        for (int i = 0; i < params.size(); i++) {
          invocation.append(params.get(i));
          if (i != params.size() - 1) {
            invocation.append(", ");
          }
        }
        invocation.append(");");
        
        // def is not exist before invokeinterfaceInst Instruction
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
  public static Predicate handle_invokevirtual(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokevirtualInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokevirtual instruction
    String def = methData.getSymbol(invokevirtualInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokevirtualInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokevirtualInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokevirtualInst.getUse(i), instInfo.valPrefix, newDefMap));
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      // the variable define by the invokevirtual instruction
      if (newVarMap.containsKey(def)) {
        StringBuilder invocation = new StringBuilder();
        // get the fieldType of the declared field of the invokevirtual instruction
        invocation.append("(" + invokevirtualInst.getDeclaredResultType().getName() + ")");
        // get the class type that declared this field
        invocation.append(ref);
        // get the name of the field
        invocation.append("." + invokevirtualInst.getDeclaredTarget().getSelector().getName());
        // get the parameters
        invocation.append("(");
        for (int i = 0; i < params.size(); i++) {
          invocation.append(params.get(i));
          if (i != params.size() - 1) {
            invocation.append(", ");
          }
        }
        invocation.append(");");
        
        // def is not exist before invokevirtual Instruction
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
  public static Predicate handle_invokespecial(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokespecialInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokespecial instruction
    String def = methData.getSymbol(invokespecialInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokespecialInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokespecialInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokespecialInst.getUse(i), instInfo.valPrefix, newDefMap));
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
      
      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);

      // the variable define by the invokespecial instruction
      if (newVarMap.containsKey(def)) {
        StringBuilder invocation = new StringBuilder();
        // get the fieldType of the declared field of the invokevirtual instruction
        invocation.append("(" + invokespecialInst.getDeclaredResultType().getName() + ")");
        // get the class type that declared this field
        invocation.append(ref);
        // get the name of the field
        invocation.append("." + invokespecialInst.getDeclaredTarget().getSelector().getName());
        // get the parameters
        invocation.append("(");
        for (int i = 0; i < params.size(); i++) {
          invocation.append(params.get(i));
          if (i != params.size() - 1) {
            invocation.append(", ");
          }
        }
        invocation.append(");");
        
        // def is not exist before invokespecial Instruction
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
  public static Predicate handle_invokestatic(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokestaticInst = (SSAInvokeInstruction) inst;

    String def = methData.getSymbol(invokestaticInst.getDef(), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(methData.getSymbol(invokestaticInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

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
  
  // go into invocation
  public static Predicate handle_invokeinterface_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokeinterface(postCond, inst, instInfo);
  }

  // go into invocation
  @SuppressWarnings("unchecked")
  public static Predicate handle_invokevirtual_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokevirtualInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokevirtual instruction
    String def = methData.getSymbol(invokevirtualInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokevirtualInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokevirtualInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokevirtualInst.getUse(i), instInfo.valPrefix, newDefMap));
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // add smtStatments to smtStatement list
      List<List<String>> newSMTStatements = addSMTStatments(
          postCond.getSMTStatements(), smtStatements);
      
      // compute valPrefix for the new method
      String newValPrefix = instInfo.valPrefix
          + String.format("%04d", invokevirtualInst.getProgramCounter());

      // add ref (v1 == this)
      String newRef = "v" + newValPrefix + "1";

      // map parameters to method
      List<String> newParams = new ArrayList<String>();
      newVarMap = mapParamsToMethod(invokevirtualInst, instInfo, methData, ref,
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
        preCond = computeToEnterCallSite(invokevirtualInst, instInfo, optionsAndStates, 
            caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
      }
      else {
        // compute targeting method with startLine = -1 (from exit block)
        preCond = computeAtCallSite(invokevirtualInst, instInfo, optionsAndStates, 
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
        preCond = handle_invokevirtual(postCond, inst, instInfo);
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
  public static Predicate handle_invokespecial_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokespecialInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokevirtual instruction
    String def = methData.getSymbol(invokespecialInst.getDef(), instInfo.valPrefix, newDefMap);
    String ref = methData.getSymbol(invokespecialInst.getUse(0), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokespecialInst.getNumberOfParameters();
    for (int i = 1; i < count; i++) {
      params.add(methData.getSymbol(invokespecialInst.getUse(i), instInfo.valPrefix, newDefMap));
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
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);

      // add new variables to varMap
      newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      
      // add smtStatments to smtStatement list
      List<List<String>> newSMTStatements = addSMTStatments(
          postCond.getSMTStatements(), smtStatements);
      
      // compute valPrefix for the new method
      String newValPrefix = instInfo.valPrefix
          + String.format("%04d", invokespecialInst.getProgramCounter());

      // add ref (v1 == this)
      String newRef = "v" + newValPrefix + "1";

      // map parameters to method
      List<String> newParams = new ArrayList<String>();
      newVarMap = mapParamsToMethod(invokespecialInst, instInfo, methData, ref,
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
        preCond = computeToEnterCallSite(invokespecialInst, instInfo, optionsAndStates, 
            caller, callStack, curInvokeDepth, newValPrefix, newPostCond);
      }
      else {
        // compute targeting method with startLine = -1 (from exit block)
        preCond = computeAtCallSite(invokespecialInst, instInfo, optionsAndStates, 
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
        preCond = handle_invokespecial(postCond, inst, instInfo);
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
  public static Predicate handle_invokestatic_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, 
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {

    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAInvokeInstruction invokestaticInst = (SSAInvokeInstruction) inst;

    // the variable(result) define by the invokespecial instruction
    String def = methData.getSymbol(invokestaticInst.getDef(), instInfo.valPrefix, newDefMap);
    List<String> params = new ArrayList<String>();
    int count = invokestaticInst.getNumberOfParameters();
    for (int i = 0; i < count; i++) {
      params.add(methData.getSymbol(invokestaticInst.getUse(i), instInfo.valPrefix, newDefMap));
    }

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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
  
  public static Predicate handle_monitorenter(Predicate postCond, 
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;

    // not implement
    preCond = new Predicate(postCond.getSMTStatements(), postCond.getVarMap(),
        postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }
  
  public static Predicate handle_monitorexit(Predicate postCond, 
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;

    // not implement
    preCond = new Predicate(postCond.getSMTStatements(), postCond.getVarMap(),
        postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }
  
  @SuppressWarnings("unchecked")
  public static Predicate handle_neg(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAUnaryOpInstruction unaryInst = (SSAUnaryOpInstruction) inst;

    // the variable(result) define by the binaryOp instruction
    String def = methData.getSymbol(unaryInst.getDef(), instInfo.valPrefix, newDefMap);
    String var = methData.getSymbol(unaryInst.getUse(0), instInfo.valPrefix, newDefMap);
    
    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);
    
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
  public static Predicate handle_new(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSANewInstruction newInst = (SSANewInstruction) inst;
    
    String def = methData.getSymbol(newInst.getDef(), instInfo.valPrefix, newDefMap);

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();
    
    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

    // the variable define by the new instruction
    if (newVarMap.containsKey(def)) {
      // get the declared type of the new Instruction
      String declaredType = newInst.getConcreteType().getName().toString();
      String freshInst    = "FreshInstanceOf(" + declaredType + ")";
      
      smtStatement = new ArrayList<String>();
      smtStatement.add(freshInst);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);

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

    // add smtStatments to smtStatement list
    List<List<String>> newSMTStatements = addSMTStatments(
        postCond.getSMTStatements(), smtStatements);
    
    preCond = new Predicate(newSMTStatements, newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public static Predicate handle_phi(Predicate postCond, SSAInstruction inst,
      BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAPhiInstruction phiInst = (SSAPhiInstruction) inst;

    String def = methData.getSymbol(phiInst.getDef(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets = 
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

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
  public static Predicate handle_pi(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();
    SSAPiInstruction piInst = (SSAPiInstruction) inst;

    String def = methData.getSymbol(piInst.getDef(), instInfo.valPrefix, newDefMap);
    String val = methData.getSymbol(piInst.getVal(), instInfo.valPrefix, newDefMap);

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, def);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

    // the variable define by the getstatic instruction
    if (newVarMap.containsKey(def)) {
      // def is not exist before getstatic Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, val);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for putfield instruction
  public static Predicate handle_putfield(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAPutInstruction putfieldInst = (SSAPutInstruction) inst;

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

      // get the fieldType of the declared field of the putfield instruction
      String declaredField = "(" + putfieldInst.getDeclaredFieldType().getName() + ")";
      // get the class type that declared this field
      declaredField += ref;
      // get the name of the field
      declaredField += "." + putfieldInst.getDeclaredField().getName();
      // the variable define by the putfield instruction
      if (newVarMap.containsKey(declaredField)) {
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

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), 
        postCond.getDefMap());
    return preCond;
  }
  
  // handler for putstatic instruction
  public static Predicate handle_putstatic(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAPutInstruction putstaticInst = (SSAPutInstruction) inst;

    String val = methData.getSymbol(putstaticInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());

    // get the fieldType of the declared field of the putstatic instruction
    String declaredField = "(" + putstaticInst.getDeclaredFieldType().getName() + ")";
    // get the class type that declared this field
    declaredField += putstaticInst.getDeclaredField().getDeclaringClass().getName();
    // get the name of the field
    declaredField += "." + putstaticInst.getDeclaredField().getName();
    // the variable define by the putstatic instruction
    if (newVarMap.containsKey(declaredField)) {
      // def is not exist before putstatic Instruction
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, declaredField, val);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, 
        postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  public static Predicate handle_return(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAReturnInstruction returnInst = (SSAReturnInstruction) inst;
    
    // the return value of the instruction
    String ret = methData.getSymbol(returnInst.getResult(), instInfo.valPrefix, postCond.getDefMap());

    // substitute "RET" given by caller
    if (newVarMap.containsKey("RET")) {
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, "RET", ret);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, 
        postCond.getPhiMap(), postCond.getDefMap());
    return preCond;
  }

  public static Predicate handle_switch(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSASwitchInstruction switchInst = (SSASwitchInstruction) inst;

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

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), 
        postCond.getDefMap());
    return preCond;
  }
  
  // handler for throw instruction
  public static Predicate handle_throw(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAThrowInstruction throwInst = (SSAThrowInstruction) inst;

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

    preCond = new Predicate(newSMTStatements, newVarMap, postCond.getPhiMap(), 
        postCond.getDefMap());
    return preCond;
  }

  @SuppressWarnings("unchecked")
  public static Predicate handle_entryblock(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();

    // at the entry block, all parameters are defined
    Hashtable<String, String> paramMap = methData.getParamMap();
    Enumeration<String> keys = paramMap.keys();
    while (keys.hasMoreElements()) {
      String valnum = (String) keys.nextElement();
      
      // add valPrefix
      valnum = "v" + instInfo.valPrefix + valnum.substring(1);

      // assign concrete variable to phi variable
      List<Hashtable<String, ?>> rets =
        assignPhiValue(postCond, methData, newVarMap, valnum);
      newVarMap = (Hashtable<String, List<String>>) rets.get(0);
      newPhiMap = (Hashtable<String, String>) rets.get(1);
      newDefMap = (Hashtable<String, Integer>) rets.get(2);
    }
    
    // at the entry block, check if the caught exception is thrown
    newVarMap = checkExceptionThrown(postCond, newVarMap);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  // handler for ShrikeCFG ConstantInstruction instruction
  @SuppressWarnings("unchecked")
  public static Predicate handle_constant(Predicate postCond,
      SSAInstruction inst, BBorInstInfo instInfo, String constantStr) {
    Predicate preCond = null;
    MethodMetaData methData = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    Hashtable<String, String> newPhiMap = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap = postCond.getDefMap();

    // assign concrete variable to phi variable
    List<Hashtable<String, ?>> rets =
      assignPhiValue(postCond, methData, newVarMap, constantStr);
    newVarMap = (Hashtable<String, List<String>>) rets.get(0);
    newPhiMap = (Hashtable<String, String>) rets.get(1);
    newDefMap = (Hashtable<String, Integer>) rets.get(2);

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }
  
  private static Hashtable<String, List<String>> mapParamsToMethod(
      SSAInvokeInstruction invokeInst, BBorInstInfo instInfo,
      MethodMetaData methData, String ref, String def, String newValPrefix, 
      String newRef, List<String> params, List<String> newParams,
      Hashtable<String, List<String>> newVarMap, Predicate postCond) {

    // if not static
    boolean isStatic = ref == null;
    if (!isStatic) {
      // add ref (v1 == this)
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, ref, newRef);
    }

    // add params
    for (int i = 0; i < params.size(); i++) {
      String valnum = params.get(i);

      // valnum inside invocation
      String newParam = "v" + newValPrefix + (i + (isStatic ? 1 : 2));
      newParams.add(newParam);
      if (valnum.startsWith("v")) {
        newVarMap = substituteVarMapKey(postCond, methData, newVarMap, valnum, newParam);

        // this is necessary, because when wp.computeRec returns, it is possible
        // that its smtStatements contain statements that have newParam elements
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, newParam, null);
      }
    }

    // substitute def with a "RET", which will be replaced with a
    // real return variable in the exit block
    if (invokeInst.getDef() != -1) {
      newVarMap = substituteVarMapKey(postCond, methData, newVarMap, def, "RET");
    }
    
    return newVarMap;
  }
  
  private static Hashtable<String, List<String>> mapParamsFromMethod(
      MethodMetaData methData, String ref, String newRef, List<String> params, 
      List<String> newParams, Hashtable<String, List<String>> newVarMap, 
      Predicate preCond) {

    // if not static
    boolean isStatic = ref == null;
    if (!isStatic) {
      newVarMap = substituteVarMapKey(preCond, methData, newVarMap, newRef, ref);
    }

    // set params back
    for (int i = 0; i < newParams.size(); i++) {
      String valnum = params.get(i);
      String newParam = newParams.get(i);
      
      newVarMap = substituteVarMapKey(preCond, methData, newVarMap, newParam, valnum);
    }
    
    return newVarMap;
  }

  private static Predicate computeToEnterCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String newValPrefix, 
      Predicate newPostCond) {
    Predicate preCond = null;
    
    String methodSig = invokeInst.getDeclaredTarget().getSignature();
    
    // get inner call stack
    CallStack innerCallStack = callStack.getInnerCallStack();
    int lineNo = innerCallStack.getCurLineNo();
    
    // we only consider inclLine & starting instruction at the innermost call
    boolean inclLine = true;
    int startingInst = -1;
    if (innerCallStack.getDepth() == 1) {
      inclLine     = optAndStates.inclInnerMostLine;
      startingInst = optAndStates.startingInst;
    }

    // get dispatch target
    CGNode target = null;
    if (optAndStates.compDispatchTargets) {
      // get targets
      CallGraph callGraph = instInfo.wp.getWalaAnalyzer().getCallGraph();
      CGNode[] targets = getInvokeTargets(callGraph, caller, invokeInst.getCallSite());
      
      // TODO: support multiple targets
      if (targets != null && targets.length > 0) {
        target = targets[0]; // take the first one
        if (target != null) {
          methodSig = target.getMethod().getSignature();
        }
      }
    }

    try {
      WeakestPreconditionResult wpResult = instInfo.wp.computeRec(optAndStates, 
          target, methodSig, lineNo, startingInst, inclLine, innerCallStack, 
          curInvokeDepth, newValPrefix, newPostCond);
      preCond = wpResult.getFirstSatisfiable();
    } catch (InvalidStackTraceException e) {}
    
    return preCond;
  }
  
  private static Predicate computeAtCallSite(SSAInvokeInstruction invokeInst,
      BBorInstInfo instInfo, GlobalOptionsAndStates optAndStates, CGNode caller, 
      CallStack callStack, int curInvokeDepth, String newValPrefix, 
      Predicate newPostCond) {
    Predicate preCond = null;

    String methodSig = invokeInst.getDeclaredTarget().getSignature();
    
    // get from summary
    boolean noMatch = false;
    if (optAndStates.summary != null) {
      preCond = optAndStates.summary.getSummary(methodSig, curInvokeDepth + 1,
          newValPrefix, newPostCond);
      if (preCond == null) {
        noMatch = true;
      }
    }

    // if no summary, compute
    if (preCond == null) {
      CGNode target = null;
      if (optAndStates.compDispatchTargets) {
        // get targets
        CallGraph callGraph = instInfo.wp.getWalaAnalyzer().getCallGraph();
        CGNode[] targets = getInvokeTargets(callGraph, caller, invokeInst.getCallSite());
        
        // TODO: support multiple targets
        if (targets != null && targets.length > 0) {
          target = targets[0]; // take the first one
          if (target != null) {
            methodSig = target.getMethod().getSignature();
          }
        }
      }
      
      try { 
        // call compute, startLine = -1 (from exit block)
        WeakestPreconditionResult wpResult = instInfo.wp.computeRec(optAndStates, 
            target, methodSig, -1, -1, false, callStack, curInvokeDepth + 1, 
            newValPrefix, newPostCond);
        preCond = wpResult.getFirstSatisfiable();
      } catch (InvalidStackTraceException e) {}
    }
    
    // save to summary
    if (noMatch && preCond != null) {
      // use the original method signature
      methodSig = invokeInst.getDeclaredTarget().getSignature();
      
      optAndStates.summary.putSummary(methodSig, curInvokeDepth + 1,
          newValPrefix, newPostCond, preCond);
    }
    
    return preCond;
  }
  
  private static CGNode[] getInvokeTargets(CallGraph callGraph, CGNode caller, 
      CallSiteReference callSite) {
    // find all possible targets
    return callGraph.getDispatchTargets(caller, callSite);
  }

  private static List<List<String>> addSMTStatments(List<List<String>> oldSMTStatmentList, 
      List<List<String>> smtStatements) {
    // create new list
    int len1 = oldSMTStatmentList.size();
    int len2 = smtStatements.size();
    List<List<String>> newSMTStatements = new ArrayList<List<String>>(len1 + len2);

    // copy elements from old list
    newSMTStatements.addAll(oldSMTStatmentList);

    // add new statements
    newSMTStatements.addAll(smtStatements);

    return newSMTStatements;
  }
  
  /**
   * create a new varMap clone only when necessary
   */
  private static Hashtable<String, List<String>> addVars2VarMap(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMap, String newVar1, String newVar2) {

    boolean isNewClone = false;
    if (newVar1 != null/* && 
        (newVar1.startsWith("v") || newVar1.startsWith("#"))*/) {
      // clone only when necessary
      if (varMap == postCond.getVarMap()) {
        varMap = postCond.getVarMapClone();
      }
      isNewClone = true;

      // if newVar1 it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newVar1);
      String key = (param == null) ? newVar1 : param;
      List<String> varList = varMap.get(key);
      if (varList != null) {
        // add new var to varlist
        varList.add(newVar1);
      }
      else {
        varList = new ArrayList<String>();
        varList.add(newVar1);
        varMap.put(key, varList);
      }
    }

    if (newVar2 != null/* && 
        (newVar2.startsWith("v") || newVar2.startsWith("#"))*/) {
      if (!isNewClone) {
        // clone only when necessary
        if (varMap == postCond.getVarMap()) {
          varMap = postCond.getVarMapClone();
        }
        isNewClone = true;
      }

      // if newVar2 it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newVar2);
      String key = (param == null) ? newVar2 : param;
      List<String> varList = varMap.get(key);
      if (varList != null) {
        // add new var to varlist
        varList.add(newVar2);
      }
      else {
        varList = new ArrayList<String>();
        varList.add(newVar2);
        varMap.put(key, varList);
      }
    }

    return varMap;
  }

  /**
   * create a new varMap clone only when necessary
   */
  private static Hashtable<String, List<String>> addVars2VarMap(
      Predicate postCond, MethodMetaData methData,
      Hashtable<String, List<String>> varMap, List<String> newVars) {
    
    boolean isNewClone = false;
    for (String var : newVars) {
      if (var != null/* && 
          (var.startsWith("v") || var.startsWith("#"))*/) {
        if (!isNewClone) {
          // clone only when necessary
          if (varMap == postCond.getVarMap()) {
            varMap = postCond.getVarMapClone();
          }
          isNewClone = true;
        }

        // if var it's a parameter, substitute with
        // the parameter name right away!
        // because we're using valPrefix, a param name will be returned only
        // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix
        // == "")
        // we don't want to replace with a param name if it's still inside some
        // invocation, because it's hard to replace a param at the outter
        // handle_invoke*_stepin
        String param = methData.getParamStr(var);
        String key = (param == null) ? var : param;
        // add new var to varlist
        List<String> varList = varMap.get(key);
        if (varList != null) {
          varList.add(var);
        }
        else {
          varList = new ArrayList<String>();
          varList.add(var);
          varMap.put(key, varList);
        }
      }
    }

    return varMap;
  }

  /**
   * might create a new phiMap and a new varMap clone
   */
  private static List<Hashtable<String, ?>> assignPhiValue(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMap, String def) {
    Hashtable<String, String> newPhiMap   = postCond.getPhiMap();
    Hashtable<String, Integer> newDefMap  = postCond.getDefMap();

    // whenever 
    if (def.startsWith("v")) {
      newDefMap = addDef2DefMap(postCond, newDefMap, def);
    }

    // check if the newly def variable is a
    // potential concrete value to any phi variables
    String phiVar = newPhiMap.get(def);
    if (phiVar != null) {
      // assign concrete value to the phi variable
      // modify directly on this varMap
      varMap = substituteVarMapKey(postCond, methData, varMap, phiVar, def);
      
      // remove phiVar from phiList because it
      // is concrete now
      newPhiMap = postCond.getPhiMapClone();
      Enumeration<String> keys = newPhiMap.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        if (newPhiMap.get(key).equals(phiVar)) {
          newPhiMap.remove(key);
        }
      }
    }
    
    // create return
    ArrayList<Hashtable<String, ?>> rets = new ArrayList<Hashtable<String, ?>>();
    rets.add(varMap);
    rets.add(newPhiMap);
    rets.add(newDefMap);
    return rets;
  }

  /**
   * create a new varMap clone only when necessary
   */
  private static Hashtable<String, List<String>> substituteVarMapKey(
      Predicate postCond, MethodMetaData methData, 
      Hashtable<String, List<String>> varMaptoSub,
      String oldKey, String newKey) {

    // value never change
    List<String> varList1 = varMaptoSub.get(oldKey);
    if (varList1 != null) {
      // create clone on demand
      if (varMaptoSub == postCond.getVarMap()) {
        varMaptoSub = postCond.getVarMapClone();
      }
      varMaptoSub.remove(oldKey);
      
      // if newKey it's a parameter, substitute with
      // the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      // we don't want to replace with a param name if it's still inside some
      // invocation, because it's hard to replace a param at the outter handle_invoke*_stepin
      String param = methData.getParamStr(newKey);
      String key = (param == null) ? newKey : param;
      List<String> varList2 = varMaptoSub.get(key);
      if (varList2 != null) {
        for (String var1 : varList1) {
          varList2.add(var1);
        }
      }
      else {
        varList2 = new ArrayList<String>();
        for (String var1 : varList1) {
          varList2.add(var1);
        }
        varMaptoSub.put(key, varList2);
      }
    }

    return varMaptoSub;
  }

  private static Hashtable<String, Integer> addDef2DefMap(
      Predicate postCond, Hashtable<String, Integer> defMap, String def) {

    if (def.startsWith("v")) {
      // get a clone
      if (defMap == postCond.getDefMap()) {
        defMap = postCond.getDefMapClone();
      }

      // change back to the original form
      int cut = def.lastIndexOf('@');
      if (cut >= 0) {
        def = def.substring(0, cut);
      }

      Integer count = defMap.get(def);
      if (count != null) {
        defMap.put(def, count + 1);
      }
      else {
        defMap.put(def, 1);
      }
    }
    
    return defMap;
  }
  
  /**
   * Assuming there is at most one 'Caught ...' at a time.
   */
  private static String findCaughtExceptionTypeStr(Predicate predicate) {
    String caughtStr = null;
    if (predicate.getVarMap().containsKey("Caught")) {
      caughtStr = predicate.getVarMap().get("Caught").get(0);
    }
    return caughtStr;
  }
  
  private static Hashtable<String, List<String>> setExceptionCaught(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String caughtExcepTypeStr) {
    // create clone on demand
    if (varMapToSet == predicate.getVarMap()) {
      varMapToSet = predicate.getVarMapClone();
    }
    
    // put "Caught"
    List<String> caught = new ArrayList<String>();
    caught.add(caughtExcepTypeStr);
    varMapToSet.put("Caught", caught);
    return varMapToSet;
  }
  
  private static Hashtable<String, List<String>> setExceptionTriggered(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String triggeredExcepTypeStr) {
    if (varMapToSet.containsKey("Caught")) {
      List<String> caughtExcepTypeStr = varMapToSet.get("Caught");
      
      if (caughtExcepTypeStr.get(0).equals(triggeredExcepTypeStr)) {
        // create clone on demand
        if (varMapToSet == predicate.getVarMap()) {
          varMapToSet = predicate.getVarMapClone();
        }
        
        // replace "Caught" with "Triggered"
        varMapToSet.put("Triggered", caughtExcepTypeStr);
        varMapToSet.remove("Caught");
      }
    }
    return varMapToSet;
  }
  
  private static Hashtable<String, List<String>> setExceptionThrownCurrent(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet, String exceptionVal) {
    // create clone on demand
    if (varMapToSet == predicate.getVarMap()) {
      varMapToSet = predicate.getVarMapClone();
    }
    
    // put "ThrownInstCurrent"
    List<String> thrownInst = new ArrayList<String>();
    thrownInst.add(exceptionVal);
    varMapToSet.put("ThrownInstCurrent", thrownInst);
    return varMapToSet;
  }
  
  private static Hashtable<String, List<String>> checkExceptionThrown(
      Predicate predicate, Hashtable<String, List<String>> varMapToSet) {
    if (varMapToSet.containsKey("Caught") && varMapToSet.containsKey("ThrownInstCurrent")) {
      String caughtExcepTypeStr = varMapToSet.get("Caught").get(0);
      
      // get the corresponding final variable
      String excepVar = varMapToSet.get("ThrownInstCurrent").get(0);
      String finalExcepVar = null;
      Enumeration<String> finalvars = varMapToSet.keys();
      while (finalvars.hasMoreElements()) {
        String finalvar = (String) finalvars.nextElement();
        
        if (!finalvar.equals("ThrownInstCurrent")) {
          List<String> midVars = predicate.getVarMap().get(finalvar);
          if (midVars.contains(excepVar)) {
            finalExcepVar = finalvar;
            break;
          }
        }
      }
      
      if (finalExcepVar != null) {
        // very sloppy! Cannot deal with super class. Only 
        // works if the exact exception type is used.
        if (finalExcepVar.contains("(" + caughtExcepTypeStr + ")")) {
          // e.g., FreshInstanceOf(Ljava/lang/NullPointerException), 
          // (Ljava/lang/NullPointerException) param1, 
          // (Ljava/lang/NullPointerException) param1.field1
          
          // the thrown instance is the same type as the caught exception
          varMapToSet = setExceptionTriggered(predicate, varMapToSet, caughtExcepTypeStr);
        }
      }
    }
    
    // remove "ThrownInstCurrent" no matter how
    if (varMapToSet.containsKey("ThrownInstCurrent")) {
      // create clone on demand
      if (varMapToSet == predicate.getVarMap()) {
        varMapToSet = predicate.getVarMapClone();
      }
      varMapToSet.remove("ThrownInstCurrent");
    }
    
    return varMapToSet;
  }
}
