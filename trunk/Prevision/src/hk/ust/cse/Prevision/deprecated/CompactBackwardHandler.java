package hk.ust.cse.Prevision.InstructionHandler;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.Predicate;
import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.WeakestPrecondition.GlobalOptionsAndStates;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.types.TypeReference;

public class CompactBackwardHandler extends CompleteBackwardHandler {

  @SuppressWarnings("unchecked")
  @Override
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
      
      if (variableExists(methData, newVarMap, arrayRef)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(arrayRef);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
      
        // add new variables to varMap
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, arrayRef, null);
      }

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
  @Override
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

      if (variableExists(methData, newVarMap, arrayRef)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(arrayRef);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
      }

      boolean hasIndex  = variableExists(methData, newVarMap, arrayIndex);
      boolean hasLength = variableExists(methData, newVarMap, arrayRef + ".length");
      if (hasIndex || hasLength) {
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
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, (!hasIndex) ? arrayIndex : null, "#!0");
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, (!hasLength) ? arrayRef + ".length" : null, null);
      }

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

  @Override
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
      
      if (variableExists(methData, newVarMap, arrayRef)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(arrayRef);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
      }

      boolean hasIndex  = variableExists(methData, newVarMap, arrayIndex);
      boolean hasLength = variableExists(methData, newVarMap, arrayRef + ".length");
      if (hasIndex || hasLength) {
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
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, (!hasIndex) ? arrayIndex : null, "#!0");
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, (!hasLength) ? arrayRef + ".length" : null, null);
    }
      
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

  // no change
  //public Predicate handle_binaryop(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_catch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  @SuppressWarnings("unchecked")
  @Override
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
      
      if (variableExists(methData, newVarMap, val)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(subTypeStr);
        smtStatement.add("==");
        smtStatement.add("true");
        smtStatement.add(val);
        smtStatement.add("==");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
        
        // add new variables to varMap
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, subTypeStr, null);
      }

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
      
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

  // no change
  //public Predicate handle_compare(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_conversion(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_conditional_branch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  @SuppressWarnings("unchecked")
  @Override
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
      
      if (variableExists(methData, newVarMap, ref)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(ref);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
        
        // add new variables to varMap
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      }

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];

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
        
        // add new variables to varMap
        newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
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

  // no change
  //public Predicate handle_getstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_goto(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_instanceof(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  @Override
  public Predicate handle_invokeinterface(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  @Override
  public Predicate handle_invokevirtual(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    return handle_invokenonstatic(postCond, inst, instInfo);
  }

  @Override
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

      if (variableExists(methData, newVarMap, ref)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(ref);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
        
        // add new variables to varMap
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      }

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];
      

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
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, params);
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

  @SuppressWarnings("unchecked")
  @Override
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
      //newVarMap = addVars2VarMap(postCond, methData, newVarMap, params);
    }

    preCond = new Predicate(postCond.getSMTStatements(), newVarMap, newPhiMap, newDefMap);
    return preCond;
  }

  @Override
  public Predicate handle_invokeinterface_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo,
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokenonstatic_stepin(optionsAndStates, caller, postCond, 
        inst, instInfo, callStack, curInvokeDepth, usedPredicates);
  }

  @Override
  public Predicate handle_invokevirtual_stepin(GlobalOptionsAndStates optionsAndStates, 
      CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo,
      CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    return handle_invokenonstatic_stepin(optionsAndStates, caller, postCond, 
        inst, instInfo, callStack, curInvokeDepth, usedPredicates);
  }

  @Override
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
      
      if (variableExists(methData, newVarMap, ref)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(ref);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
        
        // add new variables to varMap
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      }

      // assign concrete variable to phi variable
      Hashtable<String, ?>[] rets =
        (Hashtable<String, ?>[]) assignPhiValue(postCond, methData, newVarMap, def);
      newVarMap = (Hashtable<String, List<String>>) rets[0];
      newPhiMap = (Hashtable<String, String>) rets[1];
      newDefMap = (Hashtable<String, Integer>) rets[2];

      
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

  // no change
  //public Predicate handle_invokestatic_stepin(GlobalOptionsAndStates optionsAndStates, 
  //    CGNode caller, Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo,
  //    CallStack callStack, int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {}

  // no change
  //public Predicate handle_monitorenter(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_monitorexit(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_neg(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_new(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_phi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_pi(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  @Override
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
      
      if (variableExists(methData, newVarMap, ref)) {
        smtStatement = new ArrayList<String>();
        smtStatement.add(ref);
        smtStatement.add("!=");
        smtStatement.add("null");
        smtStatements.add(smtStatement);
  
        // add new variables to varMap
        //newVarMap = addVars2VarMap(postCond, methData, newVarMap, ref, null);
      }

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

  // no change
  //public Predicate handle_putstatic(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_return(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_switch(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  @Override
  public Predicate handle_throw(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {
    Predicate preCond                         = null;
    MethodMetaData methData                   = instInfo.methData;
    Hashtable<String, List<String>> newVarMap = postCond.getVarMap();
    SSAThrowInstruction throwInst             = (SSAThrowInstruction) inst;

    // the variable(result) thrown by throw instruction
    String exception = methData.getSymbol(throwInst.getUse(0), instInfo.valPrefix, postCond.getDefMap());

    List<String> smtStatement = null;
    List<List<String>> smtStatements = new ArrayList<List<String>>();

    if (variableExists(methData, newVarMap, exception)) {
      smtStatement = new ArrayList<String>();
      smtStatement.add(exception);
      smtStatement.add("!=");
      smtStatement.add("null");
      smtStatements.add(smtStatement);
  
      // add new variables to varMap
      //newVarMap = addVars2VarMap(postCond, methData, newVarMap, exception, null);
    }
    
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

  // no change
  //public Predicate handle_entryblock(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo) {}

  // no change
  //public Predicate handle_constant(Predicate postCond, SSAInstruction inst, BBorInstInfo instInfo, String constantStr) {}
  
  private boolean variableExists(MethodMetaData methData, Hashtable<String, List<String>> varMap, String var) {
    if (!var.startsWith("#")) {
      // if var is a parameter, substitute with the parameter name right away!
      // because we're using valPrefix, a param name will be returned only
      // when it's not inside an invocation(curInvokeDepth == 0, so valPrefix == "")
      String param = methData.getParamStr(var);
      String key = (param == null) ? var : param;
      return varMap.containsKey(key);
    }
    else {
      return false;
    }
  }
  
//  private boolean isBasicBlockInCondBranch(MethodMetaData methData, ISSABasicBlock condBranchBB, ISSABasicBlock targetBB) {
//    Iterator<ISSABasicBlock> succNodes = methData.getcfg().getSuccNodes(condBranchBB);
//    ISSABasicBlock succNode1 = succNodes.next();
//    ISSABasicBlock succNode2 = succNodes.next();
//    
//    // make sure succNode1 is smaller
//    if (succNode1.getNumber() > succNode2.getNumber()) {
//      ISSABasicBlock tmp = succNode1;
//      succNode1 = succNode2;
//      succNode2 = tmp;
//    }
//    
//    if (targetBB.getNumber() < succNode1.getNumber()) {
//      return false;
//    }
//    else if (targetBB.getNumber() < succNode2.getNumber()) {
//      return true;
//    }
//    else {
//      // find the merging block
//      ISSABasicBlock firstBranchLastBB = methData.getcfg().getBasicBlock(succNode2.getNumber() - 1);
//      ISSABasicBlock mergingBB = methData.getcfg().getSuccNodes(firstBranchLastBB).next();
//      return (mergingBB != null && targetBB.getNumber() < mergingBB.getNumber());
//    }
//  }
//  
//  private boolean isDupPredicateForBranch(SSAInstruction inst, String valPrefix, Predicate predicate) {
//    boolean ret = false;
//    HashSet<List<Object>> branchPredicates = 
//      m_branchPredicates.get(new SimpleEntry<String, SSAInstruction>(valPrefix, inst));
//    if (branchPredicates != null) {
//      List<Object> value = new ArrayList<Object>();
//      value.add(predicate.getVarMap());
//      value.add(predicate.getSMTStatements());
//      value.add(predicate.getPhiMap());
//      ret = branchPredicates.contains(value);
//    }
//    return ret;
//  }
//  
//  private void pushBranchPredicate(SSAInstruction inst, String valPrefix, Predicate predicate) {
//    SimpleEntry<String, SSAInstruction> key = new SimpleEntry<String, SSAInstruction>(valPrefix, inst);
//    HashSet<List<Object>> branchPredicates = m_branchPredicates.get(key);
//    if (branchPredicates == null) {
//      branchPredicates = new HashSet<List<Object>>();
//      m_branchPredicates.put(key, branchPredicates);
//    }
//    
//    List<Object> value = new ArrayList<Object>();
//    value.add(predicate.getVarMap());
//    value.add(predicate.getSMTStatements());
//    value.add(predicate.getPhiMap());
//    branchPredicates.add(value);
//  }
}
