package hk.ust.cse.Prevision.VirtualMachine;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;

import com.ibm.wala.ssa.ISSABasicBlock;

public class Instance {
  
  public enum INSTANCE_OP {ADD, AND, SUB, MUL, DIV, OR, REM, XOR, SHL, SHR, USHR}
  
  public Instance(String initCallSites, ISSABasicBlock createBlock) { // initial unknown instance
    m_createTime    = System.nanoTime();
    m_setValueTime  = Long.MIN_VALUE;
    m_createBlock   = createBlock;
    m_setValueBlock = null;
    m_setValueBy    = null;
    m_initCallSites = initCallSites;
    m_boundValues   = new HashSet<Instance>(1);
    m_fields        = new Hashtable<String, Reference>(1);
  }
  
  /**
   * @param value: if it is a constant: #! is num, ## is string, #? is unknown, also null/true/false
   */
  public Instance(String value, String type, ISSABasicBlock createBlock) { // initially known instance, immediately bounded
    m_value         = value;
    m_type          = type;
    
    m_createTime    = System.nanoTime();
    m_setValueTime  = m_createTime; // value is set when create
    m_createBlock   = createBlock;
    m_setValueBlock = createBlock;  // value is set when create
    m_setValueBy    = null;
    m_initCallSites = null;
    m_boundValues   = null;         // this is already bounded
    m_fields        = new Hashtable<String, Reference>(1);
  }
  
  public Instance(Instance left, INSTANCE_OP op, Instance right, ISSABasicBlock createBlock) {
    this(null, null, createBlock);
    
    m_left  = left;
    m_right = right;
    m_op    = op;
  }
  
  public void setValue(Instance instance) throws Exception {
    if (instance.isAtomic()) {
      setValue(instance.getValue(), instance.getType(), instance.getSetValueBlock());
    }
    else {
      setValue(instance.getLeft(), instance.getOp(), instance.getRight(), instance.getSetValueBlock());
    }
    m_setValueBy = instance;
  }
  
  // should only be called by setEquivalentInstances
  public void setValueInclSetTime(Instance instance, long setTime) throws Exception {
    setValue(instance);
    m_setValueTime = setTime;
  }
  
  public boolean storeValue(Instance instance) throws Exception {
    boolean added = false;
    if (m_boundValues != null) {
      int oriSize = m_boundValues.size();
      m_boundValues.add(instance);
      added = m_boundValues.size() > oriSize;
    }
    else {
      throw new Exception("Cannot store a bounded value to a bounded value!");
    }
    return added;
  }
  
  private void setValue(String value, String type, ISSABasicBlock setValueBlock) throws Exception {
    if (!isBounded()) {
      m_value         = value;
      m_type          = type;
      m_boundValues   = null;              // not useful anymore after bound              
      m_lastRef       = null;              // not useful anymore after bound
      m_setValueTime  = System.nanoTime(); // time stamp for setting the value
      m_setValueBlock = setValueBlock;
    }
    else {
      throw new Exception("An instance can only be bounded once!");
    }
  }
  
  private void setValue(Instance left, INSTANCE_OP op, Instance right, ISSABasicBlock setValueBlock) throws Exception {
    if (!isBounded()) {
      m_left          = left;
      m_right         = right;
      m_op            = op;
      m_boundValues   = null;              // not useful anymore after bound
      m_lastRef       = null;              // not useful anymore after bound
      m_setValueTime  = System.nanoTime(); // time stamp for setting the value
      m_setValueBlock = setValueBlock;
    }
    else {
      throw new Exception("An object instance can only be concretize once!");
    }
  }
  
  public void setLastReference(Reference lastRef) {
    m_lastRef = lastRef;
  }
  
  public void setField(String fieldName, String fieldType, String callSites, Collection<Instance> instances) {
    Reference reference = m_fields.get(fieldName);
    if (reference == null) {
      reference = new Reference(fieldName, fieldType, callSites, instances, this);
      m_fields.put(fieldName, reference);
    }
    else {
      try {
        reference.assignInstance(instances);
      } catch (Exception e) {e.printStackTrace();}
    }
  }

  public boolean isBounded() {
    return m_value != null || m_left != null;
  }
  
  public boolean isAtomic() {
    return m_value != null;
  }
  
  public boolean isConstant() {
    return m_value != null && (m_value.equals("null") || m_value.startsWith("#") || 
                               m_value.equals("true") || m_value.equals("false"));
  }
  
  public String getValue() {
    return m_value;
  }
  
  public String getType() {
    return m_type;
  }
  
  public Reference getLastReference() {
    return m_lastRef;
  }
  
  public HashSet<Instance> getBoundedValues() {
    return m_boundValues;
  }
  
  public String getInitCallSites() {
    return m_initCallSites;
  }
  
  public String getValueWithoutPrefix() {
    if (m_value.startsWith("#")) {
      return m_value.substring(2);
    }
    else {
      return m_value;
    }
  }
  
  public Instance getLeft() {
    return m_left;
  }
  
  public Instance getRight() {
    return m_right;
  }
  
