package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.AndConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A solver-independent representation of solver input
 */
public class NeutralInput {

  public class DefineConstant {
    public DefineConstant(String name, String type, String value) {
      this.name  = name;
      this.type  = type;
      this.value = value;
    }
    
    public DefineConstant(String name, String type, long value) {
      this(name, type, String.valueOf(value));
    }

    public String toString() {
      StringBuilder str = new StringBuilder("(define ");
      str.append(name).append("::").append(type);
      str.append(value != null ? (" " + value) : "").append(")");
      return str.toString();
    }
    
    public final String name;
    public final String type;
    public final String value;
  }
  
  public class DefineType {
    public DefineType(String type, List<long[]> ranges) {
      this.type   = type;
      this.ranges = ranges;
    }
    
    public String toString() {
      StringBuilder str = new StringBuilder("(define-type ");
      str.append(type).append(" ").append(ranges.size() > 1 ? "(or " : "");
      for (int i = 0, size = ranges.size(); i < size; i++) {
        str.append("(subrange ");
        str.append(ranges.get(i)[0]).append(" ").append(ranges.get(i)[1]);
        str.append(")");
        if (i != size - 1) {
          str.append(" ");
        }
      }
      str.append(ranges.size() > 1 ? "))" : ")");
      return str.toString();
    }
    
    public final String       type;
    public final List<long[]> ranges;
  }
  
  /**
   * The "array" here means the "array" in the theory of arrays proposed by McCarthy. 
   * The main feature we need is the select-store axioms in the theory of arrays.
   * In yices, "function" and "array" are the same and equivalent to the "array" in the theory of arrays.
   * In Z3, only "Array" is equivalent to the "array" in the theory of arrays.
   */
  public class DefineArray {
    public DefineArray(String arrayName, String[] paramTypes, String returnType) {
      this.arrayName  = arrayName;
      this.paramTypes = paramTypes;
      this.returnType = returnType;
    }
    
    public String toString() {
      StringBuilder str = new StringBuilder("(define ");
      str.append(arrayName).append("(-> (");
      for (int i = 0; i < paramTypes.length; i++) {
        str.append(paramTypes[i]);
        if (i != paramTypes.length - 1) {
          str.append(", ");
        }
      }
      str.append(") ");
      str.append(returnType);
      str.append("))");
      return str.toString();
    }

    public final String   arrayName;
    public final String   returnType;
    public final String[] paramTypes;
  }
  
  /**
   * Equivalent to "store" in the theory of arrays.
   * In yices, the keyword is "update".
   * In Z3, the keyword is "store".
   */
  public class DefineArrayStore extends DefineArray {
    public DefineArrayStore(DefineArray initDefineArrayStmt, 
        int storeFrom, int storeTo, Expression[] domains, Expression value) {
      super(initDefineArrayStmt.arrayName, initDefineArrayStmt.paramTypes, initDefineArrayStmt.returnType);
      this.storeFrom  = storeFrom;
      this.storeTo    = storeTo;
      this.domains    = domains;
      this.value      = value;
    }
    
    public String getStoreFromArrayName() {
      return arrayName + (storeFrom > 0 ? "@" + storeFrom : "");
    }

    public String getStoreToArrayName() {
      return arrayName + (storeTo > 0 ? "@" + storeTo : "");
    }
    
    public String toString() {
      StringBuilder str = new StringBuilder("(define ");
      str.append(getStoreToArrayName());
      str.append(" (store ").append(getStoreFromArrayName()).append(" (");
      for (int i = 0; i < domains.length; i++) {
        str.append(domains[i].value);
        if (i != domains.length - 1) {
          str.append(", ");
        }
      }
      str.append(") (");
      str.append(value.value);
      str.append(")))");
      return str.toString();
    }
    
    public final int          storeFrom;
    public final int          storeTo;
    public final Expression[] domains;
    public final Expression   value;
  }
  
  public abstract class Expression {
    public Expression(String value, String type /* either bitvector32 or number */) {
      this.value = value;
      this.type  = type;
    }
    
    // just for ReadArrayExpr
    public Expression(String arrayName, int readAt, Expression[] domains, String type) {
      this.value = createValueString(arrayName, readAt, domains);
      this.type  = type;
    }

    private String createValueString(String arrayName, int readAt, Expression[] domains) {
      StringBuilder value = new StringBuilder(arrayName);
      value.append(readAt > 0 ? "@" + readAt : "");
      value.append("(");
      for (int i = 0; i < domains.length; i++) {
        value.append(domains[i].value);
        if (i != domains.length - 1) {
          value.append(", ");
        }
      }
      value.append(")");
      return value.toString();
    }
    
    public String toString() {
      return value;
    }
    
    public boolean equals(Object o) {
      if (o == null || !(o instanceof Expression)) {
        return false;
      }
      return value.equals(((Expression) o).value);
    }
    
    public int hashCode() {
      return value.hashCode();
    }
    
    public final String value;
    public final String type; /* bitvector32 or number */
  }

  /**
   * Equivalent to "select" in the theory of arrays.
   * In yices, there is no keyword, just do (f x).
   * In Z3, the keyword is "select", (select f x).
   */
  public class ReadArrayExpr extends Expression {
    public ReadArrayExpr(String arrayName, int readAt, Expression[] domains, String type) {
      super(arrayName, readAt, domains, type);
      this.arrayName = arrayName;
      this.readAt    = readAt;
      this.domains   = domains;
    }
    
    public String getReadAtArrayName() {
      return arrayName + (readAt > 0 ? ("@" + readAt) : "");
    }
    
    public final String       arrayName;
    public final int          readAt;
    public final Expression[] domains;
  }
  
  public class NormalExpr extends Expression {
    public NormalExpr(String value, String type) {
      super(value, type);
    }
  }
  
  public class BVArithmeticExpr extends ArithmeticExpr {
    public BVArithmeticExpr(Expression left, INSTANCE_OP op, Expression right) {
      super(left, op, right, "bitvector32");
    }
  }
  
  public class ArithmeticExpr extends Expression {
    public ArithmeticExpr(Expression left, INSTANCE_OP op, Expression right, String type) {
      super("(" + op.toString() + " " + left.value + " " + right.value + ")", type);
      this.left  = left;
      this.right = right;
      this.op    = op;
    }
    
    public final Expression   left;
    public final Expression   right;
    public final INSTANCE_OP op;
  }
  
  public abstract class Assertion {
    
    public boolean equals(Object o) {
      if (o == null || !(o instanceof Assertion)) {
        return false;
      }
      return toString().equals(((Assertion) o).toString());
    }
    
    public String toString() {
      return assertString;
    }
    
    public int hashCode() {
      return toString().hashCode();
    }
    
    protected String assertString;
  }
  
  // true / false
  public class AtomicAssertion extends Assertion {
    public AtomicAssertion(String value) {
      this.value        = value;
      this.assertString = "assert " + value;
    }
    
