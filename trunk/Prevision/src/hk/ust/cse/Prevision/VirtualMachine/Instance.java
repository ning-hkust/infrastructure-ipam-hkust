package hk.ust.cse.Prevision.VirtualMachine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;

public class Instance {
  
  public enum INSTANCE_OP {
    ADD(0), AND(1), SUB(2), MUL(3), DIV(4), OR(5), REM(6), XOR(7), SHL(8), SHR(9), USHR(10), DUMMY(11);
    
    INSTANCE_OP(int index) {
      m_index = index;
    }
    
    public int toIndex() {
      return m_index;
    }
    
    public static INSTANCE_OP fromIndex(int index) {
      switch (index) {
      case 0:
        return ADD;
      case 1:
        return AND;
      case 2:
        return SUB;
      case 3:
        return MUL;
      case 4:
        return DIV;
      case 5:
        return OR;
      case 6:
        return REM;
      case 7:
        return XOR;
      case 8:
        return SHL;
      case 9:
        return SHR;
      case 10:
        return USHR;
      case 11:
        return DUMMY;
      default:
        return null;
      }
    }
    
    public String toString() {
      switch (m_index) {
      case 0:
        return "+";
      case 1:
        return "AND";
      case 2:
        return "-";
      case 3:
        return "*";
      case 4:
        return "/";
      case 5:
        return "OR";
      case 6:
        return "%";
      case 7:
        return "^";
      case 8:
        return "<<";
      case 9:
        return ">>";
      case 10:
        return ">>>";
      case 11:
        return "@";
      default:
        return null;
      }
    }
    
    private final int m_index;
  }
  
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
    m_type          = type == null && isStringConstant() ? "Ljava/lang/String" : type;
    
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
  
