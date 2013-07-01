package hk.ust.cse.Prevision.Solver.Z3;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineConstant;
import hk.ust.cse.Prevision.Solver.SolverLoader.SOLVER_RESULT;
import hk.ust.cse.Prevision.Solver.SolverResult;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncInterp;
import com.microsoft.z3.FuncInterp.Entry;
import com.microsoft.z3.Model;

public class Z3Result extends SolverResult {

  // output: 1) SAT + Model, 2) UNSAT + UnsatCore (Expr[]) 3) UNSAT + UnsatCore (String) 4) "TIMEOUT" / "UNKNOWN" / "ERROR"
  public Z3Result(SOLVER_RESULT result, Object output, Z3Input solverInput) {
    m_result      = result;
    m_output      = output;
    m_solverInput = solverInput;
    if (result == SOLVER_RESULT.SAT && output != null) {
      assert(output instanceof Model);
      parseModel((Model) output);
    }
    else if (result == SOLVER_RESULT.UNSAT && output != null) {
      if (output instanceof Expr[]) {
        parseUnsatCore((Expr[]) output);
      }
      else if (output instanceof Condition[]) {
        parseUnsatCore((Condition[]) output);
      }
    }
  }

  private void parseModel(Model model) {
    Hashtable<String, Expr> definedConsts           = ((Z3Input) m_solverInput).getDefinedConstants();
    Hashtable<String, ArrayExpr> definedArrays      = ((Z3Input) m_solverInput).getDefinedArrays();
    Hashtable<String, Instance> nameInstanceMapping = m_solverInput.getNeutralInput().getNameInstanceMapping();
    Hashtable<String, List<long[]>> typeRanges      = m_solverInput.getNeutralInput().getTypeRanges();
    
    // analyze each model line
    m_satModel = new ArrayList<ConditionTerm>();
    
    // parse constant interpretations
    List<DefineConstant> definedConstants = m_solverInput.getNeutralInput().getDefineConstants();
    for (int i = 0, size = definedConstants.size(); i < size; i++) { // (= this 3)
      DefineConstant constant = definedConstants.get(i);
      if (constant.value == null &&               // not known constant
         !constant.name.matches("v[\\d]+\\$1")) { // not conversion helper
        Expr expr = definedConsts.get(constant.name);
        try {
          Expr interp = model.getConstInterp(expr);
          if (interp != null) {
            BinaryConditionTerm term = toConditionTerm(interp, constant.name, nameInstanceMapping);
            if (term != null) {
              m_satModel.add(term);
            }
          }
        } catch (Exception e) {
          System.err.println("Unable to analyze model line: " + expr);
        }
      }
    }
    
    // parse interpretations for arrays (relations)
    Enumeration<String> keys = definedArrays.keys();
    while (keys.hasMoreElements()) {
      String arrayName = (String) keys.nextElement();
      if (!arrayName.matches("[\\S]+@[0-9]+")) { // skip intermediate helper arrays
        ArrayExpr arrayExpr = definedArrays.get(arrayName);
        try {
          FuncInterp interp = model.getFuncInterp(arrayExpr.getFuncDecl());
          if (interp != null) {
            List<BinaryConditionTerm> terms = arrayName.equals("@@array") ? 
                toConditionTerms(interp) : // (= (@@array 2147483649 0) 2147483649)
                toConditionTerms(interp, arrayExpr); // (= (size 3) 1)
            m_satModel.addAll(terms);
          }
        } catch (Exception e) {/* next one */}
      }
    }

    //XXX at the moment, let's assume z3 does not generate useless array interpretations like yices

    // convert constant string
    convertConstString();
    
    // add type conditions to satModel according to the 
    // hidden information from model value and type ranges
    addTypeConditions(typeRanges);
  }

  // parse unsat core trackers from z3 checker
  private void parseUnsatCore(Expr[] unsatCore) {
    m_unsatCore = new ArrayList<List<Condition>>();
    for (Expr unsatExpr : unsatCore) {
      String trackerName = unsatExpr.toString();
      if (trackerName.matches("tracker_[0-9]+")) {
        int trackerId = Integer.parseInt(trackerName.substring(trackerName.indexOf('_') + 1));
        
        Expr assertExpr = ((Z3Input) m_solverInput).getAssertionExprs().get(trackerId);
        Assertion assertion = ((Z3Input) m_solverInput).getAssertionMapping1().get(assertExpr);
        if (assertion != null) {
          List<Condition> conditions = m_solverInput.getNeutralInput().getAssertionCondsMapping().get(assertion);
          if (conditions != null) {
            m_unsatCore.add(conditions);
          }
        }
      }
    }
  }

