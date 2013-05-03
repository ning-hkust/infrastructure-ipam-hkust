package hk.ust.cse.Prevision.Solver.Z3;

import hk.ust.cse.Prevision.Solver.NeutralInput;
import hk.ust.cse.Prevision.Solver.NeutralInput.ArithmeticExpr;
import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.AtomicAssertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.BVArithmeticExpr;
import hk.ust.cse.Prevision.Solver.NeutralInput.BinaryAssertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineArray;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineArrayStore;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineConstant;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineType;
import hk.ust.cse.Prevision.Solver.NeutralInput.Expression;
import hk.ust.cse.Prevision.Solver.NeutralInput.MultiAssertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.NormalExpr;
import hk.ust.cse.Prevision.Solver.NeutralInput.ReadArrayExpr;
import hk.ust.cse.Prevision.Solver.NeutralInput.TypeAssertion;
import hk.ust.cse.Prevision.Solver.SolverInput;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.ArraySort;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.DatatypeSort;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Sort;
import com.microsoft.z3.Symbol;
import com.microsoft.z3.TupleSort;
import com.microsoft.z3.Z3Exception;

public class Z3Input extends SolverInput {

  public Z3Input(Context context, NeutralInput neutralInput) throws Exception {
    super(neutralInput);
    
    m_ctx               = context;
    m_definedSorts      = new Hashtable<String, Sort>();
    m_definedConstants  = new Hashtable<String, Expr>();
    m_definedArrays     = new Hashtable<String, ArrayExpr>();
    m_assertionExprs    = new ArrayList<BoolExpr>();
    m_constantMapping   = new Hashtable<Expr, DefineConstant>();
    m_arrayMapping      = new Hashtable<ArrayExpr, DefineArray>();
    m_assertionMapping1 = new Hashtable<BoolExpr, Assertion>();
    m_assertionMapping2 = new Hashtable<Assertion, BoolExpr>();
    m_intNumCache       = new Hashtable<Long, IntNum>();
    m_arraySelectCache  = new Hashtable<String, Expr>();
    m_addedInRangesAssertion = new HashSet<String>();

    defineSorts();
    defineConstants();
    defineArrays();
    createAssertionExprs();
    createHelperAssertionExprs();
  }

  // since we cannot find the sub-type (subrange) support in z3, we need to use explicit assertions.
  private void defineSorts() throws Z3Exception {
    for (DefineType defineType : m_neutralInput.getDefineTypes()) {
      if (!defineType.type.equals("F") && !defineType.type.equals("D")) {
        m_definedSorts.put(defineType.type, m_ctx.mkIntSort());
      }
    }
    m_definedSorts.put("F", m_ctx.mkRealSort());
    m_definedSorts.put("D", m_ctx.mkRealSort());
    m_definedSorts.put("bitvector32", m_ctx.mkBitVecSort(32));
  }
  
  private void defineConstants() throws Z3Exception {
    // define normal constants
    for (DefineConstant defineConstant : m_neutralInput.getDefineConstants()) {
      Expr constant = null;
      if (defineConstant.value != null) { // known constant
        constant = mkIntNum(defineConstant.value);
      }
      else {
        Sort sort = findTypeSort(defineConstant.type);
        constant = m_ctx.mkConst(defineConstant.name, sort);
        m_assertionExprs.add(createInRangesAssertionExpr(constant, defineConstant.type, true));
        m_addedInRangesAssertion.add(defineConstant.name);
      }
      m_definedConstants.put(defineConstant.name, constant);
      m_constantMapping.put(constant, defineConstant);
    }
    
    // define helper constants
    for (DefineConstant defineHelper : m_neutralInput.getDefineHelpers()) {
      Sort sort = findTypeSort(defineHelper.type);
      Expr constant = m_ctx.mkConst(defineHelper.name, sort);
      m_definedConstants.put(defineHelper.name, constant);
      m_constantMapping.put(constant, defineHelper);
      
      if (!defineHelper.type.equals("bitvector32")) {
        m_assertionExprs.add(createInRangesAssertionExpr(constant, defineHelper.type, true));
        m_addedInRangesAssertion.add(defineHelper.name);
      }
    }
  }
  
