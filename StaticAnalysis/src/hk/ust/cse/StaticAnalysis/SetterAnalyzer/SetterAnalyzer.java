package hk.ust.cse.StaticAnalysis.SetterAnalyzer;

import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

public class SetterAnalyzer {
  public SetterAnalyzer(String appJar) throws Exception {
    m_walaAnalyzer = new WalaAnalyzer(appJar);
  }
  
  public Hashtable<String, List<Method>> findAllSetters(String targetCls) throws ClassNotFoundException {
    Class<?> cls = Class.forName(targetCls);
    return findAllSetters(cls);
  }
  
  public Hashtable<String, List<Method>> findAllSetters(Class<?> targetCls) {
    Hashtable<String, List<Method>> setters = 
      new Hashtable<String, List<Method>>();
    
    Method[] allPublics = getPublicMethods(targetCls);
    for (Method method : allPublics) {
      // skip abstract or native methods
      if (Modifier.isAbstract(method.getModifiers()) || 
          Modifier.isNative(method.getModifiers())) {
        continue;
      }
      
      String setterFor = isSetter(method);
      if (setterFor != null && setterFor.length() > 0) {
        List<Method> settersForMember = setters.get(setterFor);
        if (settersForMember == null) {
          settersForMember = new ArrayList<Method>();
          setters.put(setterFor, settersForMember);
        }
        settersForMember.add(method);
      }
    }
    
    return setters;
  }
  
  private Method[] getPublicMethods(Class<?> cls) {
    Method[] allPublicMethods = cls.getMethods();
    // sort, because getMethods() does not guarantee order
    Arrays.sort(allPublicMethods, new Comparator<Method>() {
      public int compare(Method o1, Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return allPublicMethods;
  }
  
  /**
   * @param method: check if 'method' is a setter for any member of the target class
   * @return: if 'method' is a setter, return the member name, else return null
   */
  private String isSetter(Method method) {
    // getIR
    IR ir = Jar2IR.getIR(m_walaAnalyzer, method);
    if (ir == null) {
      return null;
    }
    
    // check conditions
    
    // condition 1: there is only one parameter for this method
    if (method.getParameterTypes().length != 1) {
      return null;
    }
    
    // condition 2: this parameter only sets one member field
    List<String> allFieldsSet = getAllFieldsSetByParam(ir, ir.getMethod().isStatic() ? 1 : 2);
    if (allFieldsSet.size() != 1) {
      return null;
    }
    
    // condition 3: parameter type and field type is the same
    String typeName = Utils.toPrimitiveType(Utils.getClassTypeJavaStr(method.getParameterTypes()[0].getName()));
    if (!allFieldsSet.get(0).startsWith(typeName + " ")) {
      return null;
    }
    
    // passed all condition checks
    return allFieldsSet.get(0);
  }
  
  private List<String> getAllFieldsSetByParam(IR ir, int param) {
    List<String> allFieldsSet = new ArrayList<String>();
    
    SSAInstruction[] insts = ir.getInstructions();
    for (int i = 0; i < insts.length; i++) {
      if (!(insts[i] instanceof SSAPutInstruction)) {
        continue;
      }
      
      SSAPutInstruction putfieldInst = (SSAPutInstruction) insts[i];
      
      // we only want fields set by value of param
      if (param != putfieldInst.getVal()) {
        continue;
      }
      
      String fieldType     = Utils.toPrimitiveType(Utils.getClassTypeJavaStr(
          putfieldInst.getDeclaredFieldType().getName().toString()));
      String declClassName = Utils.toPrimitiveType(Utils.getClassTypeJavaStr(
          putfieldInst.getDeclaredField().getDeclaringClass().getName().toString()));
      String declFieldName = putfieldInst.getDeclaredField().getName().toString();
      
      String fullFieldString = fieldType + " " + declClassName + "." + declFieldName;
      if (!allFieldsSet.contains(fullFieldString)) {
        allFieldsSet.add(fullFieldString);
      }
    }
    return allFieldsSet;
  }
  
  public static void main(String[] args) throws Exception {
    SetterAnalyzer setterAnalyzer = new SetterAnalyzer("./test_programs/test_program.jar");
    Hashtable<String, List<Method>> setters = setterAnalyzer.findAllSetters(
        "test_program");
    
    Enumeration<String> members = setters.keys();
    while (members.hasMoreElements()) {
      String member = (String) members.nextElement();
      List<Method> setterList = setters.get(member);
      
      System.out.println("Member Field: " + member);
      for (Method setter : setterList) {
        System.out.println(setter.toGenericString());
      }
      System.out.println("-----------------------");
    }
  }
  
  private final WalaAnalyzer m_walaAnalyzer;
}
