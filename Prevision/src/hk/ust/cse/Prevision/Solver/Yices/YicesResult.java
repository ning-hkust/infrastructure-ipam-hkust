package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.Solver.ISolverResult;
import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesResult implements ISolverResult {
  
  private static final Pattern s_pattern1 = Pattern.compile("^\\(= ([\\S]+) ([\\S]+)\\)$");
  private static final Pattern s_pattern2 = Pattern.compile("v[\\d]+\\$1");
  
  public void parseOutput(String output, Hashtable<String, Instance> nameInstanceMapping) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);

    // analyze
    m_satModel = null;
    if (outLines.length == 0) {
      m_bSatisfactory = false; // error
    }
    else if (outLines[0].startsWith("unsat")) {
      m_bSatisfactory = false;
      
      // parse unsat core ids
      if (outLines.length > 1 && outLines[1].startsWith("unsat core ids: ")) {
        m_unsatCoreIds = new ArrayList<Integer>();
        String[] ids = outLines[1].substring(16).split(" ");
        for (String id : ids) {
          m_unsatCoreIds.add(Integer.parseInt(id));
        }
      }
    }
    else if (!outLines[0].startsWith("sat")) {
      m_bSatisfactory = false;
    }
    else {
      m_bSatisfactory = true;
      
      // analyze each model line
      m_satModel = new ArrayList<BinaryConditionTerm>();
      for (int i = 1; i < outLines.length; i++) {
        if (outLines[i].length() > 0) {
          BinaryConditionTerm term = toConditionTerm(outLines[i], nameInstanceMapping);
          if (term != null) {
            m_satModel.add(term);
          }
        }
      }
      
      // substitute embedded final vars
      substEmbeddedFinalVars();
      
      // remove conversion helpers
      removeConvHelperTerm();
    }
  }

  public boolean isSatisfactory() {
    return m_bSatisfactory;
  }

  public String getOutputStr() {
    return Utils.unfilterChars(m_output);
  }

  public List<Integer> getUnsatCoreIds() {
    return m_unsatCoreIds;
  }
  
  public List<BinaryConditionTerm> getSatModel() {
    return m_satModel;
  }
  
  private BinaryConditionTerm toConditionTerm(String str, Hashtable<String, Instance> nameInstanceMapping) {
    BinaryConditionTerm conditionTerm = null;
    
    Matcher matcher = null;
    if ((matcher = s_pattern1.matcher(str)).find()) {
      String instance1Str = matcher.group(1);
      String instance2Str = matcher.group(2);
      if (!instance1Str.startsWith("$tmp_")) { // if it is an introduced helper variable, discard
        Instance instance1 = nameInstanceMapping.get(instance1Str);
        Instance instance2 = nameInstanceMapping.get(instance2Str);
        
        // it is possible that var2 is a new value,
        // e.g. a new int or string(bit-vector) value
        if (instance2 == null) {
          String value = matcher.group(2);
          if (value.equals("true")) {
            value = "1";
          }
          else if (value.equals("false")) {
            value = "0";
          }
          
          String prefix = "";
          try {
            Long.valueOf(value);
            prefix = "#!";
          } catch (NumberFormatException e) {
            prefix = value.startsWith("0b") ? "##" : "";
          }
          instance2 = new Instance(prefix + value, instance1.getType(), null);
        }
        
        if (instance1 != null) {
          conditionTerm = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);
        }
        else {
          System.err.println("Unable to analyze model line: " + str);
        }
      }
    }
    else {
      System.err.println("Unable to analyze model line: " + str);
    }
    return conditionTerm;
  }
  
  private void removeConvHelperTerm() {
//    if (m_satModel != null) {
//      for (int i = 0; i < m_satModel.size(); i++) {
//        String var1Name = m_satModel.get(i).getVar1().getVarName();
//        if (s_pattern2.matcher(var1Name).matches()) {
//          m_satModel.remove(i--);
//        }
//      }
//    }
  }
  
  private void substEmbeddedFinalVars() {
//    if (m_satModel != null) {
//      // create a map
//      Hashtable<SMTVariable, SMTVariable> finalVarValueMap = new Hashtable<SMTVariable, SMTVariable>();
//      for (SMTTerm term : m_satModel) {
//        SMTVariable var1 = term.getVar1();
//        SMTVariable var2 = term.getVar2();
//        
//        // only substitute with numbers and booleans
//        if (var2.getVarCategory() == VarCategory.VAR_CONST && 
//            var2.getVarType().equals("#ConstantNumber") && 
//           !var2.getVarName().equals("notnull") && 
//           !var2.getVarName().equals("null")) {
//          finalVarValueMap.put(var1, var2);
//        }
//      }
//      
//      // substitute all embedded final vars with concrete model values
//      for (SMTTerm term : m_satModel) {
//        term.getVar1().substExtraVars(finalVarValueMap);
//        term.getVar2().substExtraVars(finalVarValueMap);
//      }
//    }
  }

  private String                    m_output;
  private boolean                   m_bSatisfactory;
  private List<Integer>             m_unsatCoreIds;
  private List<BinaryConditionTerm> m_satModel;
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