  private void defineArrays() throws Exception {
    for (DefineArray defineArray : m_neutralInput.getDefineArrays()) {
      // create a tuple sort to hold domains
      Sort[] domainSorts     = new Sort[defineArray.paramTypes.length];
      Symbol[] domainSymbols = new Symbol[defineArray.paramTypes.length];
      for (int i = 0; i < defineArray.paramTypes.length; i++) {
        domainSorts[i]   = findTypeSort(defineArray.paramTypes[i]);
        domainSymbols[i] = m_ctx.mkSymbol("param_" + (i + 1));
      }
      TupleSort domainsSort = m_ctx.mkTupleSort(m_ctx.mkSymbol("domains"), domainSymbols, domainSorts);
      Sort rangeSort = findTypeSort(defineArray.returnType);
      
      // define array constant
      if (!(defineArray instanceof DefineArrayStore)) { // initial array definition
        ArrayExpr array = m_ctx.mkArrayConst(defineArray.arrayName, domainsSort, rangeSort);
        m_definedArrays.put(defineArray.arrayName, array);
        m_arrayMapping.put(array, defineArray);
      }
      else { // array store
        DefineArrayStore defineArrayStore = (DefineArrayStore) defineArray;
        ArrayExpr fromArray = findArrayExpr(defineArrayStore.getStoreFromArrayName());
        
        // define a new array constant: f@2
        String newArrayName = defineArrayStore.getStoreToArrayName();
        ArrayExpr array = m_ctx.mkArrayConst(newArrayName, domainsSort, rangeSort);
        m_definedArrays.put(newArrayName, array);
        m_arrayMapping.put(array, defineArrayStore);

        // create expression: (store f domains newValue)
        Expr tuple      = domainsSort.mkDecl().apply(findConstantExprs(defineArrayStore.domains));
        Expr rangeValue = findConstantExpr(defineArrayStore.value);
        
        ArrayExpr storedArray = null;
        if (fromArray == null && !m_neutralInput.keepUnboundField()) {
          throw new Exception("Could not create solver input due to the removal of %%UnboundField%%. Good to go on!");
        }
        else {
          storedArray = m_ctx.mkStore(fromArray, tuple, rangeValue);
        }
        
        // (assert (= f@2 (store f domains newValue)))
        m_assertionExprs.add(m_ctx.mkEq(array, storedArray));
      }
    }
  }
  
  private void createAssertionExprs() throws Z3Exception {
    for (Assertion assertion : m_neutralInput.getAssertions()) {
      BoolExpr expr = createAssertionExpr((Assertion) assertion);
      m_assertionExprs.add(expr);
      m_assertionMapping1.put(expr, assertion);
      m_assertionMapping2.put(assertion, expr);
    }
  }
  
  private void createHelperAssertionExprs() throws Z3Exception {
    Hashtable<Expression, Expression> helperMapping = m_neutralInput.getHelperMapping();
    Enumeration<Expression> keys = helperMapping.keys();
    while (keys.hasMoreElements()) {
      Expression normal = (Expression) keys.nextElement();
      Expression helper = helperMapping.get(normal);
      
      Expr expr1 = findConstantExpr(normal);
      Expr expr2 = findConstantExpr(helper);
      if (helper.type.equals("bitvector32")) {
        m_assertionExprs.add(m_ctx.mkEq(expr1, BV2Int((BitVecExpr) expr2, true)));
      }
      else {
        m_assertionExprs.add(m_ctx.mkEq(expr2, BV2Int((BitVecExpr) expr1, true)));
      }
    }
  }
  
  private BoolExpr createAssertionExpr(Assertion assertion) throws Z3Exception {
    BoolExpr assertionExpr = null;
    
    if (assertion instanceof AtomicAssertion) {
      assertionExpr = createAssertionExpr((AtomicAssertion) assertion);
    }
    else if (assertion instanceof BinaryAssertion) {
      assertionExpr = createAssertionExpr((BinaryAssertion) assertion);
    }
    else if (assertion instanceof TypeAssertion) {
      assertionExpr = createAssertionExpr((TypeAssertion) assertion);
    }
    else if (assertion instanceof MultiAssertion) {
      assertionExpr = createAssertionExpr((MultiAssertion) assertion);
    }
    else {
      assertionExpr = m_ctx.mkTrue(); // skip this assertion
    }
    return assertionExpr;
  }
  
