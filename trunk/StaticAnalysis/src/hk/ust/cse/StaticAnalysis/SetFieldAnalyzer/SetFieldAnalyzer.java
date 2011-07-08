package hk.ust.cse.StaticAnalysis.SetFieldAnalyzer;

import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

public class SetFieldAnalyzer {
  public SetFieldAnalyzer(String appJar) throws Exception {
    m_walaAnalyzer = new WalaAnalyzer(appJar);
  }
  
  public SetFieldAnalyzer(WalaAnalyzer walaAnalyzer) {
    m_walaAnalyzer = walaAnalyzer;
  }
  
  public Hashtable<String, List<Method>> findAllSetFields(String targetCls, int maxLookDepth) throws ClassNotFoundException {
    Class<?> cls = Class.forName(targetCls);
    return findAllSetFields(cls, maxLookDepth);
  }
  
  public Hashtable<String, List<Method>> findAllSetFields(Class<?> targetCls, int maxLookDepth) {
    Hashtable<String, List<Method>> setFields = new Hashtable<String, List<Method>>();
    
    Method[] allPublics = Utils.getPublicMethods(targetCls);
    for (Method method : allPublics) {
      // skip abstract or native methods
      if (Modifier.isAbstract(method.getModifiers()) || 
          Modifier.isNative(method.getModifiers())) {
        continue;
      }
      
      // getIR
      IR ir = Jar2IR.getIR(m_walaAnalyzer, method);
      if (ir == null) {
        continue;
      }
      
      // get fields set
      List<String> fieldsSet = getAllFieldsSet(ir, 0, maxLookDepth);
      for (String fieldSet : fieldsSet) {
        List<Method> methodList = setFields.get(fieldSet);
        if (methodList == null) {
          methodList = new ArrayList<Method>();
          setFields.put(fieldSet, methodList);
        }
        methodList.add(method);
      }
    }
    
    return setFields;
  }
  
  private List<String> getAllFieldsSet(IR ir, int curDepth, int maxDepth) {
    List<String> allFieldsSet = new ArrayList<String>();
    
    SSAInstruction[] insts = ir.getInstructions();
    for (int i = 0; i < insts.length; i++) {
      if (insts[i] instanceof SSAPutInstruction) {
        SSAPutInstruction putfieldInst = (SSAPutInstruction) insts[i];
        
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
      else if (insts[i] instanceof SSAInvokeInstruction && curDepth < maxDepth) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) insts[i];
        
        IR ir2 = Jar2IR.getIR(m_walaAnalyzer, invokeInst.getDeclaredTarget().getSignature());
        if (ir2 != null) {
          // recursively call getAllFieldsSet
          List<String> allFieldsSet2 = getAllFieldsSet(ir2, curDepth + 1, maxDepth);
          for (String field : allFieldsSet2) {
            if (!allFieldsSet.contains(field)) {
              allFieldsSet.add(field);
            }
          }
        }
      }
      else {
        continue;
      }
    }
    return allFieldsSet;
  }
  
  public static void main(String[] args) throws Exception {
    SetFieldAnalyzer setFieldAnalyzer = new SetFieldAnalyzer("./test_programs/test_program.jar");
    Hashtable<String, List<Method>> setFields = setFieldAnalyzer.findAllSetFields(
        "test_program", 5);
    
    Enumeration<String> fields = setFields.keys();
    while (fields.hasMoreElements()) {
      String field = (String) fields.nextElement();
      List<Method> methodList = setFields.get(field);
      
      System.out.println("Member Field: " + field);
      for (Method method : methodList) {
        System.out.println(method.toGenericString());
      }
      System.out.println("-----------------------");
    }
  }
  
  private final WalaAnalyzer m_walaAnalyzer;
}