    public final String value;
  }
  
  public class BinaryAssertion extends Assertion {
    public BinaryAssertion(Expression expr1, BinaryConditionTerm.Comparator comp, Expression expr2) {
      this.expr1        = expr1;
      this.expr2        = expr2;
      this.comp         = comp;
      this.assertString = "assert (" + comp.toString() + " " + expr1.value + " " + expr2.value + ")";
    }
    
    public final Expression expr1;
    public final Expression expr2;
    public final BinaryConditionTerm.Comparator comp;
  }
  
  public class TypeAssertion extends Assertion {
    public TypeAssertion(Expression expr, TypeConditionTerm.Comparator comp, String typeString) {
      this.expr         = expr;
      this.comp         = comp;
      this.typeString   = typeString;
      this.assertString = "assert (" + expr.value + " " + comp.toString() + " " + typeString + ")";
    }
    
    public final Expression expr;
    public final TypeConditionTerm.Comparator comp;
    public final String typeString;
  }
  
  public class MultiAssertion extends Assertion {
    public MultiAssertion(Assertion[] assertions, String connector) {
      this.assertions   = assertions;
      this.connector    = connector;
      this.assertString = createAssertString();
    }

    private String createAssertString() {
      StringBuilder str = new StringBuilder();

      str.append(assertions.length > 1 ? ("(" + connector + " ") : "");
      for (int i = 0; i < assertions.length; i++) {
        str.append("(").append(assertions[i].toString()).append(")");
        if (i != assertions.length - 1) {
          str.append(" ");
        }
      }
      str.append(assertions.length > 1 ? ")" : "");
      return str.toString();
    }
    
    public final Assertion[] assertions;
    public final String      connector;
  }
  
  public NeutralInput(Formula formula, boolean keepUnboundField, boolean retrieveModel, boolean retrieveUnsatCore) {
    m_keepUnboundField      = keepUnboundField;
    m_retrieveModel         = retrieveModel;
    m_retrieveUnsatCore     = retrieveUnsatCore;
    m_basicTypes            = new Hashtable<String, long[]>();
    m_otherTypes            = new Hashtable<String, long[]>();
    m_typeRanges            = new Hashtable<String, List<long[]>>();
    m_defines               = new ArrayList<DefineConstant>();
    m_defineHelpers         = new ArrayList<DefineConstant>();
    m_defineTypes           = new ArrayList<DefineType>();
    m_defineArrays          = new ArrayList<DefineArray>();
    m_assertions            = new ArrayList<Assertion>();
    m_helperExprs           = new Hashtable<Expression, Expression>();
    m_nameInstanceMapping   = new Hashtable<String, Instance>();
    m_constInstanceMapping  = new Hashtable<String, Instance>();
    m_instanceExprMapping   = new Hashtable<Instance, Expression>();
    m_instanceNameMapping   = new Hashtable<Instance, String>();
    m_assertionCondsMapping = new Hashtable<Assertion, List<Condition>>();
    m_conditionAssertionMapping = new Hashtable<Condition, Assertion>();
    
    convertFormula(formula);
  }
  
  // create a NeutralInput object which has additional conditions over the the original NeutralInput
  @SuppressWarnings("unchecked")
  public NeutralInput(Formula origFormula, NeutralInput origInput, List<Condition> additionals) {
    m_keepUnboundField      = origInput.m_keepUnboundField;
    m_retrieveModel         = origInput.m_retrieveModel;
    m_retrieveUnsatCore     = origInput.m_retrieveUnsatCore;
    m_basicTypes            = origInput.m_basicTypes;
    m_otherTypes            = origInput.m_otherTypes;
    m_typeRanges            = origInput.m_typeRanges;
    m_defines               = origInput.m_defines;
    m_defineHelpers         = origInput.m_defineHelpers;
    m_defineTypes           = origInput.m_defineTypes;
    m_defineArrays          = origInput.m_defineArrays;
    m_helperExprs           = origInput.m_helperExprs;
    m_nameInstanceMapping   = origInput.m_nameInstanceMapping;
    m_constInstanceMapping  = origInput.m_constInstanceMapping;
    m_instanceExprMapping   = origInput.m_instanceExprMapping;
    m_instanceNameMapping   = origInput.m_instanceNameMapping;
    
    // need to clone a new assertion list
    m_assertions            = (List<Assertion>) ((ArrayList<Assertion>) origInput.m_assertions).clone();
    m_assertionCondsMapping = (Hashtable<Assertion, List<Condition>>) 
        ((Hashtable<Assertion, List<Condition>>) origInput.m_assertionCondsMapping).clone();
    m_conditionAssertionMapping = (Hashtable<Condition, Assertion>) 
        ((Hashtable<Condition, Assertion>) origInput.m_conditionAssertionMapping).clone();
    
    for (Condition additional : additionals) {
      Assertion assertion = createAssertion(additional, origFormula);
      m_assertions.add(assertion);
      m_conditionAssertionMapping.put(additional, assertion);
      
      List<Condition> assertConditions = m_assertionCondsMapping.get(assertion);
      if (assertConditions == null) {
        assertConditions = new ArrayList<Condition>();
        m_assertionCondsMapping.put(assertion, assertConditions);
      }
      assertConditions.add(additional);
    }
  }
  
  private void convertFormula(Formula formula) {
    m_defineTypes.addAll(defineTypes(formula));
    m_defines.addAll(defineInstances(formula));
    m_defineArrays.addAll(defineArrays(formula));
    addCommonContracts(formula);
    m_assertions.addAll(createAssertions(formula));
    m_defineHelpers.addAll(defineHelperConstants(formula));
  }
  
  private List<DefineType> defineTypes(Formula formula) {
    List<DefineType> defineTypes = new ArrayList<DefineType>();
    
    long[] byteRange    = new long[] {(long) Byte.MIN_VALUE, (long) Byte.MAX_VALUE};
    long[] charRange    = new long[] {(long) Character.MIN_VALUE, (long) Character.MAX_VALUE};
    long[] shortRange   = new long[] {(long) Short.MIN_VALUE, (long) Short.MAX_VALUE};
    long[] intRange     = new long[] {(long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE};
    long[] floatRange   = new long[] {(long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE};
    long[] doubleRange  = new long[] {(long) Integer.MIN_VALUE * 10, (long) Integer.MAX_VALUE * 10};
    long[] booleanRange = new long[] {0, 1};
    
    Long nullRef     = doubleRange[1] + 1;
    Long minRef      = nullRef + 1;
    Long maxRef      = minRef * 10;
    long[] refRange1 = new long[] {nullRef, maxRef};
    long[] refRange2 = new long[] {minRef, maxRef};
    long[] fullRange = new long[] {doubleRange[0], maxRef};

    m_basicTypes.put("I", intRange);
    m_basicTypes.put("J", intRange);
    m_basicTypes.put("S", shortRange);
    m_basicTypes.put("B", byteRange);
    m_basicTypes.put("C", charRange);
    m_basicTypes.put("D", doubleRange);
    m_basicTypes.put("F", floatRange);
    m_basicTypes.put("Z", booleanRange);
    m_basicTypes.put("Unknown-Type", fullRange);
    m_basicTypes.put("reference", refRange1);
    m_basicTypes.put("not_null_reference", refRange2);
    
    // add reference types found in conditions
    for (Condition condition : formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          defineTypes(instance);
        }
        if (term instanceof TypeConditionTerm) {
          String typeName = ((TypeConditionTerm) term).getTypeString();
          if (!m_basicTypes.containsKey(typeName) && !m_otherTypes.containsKey(typeName)) {
            m_otherTypes.put(typeName, new long[] {-1, -1} /* do not specify range yet */);
          }
        }
      }
    }
    
    // add reference types found in relationMap
    Hashtable<String, Relation> relationMap = formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      defineTypes(relation);
    }
    
