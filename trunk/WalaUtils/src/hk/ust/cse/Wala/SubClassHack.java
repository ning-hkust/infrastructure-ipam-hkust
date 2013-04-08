package hk.ust.cse.Wala;

import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.IR;

public class SubClassHack {
  
  public static List<String> useFreqSubclasses(String superClass) {
    if (s_frequentList == null) {
      s_frequentList = new Hashtable<String,  List<String>>();
      List<String> list1 = new ArrayList<String>();
      list1.add("Ljava/util/HashMap");
      list1.add("Ljava/util/Hashtable");
      s_frequentList.put("java.util.Map", list1);
      List<String> list2 = new ArrayList<String>();
      list2.add("Ljava/util/HashSet");
      s_frequentList.put("java.util.Set", list2);
      List<String> list3 = new ArrayList<String>();
      list3.add("Ljava/util/AbstractList$Itr");
      s_frequentList.put("java.util.Iterator", list3);
      List<String> list4 = new ArrayList<String>();
      list4.add("Ljava/util/AbstractList$ListItr");
      s_frequentList.put("java.util.ListIterator", list4);
      List<String> list5 = new ArrayList<String>();
      list5.add("Ljava/util/ArrayList");
      list5.add("Ljava/util/Vector");
      s_frequentList.put("java.util.AbstractList", list5);
      List<String> list6 = new ArrayList<String>();
      list6.add("Ljava/util/ArrayList");
      list6.add("Ljava/util/Vector");
      s_frequentList.put("java.util.List", list6);
      List<String> list7 = new ArrayList<String>();
      list7.add("Ljava/util/ArrayList");
      list7.add("Ljava/util/Vector");
      s_frequentList.put("java.util.Collection", list7);
      List<String> list8 = new ArrayList<String>();
      list8.add("Ljava/io/StringWriter");
      list8.add("Ljava/io/CharArrayWriter");
      s_frequentList.put("java.io.Writer", list8);
      List<String> list9 = new ArrayList<String>();
      list9.add("Ljava/io/ByteArrayOutputStream");
      s_frequentList.put("java.io.OutputStream", list9);
      List<String> list10 = new ArrayList<String>();
      list10.add("Ljava/io/ByteArrayInputStream");
      s_frequentList.put("java.io.InputStream", list10);
      List<String> list11 = new ArrayList<String>();
      list11.add("Ljava/util/AbstractList");
      s_frequentList.put("java.util.AbstractCollection", list11);
      List<String> list12 = new ArrayList<String>();
      list12.add("Ljava/util/HashSet");
      s_frequentList.put("java.util.AbstractSet", list12);
    }
    
    superClass = Utils.getClassTypeJavaStr(superClass);
    return s_frequentList.get(superClass);
  }
  
  public static IR[] tryUseFreqSubclassIRs(WalaAnalyzer walaAnalyzer, String superClass, String methodSig, IR[] targetIRs) {
    IR[] selectedIRs = targetIRs;
    
    // if superClass has frequent sub-classes, use them directly
    String methodSelector = methodSig.substring(methodSig.lastIndexOf('.'));
    List<String> freqSubclasses = SubClassHack.useFreqSubclasses(superClass);
    if (freqSubclasses != null) {
      // get the sub-classes
      Hashtable<String, IR> subclassIRMap = new Hashtable<String, IR>();
      for (IR targetIR : targetIRs) {
        String subclass = targetIR.getMethod().getDeclaringClass().getName().toString();
        subclassIRMap.put(subclass, targetIR);
      }
      
      // retain the IRs
      List<IR> retainedIRs = new ArrayList<IR>();
      for (String subclass : freqSubclasses) {
        String newMethodSig = Utils.getClassTypeJavaStr(subclass, false) + methodSelector;
        IR ir = Jar2IR.getIR(walaAnalyzer, newMethodSig);
        if (ir != null) {
          IR ir2 = subclassIRMap.get(ir.getMethod().getDeclaringClass().getName().toString());
          if (ir == ir2) {
            retainedIRs.add(ir);
          }
        }
      }
      
      selectedIRs = retainedIRs.size() > 0 ? retainedIRs.toArray(new IR[retainedIRs.size()]) : targetIRs;
    }

    return selectedIRs;
  }
  

  public static IR[] findFreqSubclassIRs(WalaAnalyzer walaAnalyzer, String superClass, String methodSig) {
    IR[] freqSubclassIRs = null;
    
    // if superClass has frequent sub-classes, use them directly
    String methodSelector = methodSig.substring(methodSig.lastIndexOf('.'));
    List<String> freqSubclasses = SubClassHack.useFreqSubclasses(superClass);
    if (freqSubclasses != null) {
      // get the IRs
      HashSet<IR> irs = new HashSet<IR>();
      for (String subclass : freqSubclasses) {
        String newMethodSig = Utils.getClassTypeJavaStr(subclass, false) + methodSelector;
        IR ir = Jar2IR.getIR(walaAnalyzer, newMethodSig);
        if (ir != null) {
          irs.add(ir);
        }
      }
      freqSubclassIRs = irs.size() > 0 ? irs.toArray(new IR[irs.size()]) : null;
    }

    return freqSubclassIRs;
  }

  private static Hashtable<String, List<String>> s_frequentList;
}
