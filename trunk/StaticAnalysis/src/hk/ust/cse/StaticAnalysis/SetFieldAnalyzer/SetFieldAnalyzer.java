package hk.ust.cse.StaticAnalysis.SetFieldAnalyzer;

import hk.ust.cse.Prevision_PseudoImpl.PseudoImplMap;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.SubClassHack;
import hk.ust.cse.Wala.WalaAnalyzer;
import hk.ust.cse.util.LRUCache;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;

public class SetFieldAnalyzer {
  public SetFieldAnalyzer(String appJar, String pseudoImplJarFile, int accessibilty) throws Exception {
    m_walaAnalyzer = new WalaAnalyzer(appJar);
    if (pseudoImplJarFile != null) {
      m_walaAnalyzer.addJarFile(pseudoImplJarFile);
    }
    m_usePseudo = pseudoImplJarFile != null;
    
    m_accessibility = accessibilty;
    m_setFieldCache = new LRUCache(100);
  }
  
  public SetFieldAnalyzer(WalaAnalyzer walaAnalyzer, boolean usePseudo, int accessibilty) {
    m_walaAnalyzer  = walaAnalyzer;
    m_usePseudo     = usePseudo;
    m_accessibility = accessibilty;
    m_setFieldCache = new LRUCache(100);
  }
  
  public Hashtable<String, List<Object[]>> findAllSetFieldsWithDepth(String targetCls, int maxLookDepth, 
      boolean inclCtors, boolean inclFieldType) {
    Class<?> cls = Utils.findClass(targetCls);
    return findAllSetFieldsWithDepth(cls, maxLookDepth, inclCtors, inclFieldType);
  }
  
  public Hashtable<String, List<Object[]>> findAllSetFieldsWithDepth(Class<?> targetCls, int maxLookDepth, 
      boolean inclCtors,boolean inclFieldType) {
    Hashtable<String, List<Object[]>> setFields = new Hashtable<String, List<Object[]>>();
    
    // obtain methods and constructors
    Method[] allMethods       = null;
    Constructor<?>[] allCtors = null;
    if (m_accessibility == 0) {
      allMethods = Utils.getPublicMethods(targetCls);;
      allCtors   = Utils.getPublicCtors(targetCls);
    }
    else {
      allMethods = Utils.getNonPrivateMethods(targetCls);
      allCtors   = Utils.getNonPrivateCtors(targetCls);
    }
    
    // add all to one list
    List<Member> methodOrCtors = new ArrayList<Member>();
    methodOrCtors.addAll(Arrays.asList(allMethods));
    methodOrCtors.addAll(Arrays.asList(allCtors));
    
    for (Member methodOrCtor : methodOrCtors) {
      // skip abstract or native methods
      if (Modifier.isAbstract(methodOrCtor.getModifiers()) || 
          Modifier.isNative(methodOrCtor.getModifiers())) {
        continue;
      }
      
      // getIR
      IR ir = Jar2IR.getIR(m_walaAnalyzer, methodOrCtor);
      if (ir == null) {
        continue;
      }
      // use pseudo implementations when necessary
      if (m_usePseudo) {
        String pseudoImpl = PseudoImplMap.findPseudoImpl(ir.getMethod().getSignature());
        if (pseudoImpl != null) {
          ir = Jar2IR.getIR(m_walaAnalyzer, pseudoImpl);
        }
      }
      
      // get fields set
      List<Object[]> fieldsSet = getAllFieldsSet(ir, 0, maxLookDepth, inclFieldType, new boolean[] {false});
      for (Object[] fieldSet : fieldsSet) {
        List<Object[]> methodList = setFields.get(fieldSet[0]);
        if (methodList == null) {
          methodList = new ArrayList<Object[]>();
          setFields.put((String) fieldSet[0], methodList);
        }
        methodList.add(new Object[] {methodOrCtor, fieldSet[1]});
      }
    }
    
    return setFields;
  }
  