    // compute type ranges for non-primitive types
    computeOtherTypeRanges(minRef, maxRef);
    
    // add the type ranges for primitive types
    m_typeRanges.put("I", Arrays.asList(new long[][] {intRange}));
    m_typeRanges.put("J", Arrays.asList(new long[][] {intRange}));
    m_typeRanges.put("S", Arrays.asList(new long[][] {shortRange}));
    m_typeRanges.put("B", Arrays.asList(new long[][] {byteRange}));
    m_typeRanges.put("C", Arrays.asList(new long[][] {charRange}));
    m_typeRanges.put("D", Arrays.asList(new long[][] {doubleRange}));
    m_typeRanges.put("F", Arrays.asList(new long[][] {floatRange}));
    m_typeRanges.put("Z", Arrays.asList(new long[][] {booleanRange}));
    m_typeRanges.put("Unknown-Type", Arrays.asList(new long[][] {fullRange}));
    m_typeRanges.put("reference", Arrays.asList(new long[][] {refRange1}));
    m_typeRanges.put("not_null_reference", Arrays.asList(new long[][] {refRange2}));

    // define basic types
    keys = m_basicTypes.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      DefineType defineType = new DefineType(key, Arrays.asList(new long[][] {m_basicTypes.get(key)}));
      defineTypes.add(defineType);
    }
    
    // define other types
    keys = m_otherTypes.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      DefineType defineType = new DefineType(key, m_typeRanges.get(key));
      defineTypes.add(defineType);
    }
    
    // define null
    m_defines.add(new DefineConstant("null", "Unknown-Type", nullRef));
    
    return defineTypes;
  }
  
  private void defineTypes(Instance instance) {
    if (!instance.isConstant()) {
      if (instance.isAtomic()) { // FreshInstanceOf(...)
        if (!m_basicTypes.containsKey(instance.getType()) && !m_otherTypes.containsKey(instance.getType())) {
          m_otherTypes.put(instance.getType(), new long[] {-1, -1} /* do not specify range yet */);
        }
      }
      else if (!instance.isBounded()) { // e.g. Ljava/lang/System.out
        Reference lastRef = instance.getLastReference();
        if (lastRef != null) {
          String varType = lastRef.getType();
          if (!m_basicTypes.containsKey(varType) && !m_otherTypes.containsKey(varType)) {
            m_otherTypes.put(varType, new long[] {-1, -1});
          }
          
          // for "__instanceof__" field: v2.__instanceof__Lorg/apache/tools/ant/Task
          String lastRefName = lastRef.getName();
          if (lastRefName.contains("__instanceof__")) {
            String typeName = null;
            int index = lastRefName.indexOf("__instanceof__") + 14;
            if (lastRefName.startsWith("read___instanceof__")) { // read___instanceof__[B_2247616843578211
              int index2 = lastRefName.lastIndexOf("_");
              typeName = lastRefName.substring(index, index2);
            }
            else {
              typeName = lastRefName.substring(index);
            }
            if (!m_basicTypes.containsKey(typeName) && !m_otherTypes.containsKey(typeName)) {
              m_otherTypes.put(typeName, new long[] {-1, -1});
            }
            
            // we also need to define v2's type
            Instance declInstance = lastRef.getDeclaringInstance();
            if (declInstance != null) {
              defineTypes(declInstance);
            }
          }
          if (isConstStringField(instance) >= 0) {
            if (!m_otherTypes.containsKey(STRING_TYPE)) {
              m_otherTypes.put(STRING_TYPE, new long[] {-1, -1});
            }
            if (!m_otherTypes.containsKey("[C")) {
              m_otherTypes.put("[C", new long[] {-1, -1});
            }
          }
        }
      }
    }
    else if (instance.getValue().startsWith("##")) { // constant string
      if (!m_otherTypes.containsKey(STRING_TYPE)) {
        m_otherTypes.put(STRING_TYPE, new long[] {-1, -1});
      }
      if (!m_otherTypes.containsKey("[C")) {
        m_otherTypes.put("[C", new long[] {-1, -1});
      }
    }
  }
  
  private void defineTypes(Relation relation) {
    for (int i = 0; i < relation.getFunctionCount(); i++) {
      Instance[] domainValues = relation.getDomainValues().get(i);
      Instance rangeValue     = relation.getRangeValues().get(i);
      
      List<Instance> instances = new ArrayList<Instance>();
      Collections.addAll(instances, domainValues);
      if (rangeValue != null) {
        instances.add(rangeValue);
      }
      for (Instance instance : instances) {
        defineTypes(instance);
      }
    }
    
    // the types in the relation definition
    if (!relation.isArrayRelation()) {
      for (String domainType : relation.getDomainTypes()) {
        if (domainType != null && 
            !m_basicTypes.containsKey(domainType) && 
            !m_otherTypes.containsKey(domainType)) {
          m_otherTypes.put(domainType, new long[] {-1, -1});
        }
      }
      if (!m_basicTypes.containsKey(relation.getRangeType()) && 
          !m_otherTypes.containsKey(relation.getRangeType())) {
        m_otherTypes.put(relation.getRangeType(), new long[] {-1, -1});
      }
    }
  }
  
  private void computeOtherTypeRanges(long min, long max) {
    if (m_otherTypes.size() > 0) {
      // obtain subClass information
      List<String> allClasses = new ArrayList<String>();
      final Hashtable<String, List<String>> subClassMap = new Hashtable<String, List<String>>();
      
      Enumeration<String> keys = m_otherTypes.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        allClasses.add(key);
        List<String> subClasses = new ArrayList<String>();
        subClassMap.put(key, subClasses);
        
        Enumeration<String> keys2 = m_otherTypes.keys();
        while (keys2.hasMoreElements()) {
          String key2 = (String) keys2.nextElement();
          if (key != key2 && Utils.canCastTo(key, key2)) {
            subClasses.add(key2);
          }
        }
      }
      
      // sort such that super class is at front
      List<String> sorted = new ArrayList<String>();
      for (String clazz : allClasses) {
        int insertAt = sorted.size();
        for (int i = 0, size = sorted.size(); i < size && insertAt == size; i++) {
          insertAt = subClassMap.get(clazz).contains(sorted.get(i)) ? i : insertAt;
        }
        sorted.add(insertAt, clazz);
      }
      
      long current = min;
      long splitSize = (max - min) / (long) m_otherTypes.size();
      for (String className : sorted) {
        List<long[]> subRanges = new ArrayList<long[]>();
        subRanges.add(new long[] {current, (current += splitSize) - 1});
        m_typeRanges.put(className, subRanges);
      }

      keys = m_typeRanges.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        List<long[]> subRanges = m_typeRanges.get(key);
        Enumeration<String> keys2 = m_typeRanges.keys();
        while (keys2.hasMoreElements()) {
          String key2 = (String) keys2.nextElement();
          List<long[]> otherSubRanges = m_typeRanges.get(key2);
          if (subRanges != otherSubRanges && subClassMap.get(key).contains(key2)) {
            subRanges.add(otherSubRanges.get(0));
          }
        }
      }
    }
  }

  // check if it is a constant string field, some of these fields have known values
  private int isConstStringField(Instance instance) {
    int type = -1;
    
    Reference lastRef = instance.getLastReference();
    Instance lastDeclInstance = (lastRef != null) ? lastRef.getDeclaringInstance() : null;
    if (lastDeclInstance != null && lastDeclInstance.isConstant() && lastDeclInstance.getValue().startsWith("##")) {
      String fieldName = lastRef.getName();
      if (fieldName.equals("count")) {
        type = 0;
      }
      else if (fieldName.equals("value")) {
        type = 1;
      }
    }
    else {
      Reference lastLastRef = (lastDeclInstance != null) ? lastDeclInstance.getLastReference() : null;
      Instance lastLastDeclInstance = (lastLastRef != null) ? lastLastRef.getDeclaringInstance() : null;
      if (lastLastDeclInstance != null && 
          lastLastDeclInstance.isConstant() && 
          lastLastDeclInstance.getValue().startsWith("##")) {
        String fieldName  = lastRef.getName();
        String fieldName2 = lastLastRef.getName();
        if (fieldName2.equals("value") && fieldName.equals("length")) {
          type = 2;
        }
      }
    }
    return type;
  }
  
  private List<DefineConstant> defineHelperConstants(Formula formula) {
    List<DefineConstant> defines = new ArrayList<DefineConstant>();
    
    Enumeration<Expression> keys = m_helperExprs.keys();
    while (keys.hasMoreElements()) {
      Expression key = (Expression) keys.nextElement();
      NormalExpr helperExpr = (NormalExpr) m_helperExprs.get(key);
      
      boolean isHelperBitVector = helperExpr.type.equals("bitvector32");
      defines.add(new DefineConstant(helperExpr.value, isHelperBitVector ? "bitvector32" : "I", null));
    }
    
    return defines;
  }

  private List<DefineConstant> defineInstances(Formula formula) {
    List<DefineConstant> defines = new ArrayList<DefineConstant>();
    
    // define instances appear in conditions
    HashSet<String> definedNames = new HashSet<String>();
    for (Condition condition : formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        List<Instance> instances = new ArrayList<Instance>(Arrays.asList(term.getInstances()));
        
        // make sure constant strings get define
        for (int i = 0; i < instances.size(); i++) {
          Instance instance = instances.get(i);
          if (isConstStringField(instance) >= 0) {
            instances.add(0, instance.getToppestInstance());
            i++;
          }
        }
        
        for (Instance instance : instances) {
          List<DefineConstant> created = createDefineStmts(instance);
          for (DefineConstant define : created) {
            if (define != null) {
              if (!definedNames.contains(define.name)) {
                defines.add(define);
                definedNames.add(define.name);
              }
            }
          }
        }
      }
    }
    
    // define instances appear in relations
    Hashtable<String, Relation> relationMap = formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      for (int i = 0; i < relation.getFunctionCount(); i++) {
        Instance[] domainValues = relation.getDomainValues().get(i);
        Instance rangeValue     = relation.getRangeValues().get(i);
        
        List<Instance> instances = new ArrayList<Instance>();
        Collections.addAll(instances, domainValues);
        if (rangeValue != null) {
          instances.add(rangeValue);
        }
        // make sure constant strings get define
        for (int j = 0; j < instances.size(); j++) {
          Instance instance = instances.get(j);
          if (isConstStringField(instance) >= 0) {
            instances.add(0, instance.getToppestInstance());
            j++;
          }
        }
        
        for (Instance instance : instances) {
          List<DefineConstant> created = createDefineStmts(instance);
          for (DefineConstant define : created) {
            if (define != null) {
              if (!definedNames.contains(define.name)) {
                defines.add(define);
                definedNames.add(define.name);
              }
            }
          }
        }
      }
    }
    return defines;
  }
  
  private List<DefineArray> defineArrays(Formula formula) {
    List<DefineArray> defineArrayStmts = new ArrayList<DefineArray>();
    
    Hashtable<String, Relation> relationMap = formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      defineArrayStmts.add(defineArray(relation));
    }
    
    // obtain all relation store define statements
    List<DefineArrayStore> defineStoreStmts = new ArrayList<DefineArrayStore>();
    final Hashtable<String, HashSet<String>> dependBy = new Hashtable<String, HashSet<String>>();
    keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      
      // find the corresponding array define statement
      DefineArray defineArrayStmt = null;
      for (int i = 0, size = defineArrayStmts.size(); i < size && defineArrayStmt == null; i++) {
        defineArrayStmt = defineArrayStmts.get(i).arrayName
            .equals(relation.getName()) ? defineArrayStmts.get(i) : null;
      }
      defineArrayStores(relation, formula, defineArrayStmt, defineStoreStmts, dependBy);
    }
    
    // sort by dependencies
    boolean changed = true;
    List<DefineArrayStore> sortedStoreStmts = defineStoreStmts;
    for (int i = 0; i < 10 /* avoid any possible endless loop */ && changed; i++) {
      defineStoreStmts = sortedStoreStmts;
      sortedStoreStmts = new ArrayList<DefineArrayStore>();
      for (DefineArrayStore defineStoreStmt : defineStoreStmts) {
        HashSet<String> dependBySet = dependBy.get(defineStoreStmt.getStoreToArrayName());
        int insertAt = sortedStoreStmts.size();
        for (int j = 0; j < sortedStoreStmts.size() && dependBySet != null; j++) {
          DefineArrayStore sortedStoreStmt = sortedStoreStmts.get(j);
          if (dependBySet.contains(sortedStoreStmt.getStoreToArrayName())) {
            insertAt = j;
            break;
          }
        }
        sortedStoreStmts.add(insertAt, defineStoreStmt);
      }
      changed = !defineStoreStmts.equals(sortedStoreStmts);
    }

    defineArrayStmts.addAll(sortedStoreStmts);
    return defineArrayStmts;
  }

  // define relation
  private DefineArray defineArray(Relation relation) {
    DefineArray defineArrayStmt = new DefineArray(relation.getName(), relation.getDomainTypes(), relation.getRangeType());
    return defineArrayStmt;
  }
  
  private static final Pattern s_pattern = Pattern.compile("\\(([\\S]+@[0-9]+)\\("); // (@@array@1( 
  private void defineArrayStores(Relation relation, Formula formula, DefineArray defineArrayStmt, 
      List<DefineArrayStore> defineStoreStmts, Hashtable<String, HashSet<String>> dependBy) {

    // adjust order such that, constant string values are always updated first
    if (relation.getName().equals("@@array")) {
      for (int i = 0; i < relation.getFunctionCount(); i++) {
        if (relation.isUpdate(i)) {
          Instance domain = relation.getDomainValues().get(i)[0];
          if (domain.getLastReference() != null && domain.getLastRefName().equals("value") && 
              domain.getLastReference().getDeclaringInstance() != null && 
              domain.getLastReference().getDeclaringInstance().isConstant()) {
            relation.move(i, 0);
            relation.getFunctionTimes().set(0, Long.MIN_VALUE);
          }
        }
      }
    }
    
    int maxUpdates = Math.min(relation.getFunctionCount(), 256); // avoid JVM crash
    for (int i = 0; i < maxUpdates; i++) {
      Instance[] domainValues = relation.getDomainValues().get(i);
      Instance rangeValue     = relation.getRangeValues().get(i);
      if (rangeValue != null) { // it is an update
        Expression[] domains = new Expression[domainValues.length];
        for (int j = 0; j < domainValues.length; j++) {
          domains[j] = createExpression(domainValues[j], formula);
          if (relation.isArrayRelation() && j == 1) {
            domains[j] = makeHelperWhenNecessary(domains[j], "number"); // the index should always be number
          }
        }
        int lastUpdate = relation.getLastUpdateIndex(i);
        Expression value = createExpression(rangeValue, formula);
        DefineArrayStore defineStoreStmt = 
            new DefineArrayStore(defineArrayStmt, lastUpdate + 1, i + 1, domains, value);
        
        // create a string of all depending expressions
        String defineStoreStmtStr = defineStoreStmt.toString();
        if (!defineStoreStmtStr.contains("%%UnboundField%%")) {
          defineStoreStmts.add(defineStoreStmt);
          
          // find dependents
          Matcher matcher = s_pattern.matcher(defineStoreStmtStr);
          while (matcher.find()) {
            String dependStore = matcher.group(1);
            HashSet<String> dependBySet = dependBy.get(dependStore);
            if (dependBySet == null) {
              dependBySet = new HashSet<String>();
              dependBy.put(dependStore, dependBySet);
            }
            dependBySet.add(relation.getName() + "@" + (i + 1));
          }
        }
        
        // also depend on the array to store
        if (lastUpdate >= 0) {
          String storeFrom = relation.getName() + "@" + (lastUpdate + 1);
          HashSet<String> dependBySet = dependBy.get(storeFrom);
          if (dependBySet == null) {
            dependBySet = new HashSet<String>();
            dependBy.put(storeFrom, dependBySet);
          }
          dependBySet.add(relation.getName() + "@" + (i + 1));
        }
      }
    }
  }

  private List<DefineConstant> createDefineStmts(Instance instance) {
    List<DefineConstant> defines = new ArrayList<DefineConstant>();
    
    boolean justDefStr = false;
    if (instance.isAtomic()) {
      DefineConstant define = null;
      if (instance.isConstant()) {
        String value = instance.getValue();
        if (value.startsWith("##")) {
          define = createStringDefineStmt(instance);
          justDefStr = define != null;
        }
        else if (value.startsWith("#!")) {
          define = createNumberDefineStmt(instance);
        }
      }
      else { // should only be FreshInstanceOf(...)
        String value = instance.getValue();
        if (value.startsWith("FreshInstanceOf(")) {
          define = createFreshInstanceDefineStmt(instance);
        }
      }
      defines.add(define);
    }
    else if (instance.isBounded() /* not atomic but still bounded */) {
      List<DefineConstant> defines1 = createDefineStmts(instance.getLeft());
      List<DefineConstant> defines2 = createDefineStmts(instance.getRight());
      defines.addAll(defines1);
      defines.addAll(defines2);
    }
    else if (!instance.isBounded()) { // field reference, array read
      // check if it is a constant string field, some of these fields have known values
      if (isConstStringField(instance) >= 0) {
        //defines.add(constStringFieldToDefStr(instance, type));
      }
      else if (!instance.isRelationRead()) {
        if (instance.getLastReference() != null && instance.getLastRefName().contains("__instanceof__")) {
          instance = instance.getLastReference().getDeclaringInstance();
          if (!instance.isRelationRead()) {
            defines.add(createUnboundDefineStmt(instance));
          }
        }
        else if (instance.getLastReference() != null && instance.getLastRefName().endsWith(".class")) {
          defines.add(createClassFieldDefineStmt(instance));
        }
        else {
          defines.add(createUnboundDefineStmt(instance));
        }
      }
    }
    
    // only save if there is an 1:1 matching (can never have 1:1 matching for left op right)
    if (defines.size() == 1 && defines.get(0) != null && (!instance.isBounded() || instance.isAtomic())) {
      m_nameInstanceMapping.put(defines.get(0).name, instance);
      m_instanceNameMapping.put(instance, defines.get(0).name);
    }
    
    // define constant string's fields
    if (justDefStr) {
      defines.addAll(createStringFieldsDefineStmts(instance));
    }
    
    return defines;
  }
  
  // define string in the form of ##somestr
  private DefineConstant createStringDefineStmt(Instance instance) {
    DefineConstant define = null;

    // avoid multiple define
    boolean alreadyDefined = false;
    Enumeration<String> keys = m_constInstanceMapping.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Instance instance2 = m_constInstanceMapping.get(key);
      if (instance.getValue().equals(instance2.getValue())) {
        alreadyDefined = true;
        break;
      }
    }
    
    if (!alreadyDefined) {
      // get the new value
      long[] range = m_typeRanges.get(STRING_TYPE).get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      define = new DefineConstant(instance.getValue(), STRING_TYPE, defValue);
      
      // save the constant value
      m_constInstanceMapping.put(String.valueOf(defValue), instance);
    }
    
    return define;
  }
  
  private List<DefineConstant> createStringFieldsDefineStmts(Instance instance) {
    List<DefineConstant> defines = new ArrayList<DefineConstant>();

    // define constant string count
    defines.add(new DefineConstant(instance.getValue() + ".count", "I", instance.getValue().length() - 2));
    
    // define constant string offset
    defines.add(new DefineConstant(instance.getValue() + ".offset", "I", 0));
    
    // define constant string value not null
    if (m_typeRanges.containsKey("[C")) {
      // define constant string value not null
      long[] range = m_typeRanges.get("[C").get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      defines.add(new DefineConstant(instance.getValue() + ".value", "[C", defValue));
      
      // save the constant value
      if (instance.getField("value") != null) {
        m_constInstanceMapping.put(String.valueOf(defValue), instance.getField("value").getInstance());
      }
      
      // define constant string value.length
      defines.add(new DefineConstant(instance.getValue() + ".value.length", "I", instance.getValue().length() - 2));
    }
    
    return defines;
  }
  
  // no need to define any thing for number constant
  private DefineConstant createNumberDefineStmt(Instance instance) {
    return null;
  }
  
  private DefineConstant createFreshInstanceDefineStmt(Instance instance) {
    DefineConstant define = null;
    
    // avoid multiple define
    boolean alreadyDefined = false;
    Enumeration<String> keys = m_constInstanceMapping.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Instance instance2 = m_constInstanceMapping.get(key);
      if (instance.getValue().equals(instance2.getValue())) {
        alreadyDefined = true;
        break;
      }
    }
    
    if (!alreadyDefined) {
      // get the new value
      long[] range = m_typeRanges.get(instance.getType()).get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      define = new DefineConstant(instance.getValue(), instance.getType(), defValue);
      
      // save the constant value
      m_constInstanceMapping.put(String.valueOf(defValue), instance);
    }
    
    return define;
  }

  private DefineConstant createClassFieldDefineStmt(Instance instance) {
    DefineConstant define = null;
    
    Reference lastRef = instance.getLastReference();
    if (lastRef != null) {
      // get the new value
      long[] range = m_typeRanges.get(lastRef.getType()).get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);

      String varName = lastRef.getLongNameWithCallSites();
      String varType = lastRef.getType();
      define = new DefineConstant(varName, varType, defValue);

      // save the constant value
      m_constInstanceMapping.put(String.valueOf(defValue), instance);
    }
    return define;
  }

  private DefineConstant createUnboundDefineStmt(Instance instance) {
    DefineConstant define = null;
    
    Reference lastRef = instance.getLastReference();
    if (lastRef != null) {
      String varName = lastRef.getLongNameWithCallSites();
      String varType = lastRef.getType();
      define = new DefineConstant(varName, varType, null);
    }
    return define;
  }
  
  private List<Assertion> createAssertions(Formula formula) {
    List<Assertion> assertions = new ArrayList<Assertion>();
    
    HashSet<Assertion> addedAssertions = new HashSet<Assertion>();
    for (Condition condition : formula.getConditionList()) {
      // create an assertion statement from condition
      MultiAssertion assertion = createAssertion(condition, formula);
      if (assertion.assertions.length > 0) {
        String assertionStr = assertion.toString();
        if (!assertionStr.contains("%%UnboundField%%")) {
          if (!addedAssertions.contains(assertion)) {
            assertions.add(assertion);
            addedAssertions.add(assertion);
          }
          
          // save an assertion and condition mapping
          List<Condition> assertConditions = m_assertionCondsMapping.get(assertion);
          if (assertConditions == null) {
            assertConditions = new ArrayList<Condition>();
            m_assertionCondsMapping.put(assertion, assertConditions);
          }
          assertConditions.add(condition);
          m_conditionAssertionMapping.put(condition, assertion);
        }
      }
    }
    return assertions;
  }
  
  private MultiAssertion createAssertion(Condition condition, Formula formula) {
    // create assertion from condition terms
    return createAssertion(condition.getConditionTerms(), "or", formula);
  }
  
  private MultiAssertion createAssertion(List<ConditionTerm> terms, String connector, Formula formula) {
    Assertion[] assertions = new Assertion[terms.size()];
    
    for (int i = 0, size = terms.size(); i < size; i++) {
      ConditionTerm term = terms.get(i);
      if (term instanceof BinaryConditionTerm) { // binary condition terms
        // convert instanceof to TypeConditionTerm
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        Instance instance1 = binaryTerm.getInstance1();
        Instance instance2 = binaryTerm.getInstance2();
        if (instance1.getLastReference() != null && 
            instance1.getLastRefName().contains("__instanceof__") && 
            instance2.isConstant()) {
          boolean equals = binaryTerm.getComparator().equals(Comparator.OP_EQUAL);
          boolean toTrue = instance2.getValue().equals("#!1");
          boolean isInstanceOf = !(equals ^ toTrue);
          
          Instance instance = null;
          String typeName   = null;
          String lastRefName = instance1.getLastRefName();
          int index = lastRefName.indexOf("__instanceof__") + 14;
          if (lastRefName.startsWith("read___instanceof__")) { // read___instanceof__[B_2247616843578211
            // get type name
            int index2 = lastRefName.lastIndexOf("_");
            typeName = lastRefName.substring(index, index2);
            Relation relation = formula.getRelation(lastRefName.substring(index - 14, index2));
            int relIndex = relation.getIndex(lastRefName.substring(index2 + 1));
            instance = relation.getDomainValues().get(relIndex)[0];
          }
          else {
            // get type name
            typeName = instance1.getLastRefName().substring(index);
            instance = instance1.getLastReference().getDeclaringInstance();
          }
          
          term = new TypeConditionTerm(instance, isInstanceOf ? TypeConditionTerm.Comparator.OP_INSTANCEOF : 
                                                                TypeConditionTerm.Comparator.OP_NOT_INSTANCEOF, typeName);
          assertions[i] = createAssertion((TypeConditionTerm) term, formula);
        }
        else {
          assertions[i] = createAssertion((BinaryConditionTerm) term, formula);
        }
      }
      else if (term instanceof TypeConditionTerm) { // for type condition term
        assertions[i] = createAssertion((TypeConditionTerm) term, formula);
      }
      else { // for and condition term
        assertions[i] = createAssertion((AndConditionTerm) term, formula);
      }
    }
    
    return new MultiAssertion(assertions, connector);
  }

  private Assertion createAssertion(BinaryConditionTerm binaryTerm, Formula formula) {
    Assertion assertion = null;
    
    Expression expr1 = createExpression(binaryTerm.getInstance1(), formula);
    Expression expr2 = createExpression(binaryTerm.getInstance2(), formula);
    
    // always convert to number
    expr1 = makeHelperWhenNecessary(expr1, "number");
    expr2 = makeHelperWhenNecessary(expr2, "number");
    
    // convert #!0/#!1 to false/true
    //convertTrueFalse(binaryTerm, instance1, instance2);
    
    if (!expr1.value.equals("NaN") && !expr2.value.equals("NaN")) {
      assertion = new BinaryAssertion(expr1, binaryTerm.getComparator(), expr2);
    }
    else {
      // boolean expression with NaN (NaN is not equal to everything!)
      assertion = new AtomicAssertion((binaryTerm.getComparator() == Comparator.OP_INEQUAL) ? "true" : "false");
    }
    return assertion;
  }
  
  private Assertion createAssertion(TypeConditionTerm typeTerm, Formula formula) {
    Assertion assertion = null;

    Expression expr = createExpression(typeTerm.getInstance1(), formula);
    
    switch (typeTerm.getComparator()) {
    case OP_INSTANCEOF:
    case OP_NOT_INSTANCEOF:
      assertion = new TypeAssertion(expr, typeTerm.getComparator(), typeTerm.getTypeString());
      break;
    default:
      assertion = new AtomicAssertion("true");
      break;
    }
    return assertion;
  }
  
  private Assertion createAssertion(AndConditionTerm andTerm, Formula formula) {
    return createAssertion(andTerm.getAndConditionTerms(), "and", formula);
  }
  
  private Expression createExpression(Instance instance, Formula formula) {
    Expression expression = m_instanceExprMapping.get(instance);
    if (expression != null) {
      return expression;
    }
    
    // if instance is not bound, try to show its last reference name
    if (!instance.isBounded()) {
      Reference lastRef = instance.getLastReference();
      if (lastRef != null) {
        if (instance.isRelationRead()) { // read from a relation
          String readStr    = instance.getLastRefName();
          Relation relation = formula.getRelation(readStr);

          int readIndex  = relation.getIndex(instance.getLastReference().getReadRelTime());
          int lastUpdate = relation.getLastUpdateIndex(readIndex);

          Instance[] domainValues = relation.getDomainValues().get(readIndex);
          Expression[] domains = new Expression[domainValues.length];
          for (int i = 0; i < domainValues.length; i++) {
            Expression domain = createExpression(domainValues[i], formula);
            if (relation.isArrayRelation() && i == 1) { 
              domain = makeHelperWhenNecessary(domain, "number"); // the index should always be number
            }
            domains[i] = domain;
          }
          expression = new ReadArrayExpr(relation.getName(), lastUpdate + 1, domains, "number");
        }
        else {
          String lastRefName = null;
          if (m_keepUnboundField || lastRef.getDeclaringInstance() == null) {
            lastRefName = lastRef.getLongNameWithCallSites();
          }
          else {
            // since fields could be assigned many times at different time
            // we may not want to compare fields that are not yet bounded
            lastRefName = "%%UnboundField%%";
          }
          expression = new NormalExpr(lastRefName, "number");
        }
      }
      else {
        expression = new NormalExpr("{Unbounded}", "number");
      }
    }
    else if (instance.isAtomic()) {
      if (instance.getValue().startsWith("##")) {
        expression = new NormalExpr(instance.getValue(), "number");
      }
      else { // #! or FreshInstanceOf
        String rationalFormat = convertJavaNumber(instance.getValue());
        expression = new NormalExpr(rationalFormat, "number");
      }
    }
    else {
      Expression left  = createExpression(instance.getLeft(), formula);
      Expression right = createExpression(instance.getRight(), formula);
      
      // watch out for NaN, NaN +-*/ any number is still NaN
      if (left.value.equals("NaN") || right.value.equals("NaN")) {
        expression = new NormalExpr("NaN", "number");
      }
      else {
        switch (instance.getOp()) {
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case REM:
          expression = createArithmeticExpr(left, instance.getOp(), right);
          break;
        case SHL:
        case SHR:
        case USHR:
          if (Utils.isInteger(right.value)) {
            expression = createShiftConstantExpr(left, instance.getOp(), right);
          }
          else {
            expression = createBVArithmeticExpr(left, instance.getOp(), right);
          }
          break;
        case AND:
        case OR:
        case XOR:
        default:
          expression = createBVArithmeticExpr(left, instance.getOp(), right);
          break;
        }
      }
    }
    
    if (expression != null) {
      m_instanceExprMapping.put(instance, expression);
    }
    
    return expression;
  }
  
  // convert to rational numbers
  private static final Pattern s_floatPattern1 = Pattern.compile("-*[\\d]+\\.[\\d]+"); // 1.0
  private static final Pattern s_floatPattern2 = Pattern.compile("-*[\\d]+\\.[\\d]+E-*[\\d]+");  // 1.0E-6 or 1.0E6
  private String convertJavaNumber(String number) {
    // not a number
    if (!number.startsWith("#!")) {
      return number;
    }
    
    number = number.substring(2);

    // replace Infinity
    if (number.endsWith("Infinity")) {
      number = number.substring(0, number.length() - 8);
      number += String.valueOf(Integer.MAX_VALUE);
    }
    else if (s_floatPattern1.matcher(number).matches()) {
      float fNum = Float.parseFloat(number);

      if (fNum < 1) {
        fNum *= 1000000;  /* precision up to .000000 */
        // rational number
        number = String.valueOf((int) fNum /* lose some precision here*/) + "/1000000"; 
      }
      else {
        fNum *= 1000;     /* precision up to .000 */
        // rational number
        number = String.valueOf((int) fNum /* lose some precision here*/) + "/1000"; 
      }
    }
    else if (s_floatPattern2.matcher(number).matches()) {  // 1.0E-6 or 1.0E6
      float fNum = Float.parseFloat(number);
      
      if (fNum < 1) {
        fNum *= 1000000;  /* precision up to .000000 */
        // rational number
        number = String.valueOf((int) fNum /* lose some precision here*/) + "/1000000"; 
      }
      else {
        // the number should be big, convert directly to int
        number = String.valueOf((int) fNum);
      }
    }

    return number;
  }
  
  private Expression createBVArithmeticExpr(Expression left, INSTANCE_OP op, Expression right) {
    // create bit vector helper variables for the bit vector operation
    left  = makeHelperWhenNecessary(left, "bitvector32");
    right = makeHelperWhenNecessary(right,"bitvector32");
    return new BVArithmeticExpr(left, op, right);
  }
  
  // there are two notes here: 1) SHR will have the new leftmost bit same as the 
  // original leftmost bit, thus in case of leftmost bit is 1, it does not equal to / 2^k. 
  // 2) the good news is: although division in Java for negative odd numbers will be one 
  // bigger than right-shift, for example: -15 / 4 = -3, -15 >> 2 = -4, 'div' operation in 
  // yices have the same result as right-shift, meaning that -15 div 4 = -4. So replacing 
  // >>/>>> with 'div' in yices is precise in this situation.
  private Expression createShiftConstantExpr(Expression left, INSTANCE_OP op, Expression right) {
    // create number helper variables for the number operation
    left = makeHelperWhenNecessary(left, "number");
    
    INSTANCE_OP op2 = null;
    switch (op) {
      case SHL: // << in java
        op2 = INSTANCE_OP.MUL;
        break;
      case SHR: // >> in java
        op2 = INSTANCE_OP.DIV;
        break;
      case USHR: // >>> in java
        op2 = INSTANCE_OP.DIV;
        break;
      default: 
        op2 = INSTANCE_OP.DUMMY;
        break;
    }
    int rightConstant = Integer.parseInt(right.value);
    NormalExpr right2 = new NormalExpr(String.valueOf(((int) Math.pow(2.0, rightConstant))), "number");
    
    return new ArithmeticExpr(left, op2, right2, "number");
  }
  
  private Expression createArithmeticExpr(Expression left, INSTANCE_OP op, Expression right) {
    // create number helper variables for the number operation
    left  = makeHelperWhenNecessary(left, "number");
    right = makeHelperWhenNecessary(right, "number");

    Expression expression = new ArithmeticExpr(left, op, right, "number");
    if (op == INSTANCE_OP.ADD && right.value.equals("1")) {
      if (left instanceof ArithmeticExpr && ((ArithmeticExpr) left).op == INSTANCE_OP.ADD) {
        ArithmeticExpr leftCompExpr = (ArithmeticExpr) left;
        if (Utils.isInteger(leftCompExpr.right.value)) {
          int addedValue = Integer.parseInt(leftCompExpr.right.value) + 1;
          Expression newLeft  = leftCompExpr.left;
          Expression newRight = new NormalExpr(String.valueOf(addedValue), "number");
          expression = new ArithmeticExpr(newLeft, op, newRight, "number");
        }
      }
    }
    return expression;
  }
  
  private Expression makeHelperWhenNecessary(Expression expr, String destType) {
    if (!expr.type.equals(destType) && !expr.value.contains("%%UnboundField%%")) {
      Expression helper = m_helperExprs.get(expr);
      if (helper == null) {
        helper = new NormalExpr("$tmp_" + m_helperExprs.size(), destType);
        m_helperExprs.put(expr, helper);
      }
      expr = helper;
    }
    return expr;
  }
  
  private void addCommonContracts(Formula formula) {
    
    Instance lower = new Instance("#!0", "I", null);
    Instance upper = new Instance("#!10000", "I", null);
    HashSet<Instance> checked = new HashSet<Instance>();
    
    for (int i = 0; i < formula.getConditionList().size(); i++) {
      Condition condition = formula.getConditionList().get(i);
      HashSet<Instance> instances = condition.getRelatedInstances(formula.getRelationMap(), false, false, true, false);
      for (Instance instance : instances) {
        if (!checked.contains(instance)) {
          String fieldName = null;
          if (instance.isRelationRead()) {
            fieldName = instance.getLastReference().getReadRelName().toLowerCase();
          }
          else if (instance.getLastReference() != null && instance.getLastRefType().equals("I")) {
            fieldName = instance.getLastRefName().toLowerCase();
          }
          
          if (fieldName != null && 
             (fieldName.endsWith("size") || fieldName.endsWith("count") || fieldName.endsWith("length") || 
              fieldName.endsWith("cursor") || fieldName.endsWith("height"))) {
            Condition newContract1 = new Condition(
                new BinaryConditionTerm(instance, Comparator.OP_GREATER_EQUAL, lower));
            
            // this extra condition is useful in practice for preventing huge size arrays, 
            // but it may also cause unsound UNSAT in theory due to this upper bound
            Condition newContract2 = new Condition(
                new BinaryConditionTerm(instance, Comparator.OP_SMALLER, upper));
            
            formula.getConditionList().add(i + 1, newContract1);
            formula.getConditionList().add(i + 2, newContract2);
            i += 2;
          }

          checked.add(instance);
        }
      }
    }
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder("Solver Independent Input: \n");
    
    for (DefineType defineType : m_defineTypes) {
      str.append(defineType).append("\n");
    }
    for (DefineConstant defineConst : m_defines) {
      str.append(defineConst).append("\n");
    }
    for (DefineConstant defineHelper : m_defineHelpers) {
      str.append(defineHelper).append("\n");
    }
    for (DefineArray defineArray : m_defineArrays) {
      str.append(defineArray).append("\n");
    }
    for (Assertion assertions : m_assertions) {
      str.append(assertions).append("\n");
    }
    return str.toString();
  }

  public boolean keepUnboundField() {
    return m_keepUnboundField;
  }
  
  public boolean retrieveModel() {
    return m_retrieveModel;
  }
  
  public boolean retrieveUnsatCore() {
    return m_retrieveUnsatCore;
  }


  public Hashtable<String, long[]> getBasicTypes() {
    return m_basicTypes;
  }

  public Hashtable<String, long[]> getOtherTypes() {
    return m_otherTypes;
  }
  
  public Hashtable<String, List<long[]>> getTypeRanges() {
    return m_typeRanges;
  }
  
  public List<DefineConstant> getDefineConstants() {
    return m_defines;
  }
  
  public List<DefineConstant> getDefineHelpers() {
    return m_defineHelpers;
  }
  
  public List<DefineType> getDefineTypes() {
    return m_defineTypes;
  }
  
  public List<DefineArray> getDefineArrays() {
    return m_defineArrays;
  }
  
  public List<Assertion> getAssertions() {
    return m_assertions;
  }
  
  public Hashtable<Expression, Expression> getHelperMapping() {
    return m_helperExprs;
  }
  
  public Hashtable<String, Instance> getNameInstanceMapping() {
    return m_nameInstanceMapping;
  }
  
  public Hashtable<String, Instance> getConstInstanceMapping() {
    return m_constInstanceMapping;
  }
  
  public Hashtable<Instance, String> getInstanceNameMapping() {
    return m_instanceNameMapping;
  }
  
  public Hashtable<Assertion, List<Condition>> getAssertionCondsMapping() {
    return m_assertionCondsMapping;
  }

  public Hashtable<Condition, Assertion> getConditionAssertionMapping() {
    return m_conditionAssertionMapping;
  }

  private final boolean                            m_keepUnboundField;
  private final boolean                            m_retrieveModel;
  private final boolean                            m_retrieveUnsatCore;
  private final Hashtable<String, long[]>          m_basicTypes;
  private final Hashtable<String, long[]>          m_otherTypes;
  private final Hashtable<String, List<long[]>>    m_typeRanges;
  private final List<DefineConstant>               m_defines;
  private final List<DefineConstant>               m_defineHelpers;
  private final List<DefineType>                   m_defineTypes;
  private final List<DefineArray>                  m_defineArrays;
  private final List<Assertion>                    m_assertions;
  private final Hashtable<Expression, Expression>  m_helperExprs;
  private final Hashtable<String, Instance>        m_nameInstanceMapping;
  private final Hashtable<String, Instance>        m_constInstanceMapping;
  private final Hashtable<Instance, Expression>    m_instanceExprMapping;
  private final Hashtable<Instance, String>        m_instanceNameMapping;
  private final Hashtable<Condition, Assertion>    m_conditionAssertionMapping;
  private final Hashtable<Assertion, List<Condition>> m_assertionCondsMapping;
  
  private static final String STRING_TYPE = "Ljava/lang/String";
}