  private BoolExpr createAssertionExpr(AtomicAssertion assertion) throws Z3Exception {
    if (assertion.value.equals("true")) {
      return m_ctx.mkTrue();
    }
    else {
      return m_ctx.mkFalse();
    }
  }
  
  private BoolExpr createAssertionExpr(BinaryAssertion assertion) throws Z3Exception {
    BoolExpr assertionExpr = null;

    ArithExpr expr1 = (ArithExpr) findConstantExpr(assertion.expr1);
    ArithExpr expr2 = (ArithExpr) findConstantExpr(assertion.expr2);
    switch (assertion.comp) {
    case OP_EQUAL:
      assertionExpr = m_ctx.mkEq(expr1, expr2);
      break;
    case OP_GREATER:
      assertionExpr = m_ctx.mkGt(expr1, expr2);
      break;
    case OP_GREATER_EQUAL:
      assertionExpr = m_ctx.mkGe(expr1, expr2);
      break;
    case OP_INEQUAL:
      assertionExpr = m_ctx.mkNot(m_ctx.mkEq(expr1, expr2));
      break;
    case OP_SMALLER:
      assertionExpr = m_ctx.mkLt(expr1, expr2);
      break;
    case OP_SMALLER_EQUAL:
      assertionExpr = m_ctx.mkLe(expr1, expr2);
      break;
    default:
      assertionExpr = m_ctx.mkTrue();
      break;
    }
    return assertionExpr;
  }
  
  private BoolExpr createAssertionExpr(TypeAssertion assertion) throws Z3Exception {
    BoolExpr assertionExpr = null;

    ArithExpr expr = (ArithExpr) findConstantExpr(assertion.expr);
    List<long[]> subRanges = m_neutralInput.getTypeRanges().get(assertion.typeString);
    switch (assertion.comp) {
    case OP_INSTANCEOF:
      BoolExpr[] andExprs = new BoolExpr[subRanges.size()];
      for (int i = 0, size = subRanges.size(); i < size; i++) {
        andExprs[i] = m_ctx.mkAnd(m_ctx.mkGe(expr, mkIntNum(subRanges.get(i)[0])), 
                                  m_ctx.mkLe(expr, mkIntNum(subRanges.get(i)[1])));
      }
      assertionExpr = m_ctx.mkOr(andExprs);
      break;
    case OP_NOT_INSTANCEOF:
      BoolExpr[] orExprs = new BoolExpr[subRanges.size()];
      for (int i = 0, size = subRanges.size(); i < size; i++) {
        orExprs[i] = m_ctx.mkOr(m_ctx.mkLt(expr, mkIntNum(subRanges.get(i)[0])), 
                                m_ctx.mkGt(expr, mkIntNum(subRanges.get(i)[1])));
      }
      assertionExpr = m_ctx.mkAnd(orExprs);
      break;
    default:
      assertionExpr = m_ctx.mkTrue();
      break;
    }
    return assertionExpr;
  }
  
  private BoolExpr createAssertionExpr(MultiAssertion assertion) throws Z3Exception {
    BoolExpr assertionExpr = null;
    
    BoolExpr[] subExprs = new BoolExpr[assertion.assertions.length];
    for (int i = 0; i < assertion.assertions.length; i++) {
      subExprs[i] = createAssertionExpr((assertion.assertions[i]));
    }
    
    if (subExprs.length == 1) {
      // no need to connect
      assertionExpr = subExprs[0];
    }
    else if (assertion.connector.equals("or")) {
      assertionExpr = m_ctx.mkOr(subExprs);
    }
    else if (assertion.connector.equals("and")) {
      assertionExpr = m_ctx.mkAnd(subExprs);
    }
    else {
      assertionExpr = m_ctx.mkTrue();
    }
    return assertionExpr;
  }
  