  public Hashtable<String, List<Member>> findAllSetFields(String targetCls, int maxLookDepth, 
      boolean inclCtors, boolean inclFieldType) {
    Class<?> cls = Utils.findClass(targetCls);
    return findAllSetFields(cls, maxLookDepth, inclCtors, inclFieldType);
  }
  
  public Hashtable<String, List<Member>> findAllSetFields(Class<?> targetCls, int maxLookDepth, 
      boolean inclCtors, boolean inclFieldType) {
    Hashtable<String, List<Member>> setFields = new Hashtable<String, List<Member>>();
    
    Hashtable<String, List<Object[]>> setFieldsWithDepth = 
      findAllSetFieldsWithDepth(targetCls, maxLookDepth, inclCtors, inclFieldType);
    Enumeration<String> keys = setFieldsWithDepth.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      List<Object[]> methodWithDepthList = setFieldsWithDepth.get(key);

      List<Member> methodList = new ArrayList<Member>();
      for (Object[] methodWithDepth : methodWithDepthList) {
        methodList.add((Member) methodWithDepth[0]);
      }
      setFields.put(key, methodList);
    }
    
    return setFields;
  }
  
  // list of fields set and the corresponding set depths
  @SuppressWarnings("unchecked")
  private List<Object[]> getAllFieldsSet(IR ir, int curDepth, int maxDepth, boolean inclFieldType, boolean[] reachedMaxDepth) {
    
    List<Object[]> allFieldsSet = (List<Object[]>) m_setFieldCache.find(ir);
    if (allFieldsSet != null) {
      if (curDepth > 0) {
        // modify the depth
        List<Object[]> allFieldsSetToOut = (List<Object[]>) ((ArrayList<Object[]>) allFieldsSet).clone();
        for (Object[] fieldSet : allFieldsSetToOut) {
          fieldSet[1] = ((Integer) fieldSet[1]) + curDepth;
        }
        allFieldsSet = allFieldsSetToOut;
      }
    }
    else {
      boolean oriReachedMaxDepth = reachedMaxDepth[0];
      reachedMaxDepth = new boolean[] {false};
      allFieldsSet = getAllFieldsSetRec(ir, curDepth, maxDepth, inclFieldType, reachedMaxDepth);
      
      if (!reachedMaxDepth[0] || curDepth == 0) {
        List<Object[]> allFieldsSetToSave = allFieldsSet;
        if (curDepth > 0) {
          // modify the depth
          allFieldsSetToSave = (List<Object[]>) ((ArrayList<Object[]>) allFieldsSet).clone();
          for (Object[] fieldSet : allFieldsSetToSave) {
            fieldSet[1] = ((Integer) fieldSet[1]) - curDepth;
          }
        }
        m_setFieldCache.put(ir, allFieldsSetToSave);
      }
      reachedMaxDepth[0] |= oriReachedMaxDepth;
    }
    return allFieldsSet;
  }
  
    // list of fields set and the corresponding set depths
  private List<Object[]> getAllFieldsSetRec(IR ir, int curDepth, int maxDepth, boolean inclFieldType, boolean[] reachedMaxDepth) {

    List<Object[]> allFieldsSet = new ArrayList<Object[]>();

    Hashtable<Integer, FieldReference> fieldValueMap = new Hashtable<Integer, FieldReference>();
    HashSet<String> fieldNameSet = new HashSet<String>();
    
    // <init> can set all declared fields to default values
    if (ir.getMethod().getName().toString().equals("<init>")) {
      defaultFieldSetByInit(ir, curDepth, inclFieldType, fieldNameSet, allFieldsSet);
    }
    
    // examine each instruction
    SSAInstruction[] insts = ir.getInstructions();
    for (int i = 0; i < insts.length; i++) {
      if (insts[i] instanceof SSAPutInstruction) {
        SSAPutInstruction putfieldInst = (SSAPutInstruction) insts[i];
        
        String fieldType     = Utils.getClassTypeJavaStr(putfieldInst.getDeclaredFieldType().getName().toString(), false);
        String declClassName = Utils.getClassTypeJavaStr(putfieldInst.getDeclaredField().getDeclaringClass().getName().toString(), false);
        String declFieldName = putfieldInst.getDeclaredField().getName().toString();
        
        Class<?> fieldDeclClass = Utils.getClosestFieldDeclClass(declClassName, declFieldName);
        String fullFieldString = (inclFieldType ? (fieldType + " ") : "") + 
            (fieldDeclClass == null ? declClassName : fieldDeclClass.getName()) + "." + declFieldName;
        
        if (!fieldNameSet.contains(fullFieldString)) {
          allFieldsSet.add(new Object[] {fullFieldString, curDepth});
          fieldNameSet.add(fullFieldString);
        }
      }
      else if (insts[i] instanceof SSAGetInstruction) {
        SSAGetInstruction getInst = (SSAGetInstruction) insts[i];
        if (getInst.getDeclaredFieldType().isArrayType()) {
          fieldValueMap.put(getInst.getDef(), getInst.getDeclaredField());
        }
      }
      else if (insts[i] instanceof SSAArrayStoreInstruction) {
        SSAArrayStoreInstruction arrayStoreInst = (SSAArrayStoreInstruction) insts[i];
        
        int arrayRef = arrayStoreInst.getArrayRef();
        FieldReference fieldRef = fieldValueMap.get(arrayRef);
        if (fieldRef != null) {
          String fieldType     = Utils.getClassTypeJavaStr(fieldRef.getFieldType().getName().toString(), false);
          String declClassName = Utils.getClassTypeJavaStr(fieldRef.getDeclaringClass().getName().toString(), false);
          String declFieldName = fieldRef.getName().toString();
          
          Class<?> fieldDeclClass = Utils.getClosestFieldDeclClass(declClassName, declFieldName);
          String fullFieldString = (inclFieldType ? (fieldType + " ") : "") + 
              (fieldDeclClass == null ? declClassName : fieldDeclClass.getName()) + "." + declFieldName;
          
          if (!fieldNameSet.contains(fullFieldString)) {
            allFieldsSet.add(new Object[] {fullFieldString, curDepth});
            fieldNameSet.add(fullFieldString);
          }
        }
      }
      else if (insts[i] instanceof SSANewInstruction) {
        SSANewInstruction newInst = (SSANewInstruction) insts[i];
        if (newInst.getConcreteType().isArrayType()) {
          String newClassName = Utils.getClassTypeJavaStr(newInst.getConcreteType().getName().toString(), false);
          
          String fullFieldString = (inclFieldType ? "I " : "") + newClassName + ".length";
          if (!fieldNameSet.contains(fullFieldString)) {
            allFieldsSet.add(new Object[] {fullFieldString, curDepth});
            fieldNameSet.add(fullFieldString);
          }
        }
//        else if (newInst.getConcreteType().isClassType()) {
//          IClass newClass = m_walaAnalyzer.getClassHierarchy().lookupClass(newInst.getConcreteType());
//          String newClassName = Utils.getClassTypeJavaStr(newInst.getConcreteType().getName().toString(), false);
//          if (newClass != null) {
//            Collection<IField> fields = newClass.getAllInstanceFields();
//            for (IField field : fields) {
//              String fieldType = Utils.getClassTypeJavaStr(field.getFieldTypeReference().getName().toString(), false);
//              String fieldName = field.getName().toString();
//              
//              String fullFieldString = (inclFieldType ? (fieldType + " ") : "") + newClassName + "." + fieldName;
//              if (!fieldNameSet.contains(fullFieldString)) {
//                allFieldsSet.add(new Object[] {fullFieldString, curDepth});
//                fieldNameSet.add(fullFieldString);
//              }
//            }
//          }
//        }
      }
      else if (insts[i] instanceof SSAInvokeInstruction && curDepth < maxDepth) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) insts[i];
        String methodSig = invokeInst.getDeclaredTarget().getSignature();
        
        // use pseudo implementations when necessary
        if (m_usePseudo) {
          String pseudoImpl = PseudoImplMap.findPseudoImpl(methodSig);
          if (pseudoImpl != null) {
            methodSig = pseudoImpl;
          }
        }

        List<IR> irs = new ArrayList<IR>();
        IR ir2 = Jar2IR.getIR(m_walaAnalyzer, methodSig);
        if (ir2 != null) {
          irs.add(ir2);
        }
        else {
          String superClass = invokeInst.getDeclaredTarget().getDeclaringClass().getName().toString();
          IR[] subclassIRs = SubClassHack.findFreqSubclassIRs(m_walaAnalyzer, superClass, methodSig);
          if (subclassIRs != null) {
            irs.addAll(Arrays.asList(subclassIRs));
          }
        }
        
        for (IR ir3 : irs) {
          // recursively call getAllFieldsSet
          List<Object[]> allFieldsSet2 = getAllFieldsSet(ir3, curDepth + 1, maxDepth, inclFieldType, reachedMaxDepth);
          for (Object[] field : allFieldsSet2) {
            if (!fieldNameSet.contains(field[0])) {
              allFieldsSet.add(field);
              fieldNameSet.add((String) field[0]);
            }
          }
        }
      }
      else if (insts[i] instanceof SSAInvokeInstruction && curDepth >= maxDepth) {
        reachedMaxDepth[0] = true;
      }
      else {
        continue;
      }
    }
    return allFieldsSet;
  }
  
  private void defaultFieldSetByInit(IR ir, int curDepth, boolean inclFieldType, 
      HashSet<String> fieldNameSet, List<Object[]> allFieldsSet) {
    
    // <init> can set all declared fields to default values
    if (ir.getMethod().getName().toString().equals("<init>")) {
      IClass declClass = ir.getMethod().getDeclaringClass();
      String declClassName = Utils.getClassTypeJavaStr(declClass.getName().toString(), false);
      Collection<IField> fields = declClass.getDeclaredInstanceFields(); /* this class only */
      for (IField field : fields) {
        String fieldType = Utils.getClassTypeJavaStr(field.getFieldTypeReference().getName().toString(), false);
        String fieldName = field.getName().toString();
      
        String fullFieldString = (inclFieldType ? (fieldType + " ") : "") + declClassName + "." + fieldName;
        if (!fieldNameSet.contains(fullFieldString)) {
          allFieldsSet.add(new Object[] {fullFieldString, curDepth});
          fieldNameSet.add(fullFieldString);
        }
      }
    }
  }
  
  public WalaAnalyzer getWalaAnalyzer() {
    return m_walaAnalyzer;
  }
  
  public static void main(String[] args) throws Exception {
    SetFieldAnalyzer setFieldAnalyzer = new SetFieldAnalyzer("./test_programs/test_program.jar", null, 1);
    Hashtable<String, List<Member>> setFields = setFieldAnalyzer.findAllSetFields("test_program", 5, true, true);
    
    Enumeration<String> fields = setFields.keys();
    while (fields.hasMoreElements()) {
      String field = (String) fields.nextElement();
      List<Member> methodList = setFields.get(field);
      
      System.out.println("Member Field: " + field);
      for (Member method : methodList) {
        if (method instanceof Method) {
          System.out.println(((Method) method).toGenericString());
        }
        else {
          System.out.println(((Constructor<?>) method).toGenericString());
        }
      }
      System.out.println("-----------------------");
    }
  }
  
  private final int          m_accessibility; // 0 for public, 1 for non-private
  private final boolean      m_usePseudo;
  private final WalaAnalyzer m_walaAnalyzer;
  private final LRUCache     m_setFieldCache;
}