  public INSTANCE_OP getOp() {
    return m_op;
  }
  
  public long getSetValueTime() {
    return m_setValueTime;
  }
  
  public long getLatestSetValueTime() {
    if (isAtomic()) {
      return m_setValueTime;
    }
    else if (isBounded()) {
      long latestLeft  = m_left.getLatestSetValueTime();
      long latestRight = m_right.getLatestSetValueTime();
      return (latestLeft == Long.MIN_VALUE && latestRight == Long.MIN_VALUE) ? m_setValueTime : 
        (latestLeft > latestRight ? latestLeft : latestRight);
    }
    else {
      Instance toppest = getToppestInstance();
      if (toppest.getValue() != null && toppest.getValue().startsWith("##")) {
        return toppest.getLatestSetValueTime();
      }
      return Long.MIN_VALUE;
    }
  }
  
  public Instance getToppestInstance() {
    Instance currentInstance = this;
    while (currentInstance.getLastReference() != null && currentInstance.getLastReference().getDeclaringInstance() != null) {
      currentInstance = currentInstance.getLastReference().getDeclaringInstance();
    }
    return currentInstance;
  }
  
  public long getCreateTime() {
    return m_createTime;
  }
  
  public ISSABasicBlock getSetValueBlock() {
    return m_setValueBlock;
  }
  
  public ISSABasicBlock getCreateBlock() {
    return m_createBlock;
  }
  
  public Instance getSetValueBy() {
    return m_setValueBy;
  }
  
  public Collection<Reference> getFields() {
    return m_fields.values();
  }

  public Reference getField(String fieldName) {
    return m_fields.get(fieldName);
  }
  
  public String toString() {
    StringBuilder ret = new StringBuilder();
    
    if (!isBounded()) {
      ret.append(m_lastRef == null ? "{NotBound}" : m_lastRef.getLongName());
    }
    else if (isAtomic()) {
      ret.append(m_value);
    }
    else {
      ret.append("(");
      ret.append(m_left.toString());
      switch (m_op) {
      case ADD:
        ret.append(" + ");
        break;
      case AND:
        ret.append(" & ");
        break;
      case SUB:
        ret.append(" - ");
        break;
      case MUL:
        ret.append(" * ");
        break;
      case DIV:
        ret.append(" / ");
        break;
      case OR:
        ret.append(" | ");
        break;
      case REM:
        ret.append(" % ");
        break;
      case XOR:
        ret.append(" ^ ");
        break;
      case SHL:
        ret.append(" << ");
        break;
      case SHR:
        ret.append(" >> ");
        break;
      case USHR:
        ret.append(" >> ");
        break;
      default:
        ret.append(" ? "); // unknown op
        break;
      }
      ret.append(m_right.toString());
      ret.append(")");
    }
    return ret.toString();
  }
  
  public Instance deepClone(Hashtable<Object, Object> cloneMap) {
    // if it is already cloned, return the cloned instance
    Instance clone = (Instance) cloneMap.get(this);
    if (clone == null) {
      if (isAtomic()) {
        clone = new Instance(m_value, m_type, m_createBlock);
      }
      else if (isBounded()) {
        clone = new Instance(m_left.deepClone(cloneMap), m_op, m_right.deepClone(cloneMap), m_createBlock);
      }
      else {
        clone = new Instance(m_initCallSites, m_createBlock);
      }

      // save the clone before cloning fields
      cloneMap.put(this, clone);

      // also clone fields
      Enumeration<String> keys = m_fields.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        Reference ref = m_fields.get(key).deepClone(cloneMap, clone);  
        clone.m_fields.put(key, ref);
      }
      
      // also clone the bounded values to this instance if any
      if (m_boundValues != null) {
        for (Instance instance : m_boundValues) {
          // do not need to care about lastRef
          clone.m_boundValues.add(instance.deepClone(cloneMap));
        }
      }

      // clone set value by
      if (m_setValueBy != null) {
        clone.m_setValueBy = m_setValueBy.deepClone(cloneMap);
      }

      // since it is only a clone, we don't use the auto-gen time stamps
      clone.m_createTime    = m_createTime;
      clone.m_setValueTime  = m_setValueTime;
      clone.m_setValueBlock = m_setValueBlock;
      
      // keep lastRef as it is already updated in afterInvocation/Reference.deepClone
      clone.setLastReference(m_lastRef);
    }
    return clone;
  }

  private Instance                           m_left;
  private Instance                           m_right;
  private INSTANCE_OP                       m_op;
  private String                             m_value;
  private String                             m_type;
  private Reference                          m_lastRef;     // the lastRef is only useful when the instance not bounded
  private long                               m_createTime;
  private long                               m_setValueTime;
  private ISSABasicBlock                     m_createBlock;
  private ISSABasicBlock                     m_setValueBlock;
  private Instance                           m_setValueBy;
  private HashSet<Instance>                  m_boundValues; // the boundValues is only useful when the instance not bounded
  private final String                       m_initCallSites;
  private final Hashtable<String, Reference> m_fields;
}