  private BoolExpr createInRangesAssertionExpr(Expr constant, String typeName, boolean inclNull) throws Z3Exception {
    List<long[]> ranges = m_neutralInput.getTypeRanges().get(typeName);
    
    List<BoolExpr> andExprs = new ArrayList<BoolExpr>();
    for (int i = 0, size = ranges.size(); i < size; i++) {
      andExprs.add(m_ctx.mkAnd(m_ctx.mkGe((ArithExpr) constant, mkIntNum(ranges.get(i)[0])), 
                               m_ctx.mkLe((ArithExpr) constant, mkIntNum(ranges.get(i)[1]))));
    }
    if (typeName.equals("I") || typeName.equals("S") || typeName.equals("J") || typeName.equals("D") || typeName.equals("F")) {
      andExprs.add(m_ctx.mkEq((ArithExpr) constant, mkIntNum(0)));
      andExprs.add(m_ctx.mkEq((ArithExpr) constant, mkIntNum(1)));
      andExprs.add(m_ctx.mkEq((ArithExpr) constant, mkIntNum(2)));
      andExprs.add(m_ctx.mkAnd(m_ctx.mkGe((ArithExpr) constant, mkIntNum(-10)), 
                               m_ctx.mkLe((ArithExpr) constant, mkIntNum(10))));
    }
    else if (typeName.equals("C")) {
      andExprs.add(m_ctx.mkAnd(m_ctx.mkGe((ArithExpr) constant, mkIntNum(32)), 
                               m_ctx.mkLe((ArithExpr) constant, mkIntNum(126))));
    }
    else if (m_neutralInput.getBasicTypes().get(typeName) == null && inclNull) {
      andExprs.add(m_ctx.mkEq((ArithExpr) constant, findConstantExpr("null")));
    }
    
    return m_ctx.mkOr(andExprs.toArray(new BoolExpr[andExprs.size()]));
  }
  
  private IntExpr BV2Int(BitVecExpr bv, boolean signed) throws Z3Exception {
    return BV2Int(bv, signed, 32);
  }
  
  private IntExpr BV2Int(BitVecExpr bv, boolean signed, int nbit) throws Z3Exception {
    IntExpr ret = null;
    if (nbit == 1) {
      ret = (IntExpr) m_ctx.mkITE(m_ctx.mkEq(bv, m_ctx.mkBV(0, 32)), mkIntNum(0), mkIntNum(1));
    }
    else { 
      int halfb = nbit / 2;
      int mulBy = (int) Math.pow(2, halfb);
      IntExpr intL = BV2Int(m_ctx.mkBVAND(bv, m_ctx.mkBV(mulBy - 1, 32)), signed, halfb);
      IntExpr intH = BV2Int(m_ctx.mkBVLSHR(bv, m_ctx.mkBV(halfb, 32)), signed, halfb);
      ret = (IntExpr) m_ctx.mkAdd(m_ctx.mkMul(intH, mkIntNum(mulBy)), intL);
    }
    
    // convert to signed integer
    if (nbit == 32 && signed) {
      ret = (IntExpr) m_ctx.mkITE(m_ctx.mkGt(ret, 
          mkIntNum(2147483647)), m_ctx.mkSub(ret, mkIntNum(4294967296L)), ret);
    }
    return ret;
  }
  
  private Sort findTypeSort(String typeName) {
    return m_definedSorts.get(typeName);
  }

  private Expr[] findConstantExprs(Expression[] constants) throws Z3Exception {
    Expr[] exprs = new Expr[constants.length];
    for (int i = 0; i < constants.length; i++) {
      exprs[i] = findConstantExpr(constants[i]);
    }
    return exprs;
  }
  
  private Expr findConstantExpr(Expression constant) throws Z3Exception {
    Expr expr = null;
    
    if (constant instanceof NormalExpr) {
      expr = findConstantExpr((NormalExpr) constant);
    }
    else if (constant instanceof BVArithmeticExpr) {
      expr = findConstantExpr((BVArithmeticExpr) constant);
    }
    else if (constant instanceof ArithmeticExpr) {
      expr = findConstantExpr((ArithmeticExpr) constant);
    }
    else if (constant instanceof ReadArrayExpr) {
      expr = findConstantExpr((ReadArrayExpr) constant);
    }
    else {
      expr = m_ctx.mkTrue(); // skip this expression
    }
    return expr;
  }

  private Expr findConstantExpr(String value) {
    return m_definedConstants.get(value);
  }
  