  public void setType(String typeName) {
    m_type = typeName;
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
  
  public void setField(String fieldName, String fieldType, String callSites, 
      Instance instance, boolean setLastRef, boolean override) {
    Reference reference = m_fields.get(fieldName);
    if (reference == null || override) {
      reference = new Reference(fieldName, fieldType, callSites, instance, this, setLastRef);
      m_fields.put(fieldName, reference);
    }
    else {
      try {
        reference.assignInstance(instance, setLastRef);
      } catch (Exception e) {e.printStackTrace();}
    }
  }
  
  public void setField(String fieldName, String fieldType, String callSites, 
      Collection<Instance> instances, boolean setLastRef, boolean override) {
    Reference reference = m_fields.get(fieldName);
    if (reference == null || override) {
      reference = new Reference(fieldName, fieldType, callSites, instances, this, setLastRef);
      m_fields.put(fieldName, reference);
    }
    else {
      try {
        reference.assignInstance(instances, setLastRef);
      } catch (Exception e) {e.printStackTrace();}
    }
  }

  // #!0 + #!1
  public String computeArithmetic() {
    return computeArithmetic(new Hashtable<String, String>());
  }
  
  // v1.head + #!1
  public String computeArithmetic(Hashtable<String, String> nameValueMap) {
    String computed = null;
    if (isNumberConstant()) {
      computed = m_value;
    }
    else if (isBounded()) {
      try {
        String leftStr  = m_left.computeArithmetic(nameValueMap);
        String rightStr = m_right.computeArithmetic(nameValueMap);
        if (leftStr != null && rightStr != null && leftStr.startsWith("#!") && rightStr.startsWith("#!")) {
          int leftInt  = Integer.parseInt(leftStr.substring(2));
          int rightInt = Integer.parseInt(rightStr.substring(2));
          switch (m_op) {
          case ADD:
            computed = "#!" + String.valueOf(leftInt + rightInt);
            break;
          case AND:
            computed = "#!" + String.valueOf(leftInt & rightInt);
            break;
          case SUB:
            computed = "#!" + String.valueOf(leftInt - rightInt);
            break;
          case MUL:
            computed = "#!" + String.valueOf(leftInt * rightInt);
            break;
          case DIV:
            computed = "#!" + String.valueOf(leftInt / rightInt);
            break;
          case OR:
            computed = "#!" + String.valueOf(leftInt | rightInt);
            break;
          case REM:
            computed = "#!" + String.valueOf(leftInt % rightInt);
            break;
          case XOR:
            computed = "#!" + String.valueOf(leftInt ^ rightInt);
            break;
          case SHL:
            computed = "#!" + String.valueOf(leftInt << rightInt);
            break;
          case SHR:
            computed = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          case USHR:
            computed = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          default:
            break;
          }
        }
      } catch (Exception e) {}
    }
    else if (m_lastRef != null) {
      computed = nameValueMap.get(toString());
    }
    return computed;
  }
  

  // (v1 @ (#!0 + #!1))
  public String computeInnerArithmetic() {
    return computeInnerArithmetic(new Hashtable<String, String>());
  }
  
  // (v1 @ (v1.head + #!1))
  public String computeInnerArithmetic(Hashtable<String, String> nameValueMap) {
    String computed = computeArithmetic();
    if (computed != null) {
      return computed;
    }
    
    if (isBounded()) {
      String leftStr  = m_left.computeInnerArithmetic(nameValueMap);
      String rightStr = m_right.computeInnerArithmetic(nameValueMap);
      computed = "(" + leftStr;
      switch (m_op) {
      case ADD:
        computed += " + ";
        break;
      case AND:
        computed += " & ";
        break;
      case SUB:
        computed += " - ";
        break;
      case MUL:
        computed += " * ";
        break;
      case DIV:
        computed += " / ";
        break;
      case OR:
        computed += " | ";
        break;
      case REM:
        computed += " % ";
        break;
      case XOR:
        computed += " ^ ";
        break;
      case SHL:
        computed += " << ";
        break;
      case SHR:
        computed += " >> ";
        break;
      case USHR:
        computed += " >> ";
        break;
      case DUMMY:
        computed += " @ ";
        break;
      default:
        computed += " ? "; // unknown op
        break;
      }
      computed += rightStr + ")";
    }
    return computed != null ? computed : toString();
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
  
  public boolean isRelationRead() {
    return m_lastRef != null && m_lastRef.getName().startsWith("read_");
  }
  
  public boolean isOneOfDeclInstance(Instance instance) {
    boolean isDeclInstance = false;
    
    if (m_lastRef != null) {
      Instance declInstance = m_lastRef.getDeclaringInstance();
      while (declInstance != null && !isDeclInstance) {
        isDeclInstance = declInstance == instance;
        if (declInstance.getLastReference() != null) {
          declInstance = declInstance.getLastReference().getDeclaringInstance();
        }
        else {
          declInstance = null;
        }
      }
    }

    return isDeclInstance;
  }
  
  public boolean isNullConstant() {
    return m_value != null && m_value.equals("null");
  }
  
  public boolean isNumberConstant() {
    return m_value != null && m_value.startsWith("#!");
  }
  
  public boolean isStringConstant() {
    return m_value != null && m_value.startsWith("##");
  }
  
  public boolean hasDeclaringInstance() {
    return getLastReference() != null && 
           getLastReference().getDeclaringInstance() != null;
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

  public String getLastRefName() {
    return m_lastRef.getName();
  }
  
  public String getLastRefType() {
    return m_lastRef.getType();
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
    HashSet<Instance> prevInstances = new HashSet<Instance>();
    
    Instance currentInstance = this;
    while (currentInstance != null && currentInstance.hasDeclaringInstance()) {
      if (!prevInstances.contains(currentInstance)) {
        prevInstances.add(currentInstance);
        currentInstance = currentInstance.getLastReference().getDeclaringInstance();
      }
      else {
        // this should not happen, but if it in indeed happens due to some bugs, 
        // we need make sure the program does not get into infinite recursion
        currentInstance = null;
      }
    }
    return currentInstance;
  }
  
  public Reference getField(String[] fieldNames) {
    Reference fieldRef = null;
    
    if (fieldNames.length == 1) {
      fieldRef = m_fields.get(fieldNames[0]);
    }
    else if (fieldNames.length > 1) {
      fieldRef = m_fields.get(fieldNames[0]);
      if (fieldRef != null) {
        fieldRef = fieldRef.getInstance().getField(Arrays.copyOfRange(fieldNames, 1, fieldNames.length));
      }
    }
    return fieldRef;
  }
  
  // including the current one
  public String[] getDeclFieldNames() {
    List<String> fieldNames = new ArrayList<String>();
    
    Reference lastRef = m_lastRef;
    while(lastRef != null && lastRef.getDeclaringInstance() != null) {
      fieldNames.add(0, lastRef.getName());
      lastRef = lastRef.getDeclaringInstance().getLastReference();
    }
    return fieldNames.toArray(new String[fieldNames.size()]);
  }

  // including the current one
  public Instance[] getDeclFieldInstances() {
    List<Instance> instances = new ArrayList<Instance>();
    
    Instance current  = this;
    Reference lastRef = m_lastRef;
    while(lastRef != null && lastRef.getDeclaringInstance() != null) {
      instances.add(0, current);
      current = lastRef.getDeclaringInstance();
      lastRef = lastRef.getDeclaringInstance().getLastReference();
    }
    return instances.toArray(new Instance[instances.size()]);
  }
  
  public HashSet<Instance> getRelatedInstances(Hashtable<String, Relation> relationMap, 
      boolean inclDeclInstances, boolean inclArrayRefIndexInDecl, boolean inclReadRelDomains, boolean inclArrayRead) {
    HashSet<Instance> instances = new HashSet<Instance>();
    getRelatedInstances(instances, relationMap, inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
    return instances;
  }
  
  // inclArrayRefIndexInDecl: include v1.list and v1.index in (v1.list @ v1.index).field
  private void getRelatedInstances(HashSet<Instance> instances, Hashtable<String, Relation> relationMap, 
      boolean inclDeclInstances, boolean inclArrayRefIndexInDecl, boolean inclReadRelDomains, boolean inclArrayRead) {
    if (!isBounded()) {
      Instance currentInstance = this;
      while (currentInstance != null) {
        if (!currentInstance.isBounded()) {
          if (currentInstance == this || inclDeclInstances) {
            instances.add(currentInstance);
          }
          if (currentInstance.isRelationRead() && inclReadRelDomains) {
            String relName          = currentInstance.getLastReference().getReadRelName();
            long readTime           = currentInstance.getLastReference().getReadRelTime();
            Relation relation       = relationMap.get(relName);
            Instance[] relInstances = relation.getDomainValues().get(relation.getIndex(readTime));
            
            for (Instance relInstance : relInstances) {
              relInstance.getRelatedInstances(instances, relationMap, 
                  inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
            }
          }
        }
        else if (!currentInstance.isAtomic()) {
          currentInstance.getLeft().getRelatedInstances(instances, relationMap, 
              inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
          currentInstance.getRight().getRelatedInstances(instances, relationMap, 
              inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
        }
        currentInstance = (inclDeclInstances || inclArrayRefIndexInDecl) && currentInstance.getLastReference() != null ? 
            currentInstance.getLastReference().getDeclaringInstance() : null;
      }
    }
    else if (!isAtomic()) {
      getLeft().getRelatedInstances(instances, relationMap, 
          inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
      getRight().getRelatedInstances(instances, relationMap, 
          inclDeclInstances, inclArrayRefIndexInDecl, inclReadRelDomains, inclArrayRead);
      if (inclArrayRead && m_op == INSTANCE_OP.DUMMY) {
        instances.add(this);
      }
    }
  }
  
  public HashSet<Instance> getRelatedTopInstances(Hashtable<String, Relation> relationMap) {
    HashSet<Instance> instances = new HashSet<Instance>();
    getRelatedTopInstances(instances, relationMap);
    return instances;
  }
  
  private void getRelatedTopInstances(HashSet<Instance> instances, Hashtable<String, Relation> relationMap) {
    if (!isBounded()) {
      Instance topInstance = getToppestInstance();
      if (topInstance != null && !topInstance.isBounded()) {
        if (topInstance.isRelationRead()) {
          String relName          = topInstance.getLastReference().getReadRelName();
          long readTime           = topInstance.getLastReference().getReadRelTime();
          Relation relation       = relationMap.get(relName);
          Instance[] relInstances = relation.getDomainValues().get(relation.getIndex(readTime));

          for (Instance relInstance : relInstances) {
            relInstance.getRelatedTopInstances(instances, relationMap);
          }
        }
        else {
          instances.add(topInstance);
        }
      }
      else if (topInstance != null) {
        topInstance.getRelatedTopInstances(instances, relationMap);
      }
    }
    else if (!isAtomic()) {
      getLeft().getRelatedTopInstances(instances, relationMap);
      getRight().getRelatedTopInstances(instances, relationMap);
    }
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
  
  public Hashtable<String, Reference> getFieldSet() {
    return m_fields;
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
      
      // possibly due to bugs
      if (this == m_left || this == m_right || this == m_left.getLeft() || 
          this == m_left.getRight() || this == m_right.getLeft() || this == m_right.getRight()) {
        return "";
      }
      
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
      case DUMMY:
        ret.append(" @ ");
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
  
  public Instance replaceInstances(Hashtable<Instance, Instance> replaceMap) {
    Instance replace = replaceMap.get(this);
    if (replace != null) {
      return replace;
    }
    
    replace = this;
    if (isBounded() && !isAtomic()) {
      Instance left  = m_left.replaceInstances(replaceMap);
      Instance right = m_right.replaceInstances(replaceMap);
      if (left != m_left || right != m_right) {
        replace = new Instance(left, m_op, right, m_createBlock);
      }
    }
    else if (!isBounded() && m_lastRef != null && m_lastRef.getDeclaringInstance() != null) {
      Instance declInstance = m_lastRef.getDeclaringInstance().replaceInstances(replaceMap);
      if (declInstance != m_lastRef.getDeclaringInstance()) {
        replace = new Instance(m_initCallSites, m_createBlock);
        declInstance.setField(m_lastRef.getName(), m_lastRef.getType(), m_lastRef.getCallSites(), replace, true, true);
      }
    }
    
    return replace;
  }
  
  public Instance deepClone(Hashtable<Object, Object> cloneMap) {
    // if it is already cloned, return the cloned instance
    Instance clone = (Instance) cloneMap.get(this);
    if (clone == null) {
      if (isAtomic()) {
        clone = new Instance(m_value, m_type, m_createBlock);
        
        // save the clone before cloning fields
        cloneMap.put(this, clone);
      }
      else if (isBounded()) {
        clone = new Instance(m_initCallSites, m_createBlock);

        // save the clone before cloning fields
        cloneMap.put(this, clone);
        
        try {
          clone.setValue(m_left.deepClone(cloneMap), m_op, m_right.deepClone(cloneMap), m_setValueBlock);
        } catch (Exception e) {}
      }
      else {
        clone = new Instance(m_initCallSites, m_createBlock);
        
        // save the clone before cloning fields
        cloneMap.put(this, clone);
      }

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
      
      // if clone's lastRef is not null, the lastRef reference must have been 
      // cloned during the cloning of the fields, so the value is correct already
      if (clone.getLastReference() == null) {
        // keep lastRef as it is already updated in afterInvocation/Reference.deepClone
        clone.setLastReference(m_lastRef);
      }
    }
    return clone;
  }
  
  // create instance from persistence string
  public static Instance createInstance(int num, int numLeft, int numOp, int numRight, String value, String type, 
      int numLastRef, Hashtable<String, Integer> fieldSet, Hashtable<Integer, Instance> instanceNumMap, 
      Hashtable<Integer, Reference> referenceNumMap) {
    
    Instance instance = instanceNumMap.get(num);
    if (instance == null) {
      instance = new Instance(value, type, null);
      instanceNumMap.put(num, instance);
    }
    
    // assign fields
    // m_left
    Instance left = numLeft < 0 ? null : instanceNumMap.get(numLeft);
    if (numLeft >= 0 && left == null) {
      left = new Instance("", "", null);
      instanceNumMap.put(numLeft, left);
    }
    instance.m_left = left;

    // m_right
    Instance right = numRight < 0 ? null : instanceNumMap.get(numRight);
    if (numRight >= 0 && right == null) {
      right = new Instance("", "", null);
      instanceNumMap.put(numRight, right);
    }
    instance.m_right = right;
    
    // m_op
    Instance.INSTANCE_OP op = numOp < 0 ? null : Instance.INSTANCE_OP.fromIndex(numOp);
    instance.m_op = op;
    
    // m_value, m_type
    instance.m_value = value;
    instance.m_type  = type;
    
    // m_lastRef
    Reference lastRef = numLastRef < 0 ? null : referenceNumMap.get(numLastRef);
    if (numLastRef >= 0 && lastRef == null) {
      lastRef = new Reference("", "", "", (Instance) null, null, false);
      referenceNumMap.put(numLastRef, lastRef);
    }
    instance.m_lastRef = lastRef;
    
    // m_fields
    Hashtable<String, Reference> fields = new Hashtable<String, Reference>();
    Enumeration<String> keys = fieldSet.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      int numField = fieldSet.get(key);
      Reference fieldRef = numField < 0 ? null : referenceNumMap.get(numField);
      if (numField >= 0 && fieldRef == null) {
        fieldRef = new Reference("", "", "", (Instance) null, null, false);
        referenceNumMap.put(numField, fieldRef);
      }
      fields.put(key, fieldRef);
    }
    instance.m_fields = fields;

    return instance;
  }

  private Instance                     m_left;
  private Instance                     m_right;
  private INSTANCE_OP                 m_op;
  private String                       m_value;
  private String                       m_type;
  private Reference                    m_lastRef;     // the lastRef is only useful when the instance not bounded
  private long                         m_createTime;
  private long                         m_setValueTime;
  private ISSABasicBlock               m_createBlock;
  private ISSABasicBlock               m_setValueBlock;
  private Instance                     m_setValueBy;
  private HashSet<Instance>            m_boundValues; // the boundValues is only useful when the instance not bounded
  private String                       m_initCallSites;
  private Hashtable<String, Reference> m_fields;
}