  // parse unsat core conditions from simple checker
  private void parseUnsatCore(Condition[] unsatCore) {
    m_unsatCore = new ArrayList<List<Condition>>();
    for (Condition condition : unsatCore) {
      m_unsatCore.add(Arrays.asList(condition));
    }
  }
  
  private BinaryConditionTerm toConditionTerm(Expr interp, String constantName, Hashtable<String, Instance> nameInstanceMapping) {
    BinaryConditionTerm conditionTerm = null;

    try {
      // create/retrieve instance1
      Instance instance1 = nameInstanceMapping.get(constantName);
      
      if (instance1 != null) {
        // create/retrieve instance2
        String instance2Str = interp.toString();
        Instance instance2 = nameInstanceMapping.get(instance2Str);
        // it is possible that var2 is a new value, e.g. a new int or string value
        if (instance2 == null) {
          instance2Str = instance2Str.equals("true") ? "1" : (instance2Str.equals("false") ? "0" : instance2Str);
          instance2 = parseNumberOrBV(instance2Str, instance1.getType());
        }
        conditionTerm = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);
      }
      else {
        System.err.println("Unable to analyze model line: " + interp.toString());
      }
    } catch (Exception e) {
      System.err.println("Unable to analyze model line: " + interp.toString());
    }
    return conditionTerm;
  }

  // (= (@@array 2147483649 0) 2147483649)
  private List<BinaryConditionTerm> toConditionTerms(FuncInterp interp) {
    Hashtable<Expr, DefineConstant> definedConsts = ((Z3Input) m_solverInput).getConstantMapping();
    
    List<BinaryConditionTerm> conditionTerms = new ArrayList<BinaryConditionTerm>();
    try {
      for (Entry entry : interp.getEntries()) {
        try {
          Instance instanceRelObj   = new Instance("#!" + entry.getArgs()[0].getArgs()[0], "", null);
          Instance instanceRelIndex = new Instance("#!" + entry.getArgs()[0].getArgs()[1], "", null);

          // discard lines like (#!60921404649 @ #!0) == #!102 where #!60921404649 is ##str.value.
          // We have such lines because we use assertions for constant string definition instead 
          // of array stores due to performance concern.
          DefineConstant definedConst = definedConsts.get(entry.getArgs()[0].getArgs()[0]);
          if (definedConst != null && definedConst.type.equals("[C") && definedConst.name.matches("##.*\\.value")) {
            continue;
          }
          
          // dummy holder for function interpretation line, INSTANCE_OP.ADD is useless
          Instance instance1 = new Instance(instanceRelObj, INSTANCE_OP.DUMMY, instanceRelIndex, null);
          
          // create/retrieve instance2
          String instance2Str = entry.getValue().toString();
          instance2Str = instance2Str.equals("true") ? "1" : (instance2Str.equals("false") ? "0" : instance2Str);
          
          Instance instance2 = parseNumberOrBV(instance2Str, instance1.getType());
          BinaryConditionTerm conditionTerm = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);

          conditionTerms.add(conditionTerm);
        } catch (Exception e) {
          System.err.println("Unable to analyze model line: " + entry.toString());
        }
      }
    } catch (Exception e) {
      System.err.println("Unable to analyze model line: " + interp.toString());
    }
    return conditionTerms;
  }
  
  // (= (size 3) 1)
  private List<BinaryConditionTerm> toConditionTerms(FuncInterp interp, ArrayExpr arrayExpr) {
    List<BinaryConditionTerm> conditionTerms = new ArrayList<BinaryConditionTerm>();

    try {
      String arrayName = arrayExpr.getSExpr();
      for (Entry entry : interp.getEntries()) {
        try {
          String relationObj  = entry.getArgs()[0].getArgs()[0].toString(); // should always be number
          
          // create instance for relation name
          Instance instanceRelName = new Instance("##" + arrayName, "", null);
          // create instance for reference object
          Instance instanceRelObj = parseNumberOrBV(relationObj, "");
          
          // dummy holder for function interpretation line, INSTANCE_OP.ADD is useless
          Instance instance1 = new Instance(instanceRelName, INSTANCE_OP.DUMMY, instanceRelObj, null);
          
          // create/retrieve instance2
          String instance2Str = entry.getValue().toString();
          instance2Str = instance2Str.equals("true") ? "1" : (instance2Str.equals("false") ? "0" : instance2Str);
          
          Instance instance2 = parseNumberOrBV(instance2Str, instance1.getType());
          BinaryConditionTerm conditionTerm = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);

          conditionTerms.add(conditionTerm);
        } catch (Exception e) {
          System.err.println("Unable to analyze model line: " + entry.toString());
        }
      }
    } catch (Exception e) {
      System.err.println("Unable to analyze model line: " + interp.toString());
    }
    return conditionTerms;
  }

  private Instance parseNumberOrBV(String str, String type) {
    String prefix = "";
    
    if (prefix.length() == 0) {
      try {
        Long.parseLong(str);
        prefix = "#!";
      } catch (NumberFormatException e) {}
    }
    if (prefix.length() == 0) {
      if (str.matches("-*[\\d]+/[\\d]+")) {
        int index = str.indexOf('/');
        try {
          boolean negative = str.startsWith("-");
          long num1 = Long.parseLong(str.substring(negative ? 1 : 0, index));
          long num2 = Long.parseLong(str.substring(index + 1));
          double d = (double) num1 / (double) num2;
          str = (negative ? "-" : "") + String.valueOf(d);
          prefix = "#!";
        } catch (NumberFormatException e) {}
      }
    }
    
    return new Instance(prefix + str, type, null);
  }
  
  private void convertConstString() {
    for (int i = 0, size = m_satModel.size(); i < size; i++) {
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) m_satModel.get(i);
      Instance instance1 = binaryTerm.getInstance1();
      Instance instance2 = binaryTerm.getInstance2();

      if (instance2.getValue().startsWith("#!")) { // model value
        Long value = null;
        try {
          value = Long.parseLong(instance2.getValueWithoutPrefix());
        } catch (NumberFormatException e) {}

        if (value != null) {
          Instance constInstance = m_solverInput.getNeutralInput().getConstInstanceMapping().get(String.valueOf(value));
          if (constInstance != null && constInstance.getValue() != null && constInstance.getValue().startsWith("##")) {
            binaryTerm = new BinaryConditionTerm(instance1, binaryTerm.getComparator(), constInstance);
            m_satModel.set(i, binaryTerm);
          }
        }
      }
    }
  }
  
  private void addTypeConditions(Hashtable<String, List<long[]>> typeRanges) {
    // obtain variable type information from model value
    for (int i = 0, size = m_satModel.size(); i < size; i++) {
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) m_satModel.get(i);
      
      Instance instance1 = binaryTerm.getInstance1();
      Instance instance2 = binaryTerm.getInstance2();

      if (instance2.getValue().startsWith("#!")) { // model value
        Long value = null;
        try {
          value = Long.parseLong(instance2.getValueWithoutPrefix());
        } catch (NumberFormatException e) {}

        // if it is non-primitive type
        if (value != null && value >= 21474836471L) {
          TypeConditionTerm typeTerm = null;
          Enumeration<String> keys = m_solverInput.getNeutralInput().getOtherTypes().keys();
          while (keys.hasMoreElements() && typeTerm == null) {
            String typeName = (String) keys.nextElement();
            List<long[]> ranges = typeRanges.get(typeName);

            // model value within type range, obtain this type information
            if (ranges != null && value >= ranges.get(0)[0] && value <= ranges.get(0)[1]) {
              typeTerm = new TypeConditionTerm(
                  instance1, TypeConditionTerm.Comparator.OP_INSTANCEOF, typeName);
            }
          }
          
          if (typeTerm != null) {
            m_satModel.add(typeTerm);
          }
        }
      }
    }
  }
  
  @Override
  public String getOutputInfo(Formula formula) {
    StringBuilder info = new StringBuilder();
    
    if (m_result == SOLVER_RESULT.SAT) {
      if (m_satModel != null && formula != null) {
        info.append(convSatModelToString(formula));
      }
      else {
        info.append("SAT\n");
        if (m_satModel != null) {
          for (ConditionTerm term : m_satModel) {
            info.append(term.toString()).append("\n");
          }
        }
      }
    }
    else if (m_result == SOLVER_RESULT.UNSAT) {
      info.append("UNSAT\n");
      if (m_unsatCore != null) {
        info.append("Unsat Core Conditions: \n");
        for (List<Condition> conditions : m_unsatCore) {
          info.append(conditions.get(0)).append("\n");
        }
      }
    }
    else {
      info.append(m_output != null ? m_output.toString() : "");
    }
    return info.toString(); 
  }
}