  private Expr findConstantExpr(NormalExpr constant) throws Z3Exception {
    Expr expr = m_definedConstants.get(constant.value);
    if (expr == null) {
      try {
        expr = mkIntNum(constant.value);
      } catch (Z3Exception e) {}
    }
    if (expr == null) {
      try {
        expr = m_ctx.mkReal(constant.value);
      } catch (Z3Exception e) {}
    }
    if (expr == null) {
      expr = constant.value.equals("true") ? m_ctx.mkBool(true) : 
             constant.value.equals("false") ? m_ctx.mkBool(false) : null;
    }
    return expr;
  }
  
  private Expr findConstantExpr(ArithmeticExpr constant) throws Z3Exception {
    Expr expr = null;

    ArithExpr left  = (ArithExpr) findConstantExpr(constant.left);
    ArithExpr right = (ArithExpr) findConstantExpr(constant.right);
    switch (constant.op) {
      case ADD:
        expr = m_ctx.mkAdd(left, right);
        break;
      case SUB:
        expr = m_ctx.mkSub(left, right);
        break;
      case MUL:
        expr = m_ctx.mkMul(left, right);
        break;
      case DIV:
        expr = m_ctx.mkDiv(left, right); // can handle both integer and real divisions
        m_assertionExprs.add(m_ctx.mkNot(m_ctx.mkEq(right, mkIntNum(0))));
        break;
      case REM:
        expr = m_ctx.mkMod((IntExpr) left, (IntExpr) right); // not mkRem
        break;
      default:
        expr = m_ctx.mkTrue(); // skip this expression
        break;
    }
    return expr;
  }
  
  private Expr findConstantExpr(BVArithmeticExpr constant) throws Z3Exception {
    Expr expr = null;
    
    BitVecExpr left  = (BitVecExpr) findConstantExpr(constant.left);
    BitVecExpr right = (BitVecExpr) findConstantExpr(constant.right);
    switch (constant.op) {
    case SHL: // << in java
      expr = m_ctx.mkBVSHL(left, right);
      break;
    case SHR: // >> in java
      expr = m_ctx.mkBVASHR(left, right);
      break;
    case USHR: // >>> in java
      expr = m_ctx.mkBVLSHR(left, right);
      break;
    case AND:
      expr = m_ctx.mkBVAND(left, right);
      break;
    case OR:
      expr = m_ctx.mkBVOR(left, right);
      break;
    case XOR:
      expr = m_ctx.mkBVXOR(left, right);
      break;  
    default:
      expr = m_ctx.mkTrue(); // skip this expression
      break;
    }
    return expr;
  }
  
  private Expr findConstantExpr(ReadArrayExpr constant) throws Z3Exception {
    Expr arraySelect = m_arraySelectCache.get(constant.toString());
    if (arraySelect != null) {
      return arraySelect;
    }

    ArrayExpr array = m_definedArrays.get(constant.getReadAtArrayName());
    DatatypeSort domainsSort = (DatatypeSort) ((ArraySort) array.getSort()).getDomain();
    Expr tuple = domainsSort.getConstructors()[0].apply(findConstantExprs(constant.domains));
    arraySelect = m_ctx.mkSelect(array, tuple);

    // constraint the value of the selected element according to type
    if (constant.arrayName.startsWith("@@array")) {
      DefineConstant refConstant = m_constantMapping.get(tuple.getArgs()[0]);
      if (refConstant != null && refConstant.type != null && refConstant.type.length() > 0) {
        String elemType = refConstant.type.substring(1);
        if (m_definedSorts.containsKey(elemType)) {
          if (!m_addedInRangesAssertion.contains(constant.toString())) {
            m_assertionExprs.add(createInRangesAssertionExpr(arraySelect, elemType, true));
            m_addedInRangesAssertion.add(constant.toString());
          }
        }
      }
    }
    else {
      // constraint the value of the selected element according to return type
      DefineArray definedArray = m_arrayMapping.get(array);
      if (m_definedSorts.containsKey(definedArray.returnType)) {
        if (!m_addedInRangesAssertion.contains(constant.toString())) {
          m_assertionExprs.add(createInRangesAssertionExpr(arraySelect, definedArray.returnType, true));
          m_addedInRangesAssertion.add(constant.toString());
        }
      }
      // constraint the value of the reference element according to field reference type
      if (m_definedSorts.containsKey(definedArray.paramTypes[0])) {
        Expr fieldRefExpr = findConstantExpr(constant.domains[0]);
        if (!m_addedInRangesAssertion.contains(fieldRefExpr.toString())) {
          m_assertionExprs.add(createInRangesAssertionExpr(fieldRefExpr, definedArray.paramTypes[0], false));
          m_addedInRangesAssertion.add(fieldRefExpr.toString());
        }
      }
    }
    
    m_arraySelectCache.put(constant.toString(), arraySelect);
    return arraySelect;
  }
  
