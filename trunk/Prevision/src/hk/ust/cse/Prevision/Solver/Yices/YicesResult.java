package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.Solver.ISolverResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class YicesResult implements ISolverResult {
  
  private static final Pattern s_pattern1 = Pattern.compile("^\\(= ([\\S]+) ([\\S]+)\\)$");
  private static final Pattern s_pattern2 = Pattern.compile("v[\\d]+\\$1");
  
  public void parseOutput(String output) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);

    // analyze
    m_satModel = new ArrayList<ConditionTerm>();
    if (outLines.length == 0) {
      m_bSatisfactory = false;
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
//      for (int i = 1; i < outLines.length; i++) {
//        if (outLines[i].length() > 0) {
//          ConditionTerm term = toConditionTerm(outLines[i]);
//          if (term != null) {
//            m_satModel.add(term);
//          }
//          else {
//            System.err.println("Unable to analyze model line: " + outLines[i]);
//          }
//        }
//      }
      
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
  
  public List<ConditionTerm> getSatModel() {
    return m_satModel;
  }
  
  private ConditionTerm toConditionTerm(String str) {
    ConditionTerm smtTerm = null;
    
//    Matcher matcher = null;
//    if ((matcher = s_pattern1.matcher(str)).find()) {
//      SMTVariable var1 = defFinalVarMap.get(matcher.group(1));
//      SMTVariable var2 = defFinalVarMap.get(matcher.group(2));
//      
//      // it is possible that var2 is a new value,
//      // e.g. a new int or string(bit-vector) value
//      if (var2 == null) {
//        var2 = new SMTVariable(matcher.group(2), "#ConstantNumber", 
//            VarCategory.VAR_CONST, null);
//      }
//      
//      if (var1 != null) {
//        smtTerm = new SMTTerm(var1, Operator.OP_EQUAL, var2);
//      }
//    }
    return smtTerm;
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

  private String              m_output;
  private boolean             m_bSatisfactory;
  private List<Integer>       m_unsatCoreIds;
  private List<ConditionTerm> m_satModel;
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
