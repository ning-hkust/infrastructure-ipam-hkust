package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverResult;
import hk.ust.cse.Prevision.Solver.SMTTerm;
import hk.ust.cse.Prevision.Solver.SMTVariable;
import hk.ust.cse.Prevision.Solver.Utils;
import hk.ust.cse.Prevision.Solver.SMTTerm.Operator;
import hk.ust.cse.Prevision.Solver.SMTVariable.VarCategory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesResult implements ISolverResult {
  
  private static final Pattern s_pattern1 = Pattern.compile("^\\(= ([\\S]+) ([\\S]+)\\)$");
  private static final Pattern s_pattern2 = Pattern.compile("v[\\d]+\\$1");
  
  public void parseOutput(String output, Hashtable<String, SMTVariable> defFinalVarMap) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);

    // analyze
    m_satModel = new ArrayList<SMTTerm>();
    if (outLines.length == 0) {
      m_bSatisfactory = false;
    }
    else if (!outLines[0].startsWith("sat")) {
      m_bSatisfactory = false;
    }
    else {
      m_bSatisfactory = true;
      
      // analyze each model line
      Hashtable<SMTVariable, SMTVariable> convHelperMap = new Hashtable<SMTVariable, SMTVariable>();
      for (int i = 1; i < outLines.length; i++) {
        if (outLines[i].length() > 0) {
          SMTTerm term = toSMTTerm(outLines[i], defFinalVarMap, convHelperMap);
          if (term != null) {
            m_satModel.add(term);
          }
          else {
            System.err.println("Unable to analyze model line: " + outLines[i]);
          }
        }
      }
      
      // substitute conversion helper variables
      substConvHelper(convHelperMap);
    }
  }

  public boolean isSatisfactory() {
    return m_bSatisfactory;
  }

  public String getOutputStr() {
    return Utils.unfilterChars(m_output);
  }

  public List<SMTTerm> getSatModel() {
    return m_satModel;
  }

  private boolean isConversionHelper(String modelLine) {
    return s_pattern2.matcher(modelLine).matches();
  }
  
  private SMTTerm toSMTTerm(String str, Hashtable<String, SMTVariable> defFinalVarMap, 
      Hashtable<SMTVariable, SMTVariable> convHelperMap) {
    Matcher matcher = null;
    if ((matcher = s_pattern1.matcher(str)).find()) {
      SMTVariable var1 = defFinalVarMap.get(matcher.group(1));
      SMTVariable var2 = defFinalVarMap.get(matcher.group(2));
      
      // it is possible that var2 is a new value,
      // e.g. a new int or string(bit-vector) value
      if (var2 == null) {
        var2 = new SMTVariable(matcher.group(2), "#ConstantNumber", 
            VarCategory.VAR_CONST, null);
      }
      
      if (var1 != null) {
        if (isConversionHelper(matcher.group(1))) {
          convHelperMap.put(var1, var2);
        }
        return new SMTTerm(var1, Operator.OP_EQUAL, var2);
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }
  
  private void substConvHelper(Hashtable<SMTVariable, SMTVariable> convHelperMap) {
    if (m_satModel != null) {
      for (int i = 0; i < m_satModel.size(); i++) {
        SMTVariable var1 = m_satModel.get(i).getVar1();
        SMTVariable var2 = m_satModel.get(i).getVar2();
        if (convHelperMap.containsKey(var1)) {
          // var1 is a conversion helper, remove this term
          m_satModel.remove(i--);
        }
        else {
          // substitute conversion helper variables with concrete values
          var1.substExtraVars(convHelperMap);
          var2.substExtraVars(convHelperMap);
        }
      }
    }
  }

  private String        m_output;
  private boolean       m_bSatisfactory;
  private List<SMTTerm> m_satModel;
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}