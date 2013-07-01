package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.SolverLoader.SOLVER_RESULT;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SolverResult {

  public boolean isSatisfactory() {
    return m_result.equals(SOLVER_RESULT.SAT);
  }

  public Object getOutput() {
    return m_output;
  }

  public String getOutputInfo(Formula formula) {
    return m_output != null ? m_output.toString() : "";
  }

  public SolverInput getSolverInput() {
    return m_solverInput;
  }
  
  public List<List<Condition>> getUnsatCore() {
    return m_unsatCore;
  }
  
  public List<ConditionTerm> getSatModel() {
    return m_satModel;
  }
  
  public void setSatModel(List<ConditionTerm> newSatModel) {
    m_satModel = newSatModel;
  }
  
  public String convSatModelToString(Formula satisfiable) {
    List<String> modelLines = new ArrayList<String>();
    
    // create assignedValueMap for array index
    Hashtable<String, String> assignedValueMap = new Hashtable<String, String>();
    for (ConditionTerm modelTerm : m_satModel) {
      if (modelTerm instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) modelTerm;
        assignedValueMap.put(binaryTerm.getInstance1().toString(), 
                             binaryTerm.getInstance2().toString());
      }
    }
    
    for (ConditionTerm modelTerm : m_satModel) {
      // if modelLine contains read_@@array_, replace them
      String modelLine = modelTerm.toString();
      modelLine = replaceArrayRead(modelLine, satisfiable.getRelation("@@array"), assignedValueMap);
      modelLines.add(modelLine);
    }
    modelLines = Utils.deleteRedundents(modelLines);
    return "sat\n" + Utils.concatStrings(modelLines, "\n", true);
  }
  
  // translate occurrences of read_@@array_ into obj[index] format
  private static final Pattern s_readArrayPattern = Pattern.compile("read_@@array_([0-9]+)");
  protected static String replaceArrayRead(String str, Relation arrayRel, Hashtable<String, String> assignedValueMap) {    
    Matcher matcher = null;
    while ((matcher = s_readArrayPattern.matcher(str)).find()) {
      String readTime = matcher.group(1);
      
      int index = arrayRel.getIndex(readTime);
      Instance[] domainValues = arrayRel.getDomainValues().get(index);
      
      // look through previous updates to see if it has been assigned before
      String refName  = domainValues[0].toString();
      String refValue = assignedValueMap.get(refName);
      refValue = refValue == null ? refName : refValue;
      String valueIndex = replaceArrayIndex(domainValues[1], assignedValueMap);

      String arrayRead = refName + "[" + valueIndex + "]";
      for (int i = index - 1; i >= 0; i--) {
        if (arrayRel.isUpdate(i)) {
          String refName2  = arrayRel.getDomainValues().get(i)[0].toString();
          String refValue2 = assignedValueMap.get(refName2);
          refValue2 = refValue2 == null ? refName2 : refValue2;
          String valueIndex2 = replaceArrayIndex(arrayRel.getDomainValues().get(i)[1], assignedValueMap);
          if (refName.equals(refName2) && valueIndex.equals(valueIndex2)) {
            arrayRead = replaceArrayRead(arrayRel.getRangeValues().get(i).toString(), arrayRel, assignedValueMap);
            break;
          }
        }
      }
      
      str = str.replace("read_@@array_" + readTime, arrayRead);
    }
    return str;
  }
  
  // try to replace indexInstance with a concrete numeric value
  protected static String replaceArrayIndex(Instance indexInstance, Hashtable<String, String> assignedValueMap) {
    String translated = indexInstance.toString();
    if (indexInstance.isBounded()) { // (v1.all.length - #!1)
      try {
        String leftString  = replaceArrayIndex(indexInstance.getLeft(), assignedValueMap);
        String rightString = replaceArrayIndex(indexInstance.getRight(), assignedValueMap);
        if (leftString.startsWith("#!") && rightString.startsWith("#!")) {
          int leftInt  = Integer.parseInt(leftString.substring(2));
          int rightInt = Integer.parseInt(rightString.substring(2));
          
          switch (indexInstance.getOp()) {
          case ADD:
            translated = "#!" + String.valueOf(leftInt + rightInt);
            break;
          case AND:
            translated = "#!" + String.valueOf(leftInt & rightInt);
            break;
          case SUB:
            translated = "#!" + String.valueOf(leftInt - rightInt);
            break;
          case MUL:
            translated = "#!" + String.valueOf(leftInt * rightInt);
            break;
          case DIV:
            translated = "#!" + String.valueOf(leftInt / rightInt);
            break;
          case OR:
            translated = "#!" + String.valueOf(leftInt | rightInt);
            break;
          case REM:
            translated = "#!" + String.valueOf(leftInt % rightInt);
            break;
          case XOR:
            translated = "#!" + String.valueOf(leftInt ^ rightInt);
            break;
          case SHL:
            translated = "#!" + String.valueOf(leftInt << rightInt);
            break;
          case SHR:
            translated = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          case USHR:
            translated = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          default:
            break;
          }
        }
      } catch (Exception e) {}
    }
    else if (!indexInstance.isBounded()) { // v1.length, read_@@array_
      String value = assignedValueMap.get(translated);
      translated = value != null ? value : translated;
    }
    return translated;
  }

  protected SOLVER_RESULT       m_result;
  protected Object                m_output;
  protected SolverInput           m_solverInput;
  protected List<List<Condition>> m_unsatCore;
  protected List<ConditionTerm>   m_satModel;
  
  protected static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