  private ArrayExpr findArrayExpr(String arrayName) {
    return m_definedArrays.get(arrayName);
  }
  
  private IntNum mkIntNum(String strValue) throws Z3Exception {
    Long numValue = null;
    try {
      numValue = Long.parseLong(strValue);
    } catch (NumberFormatException e) {}
    return numValue != null ? mkIntNum(numValue) : m_ctx.mkInt(strValue);
  }
  
  private IntNum mkIntNum(long intValue) throws Z3Exception {
    IntNum intNum = m_intNumCache.get(intValue);
    if (intNum == null) {
      intNum = m_ctx.mkInt(intValue);
      m_intNumCache.put(intValue, intNum);
    }
    return intNum;
  }
  
  public Context getContext() {
    return m_ctx;
  }

//  public static void main(String[] args) throws Z3Exception {
//
//    Context ctx = new Context();
//    Solver solver = ctx.mkSolver();
//    
//    IntExpr expr1 = ctx.mkIntConst("int1");
//    IntExpr expr2 = ctx.mkIntConst("int2");
//    BoolExpr assertion = ctx.mkEq(expr1, expr2);
//
//    BoolExpr tracker1 = ctx.mkBoolConst("tracker1");
//    solver.add(assertion);
//    System.out.println(solver.check());
//    
//    solver.push();
//    BoolExpr tracker2 = ctx.mkBoolConst("tracker2");
//    BoolExpr assertion2 = ctx.mkEq(expr1, ctx.mkInt(1));
//    solver.add(assertion2);
//    System.out.println(solver.check());
//    System.out.println(solver.getModel());
//
//    BoolExpr tracker3 = ctx.mkBoolConst("tracker3");
//    BoolExpr assertion3 = ctx.mkEq(expr2, ctx.mkInt(2));
//    solver.add(assertion3);
//    System.out.println(solver.check());
//    Expr[] unsatCore = solver.getUnsatCore();
//    for (Expr unsat : unsatCore) {
//      System.out.println(unsat);
//    }
//    
//    solver.pop();
//    solver.add(assertion3);
//    System.out.println(solver.check());
//    System.out.println(solver.getModel());
//
//    ctx.dispose();
//  }
  
  public Hashtable<String, Sort> getDefinedSorts() {
    return m_definedSorts;
  }
  
  public Hashtable<String, Expr> getDefinedConstants() {
    return m_definedConstants;
  }
  
  public Hashtable<String, ArrayExpr> getDefinedArrays() {
    return m_definedArrays;
  }
  
  public List<BoolExpr> getAssertionExprs() {
    return m_assertionExprs;
  }
  
  public Hashtable<Expr, DefineConstant> getConstantMapping() {
    return m_constantMapping;
  }
  
  public Hashtable<ArrayExpr, DefineArray> getArrayMapping() {
    return m_arrayMapping;
  }
  
  public Hashtable<BoolExpr, Assertion> getAssertionMapping1() {
    return m_assertionMapping1;
  }
  
  public Hashtable<Assertion, BoolExpr> getAssertionMapping2() {
    return m_assertionMapping2;
  }
  
  private final Context                           m_ctx;
  private final Hashtable<String, Sort>           m_definedSorts;
  private final Hashtable<String, Expr>           m_definedConstants;
  private final Hashtable<String, ArrayExpr>      m_definedArrays;
  private final List<BoolExpr>                    m_assertionExprs;
  private final Hashtable<Expr, DefineConstant>   m_constantMapping;
  private final Hashtable<ArrayExpr, DefineArray> m_arrayMapping;
  private final Hashtable<BoolExpr, Assertion>    m_assertionMapping1;
  private final Hashtable<Assertion, BoolExpr>    m_assertionMapping2;
  private final Hashtable<Long, IntNum>           m_intNumCache;
  private final Hashtable<String, Expr>           m_arraySelectCache;
  private final HashSet<String>                   m_addedInRangesAssertion;
}
