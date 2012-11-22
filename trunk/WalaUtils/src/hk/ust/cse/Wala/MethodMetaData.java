package hk.ust.cse.Wala;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.cfg.ShrikeCFG;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;

public class MethodMetaData {
  public MethodMetaData(IR ir) {
    // save ir
    m_ir = ir;

    // init method parameter map and list
    m_paramMap  = new Hashtable<String, String>();
    m_paramList = new ArrayList<String>();
    int num = ir.getNumberOfParameters();
    for (int i = 0; i < num; i++) {
      int valnum = ir.getParameter(i);
      
      String valStr = "v" + valnum;
      String param = "(" + ir.getParameterType(i).getName() + ")" + getParamName(valnum);
      m_paramMap.put(valStr, param);

      // the first one is "this" for non-static methods
      m_paramList.add(param);
    }
  }

  public String getName() {
    return m_ir.getMethod().getName().toString();
  }

  public String getDeclaringClass(boolean includePkg) {
    if (includePkg) {
      return m_ir.getMethod().getDeclaringClass().getName().toString();
    }
    else {
      return m_ir.getMethod().getDeclaringClass().getName().getClassName().toString();
    }
  }

  public String getParamStr(String val) {
    return m_paramMap.get(val);
  }

  public Hashtable<String, String> getParamMap() {
    return m_paramMap;
  }
  
  public List<String> getParamList() {
    return m_paramList;
  }

  public String getParamName(int valnum) {
    String[] names = m_ir.getLocalNames(0, valnum);
    return (names != null && names.length > 0) ? names[0] : ("paramUnknownName_v" + valnum);
  }

  public String[] getLocalNames(int valnum) {
    return m_ir.getLocalNames(0, valnum);
  }

  // what index do exactly?
  public String[] getLocalNames(int index, int valnum) {
    return m_ir.getLocalNames(index, valnum);
  }

  public IR getIR() {
    return m_ir;
  }

  public SSACFG getcfg() {
    return m_ir.getControlFlowGraph();
  }

  public SymbolTable getSymbolTable() {
    return m_ir.getSymbolTable();
  }
  
  public int getInstructionIndex(SSAInstruction instruction) {
    SSAInstruction[] instructions = m_ir.getControlFlowGraph().getInstructions();
    
    int index = -1;
    for (int i = 0; i < instructions.length; i++) {
      if (instruction == instructions[i]/* use '==' instead of equals() */) {
        // get line number by instruction index
        index = i;
        break;
      }
    }
    return index;
  }
  
  public int getLineNumber(int instIndex) {
    try {
      SSACFG cfg = m_ir.getControlFlowGraph();
      int bcIndex = ((IBytecodeMethod) cfg.getMethod()).getBytecodeIndex(instIndex);
      return ((IBytecodeMethod) cfg.getMethod()).getLineNumber(bcIndex);
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      return -1;
    }
  }
  
  public int getLineNumber(SSAInstruction instruction) {
    int instIndex = getInstructionIndex(instruction);
    
    int lineNo = -1;
    if (instIndex >= 0) {
      lineNo = getLineNumber(instIndex);
    }
    return lineNo;
  }
  
  public int getLineNumber(ISSABasicBlock bb) {
    int instIndex = bb.getLastInstructionIndex();
    
    int lineNo = -1;
    if (instIndex >= 0) {
      lineNo = getLineNumber(instIndex);
    }
    return lineNo;
  }

  public int getFirstInstructionIndexForLine(int lineNo) {
    int index = -1;
    
    SSAInstruction[] instructions = m_ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      if (instructions[i] != null) {
        int instLineNo = getLineNumber(i);
        if (instLineNo == lineNo) {
          index = i;
          break;
        }
        else if (instLineNo > lineNo) {
          break;
        }
      }
    }
    return index;
  }
  
  public SSAInstruction getFirstInstructionForLine(int lineNo) {
    SSAInstruction instForLine = null;
    
    SSAInstruction[] instructions = m_ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      if (instructions[i] != null) {
        int instLineNo = getLineNumber(i);
        if (instLineNo == lineNo) {
          instForLine = instructions[i];
          break;
        }
        else if (instLineNo > lineNo) {
          break;
        }
      }
    }
    return instForLine;
  }
  
  public int getLastInstructionIndexForLine(int lineNo) {
    int index = -1;
    
    SSAInstruction[] instructions = m_ir.getInstructions();
    for (int i = instructions.length - 1; i >= 0; i--) {
      if (instructions[i] != null) {
        int instLineNo = getLineNumber(i);
        if (instLineNo == lineNo) {
          index = i;
          break;
        }
      }
    }
    return index;
  }
  
  public SSAInstruction getLastInstructionForLine(int lineNo) {
    SSAInstruction instForLine = null;
    
    SSAInstruction[] instructions = m_ir.getInstructions();
    for (int i = instructions.length - 1; i >= 0; i--) {
      if (instructions[i] != null) {
        int instLineNo = getLineNumber(i);
        if (instLineNo == lineNo) {
          instForLine = instructions[i];
          break;
        }
      }
    }
    return instForLine;
  }
  
  public ISSABasicBlock getFirstBasicBlockForLine(int lineNo) {
    ISSABasicBlock basicBlock = null;
    
    SSAInstruction inst = getFirstInstructionForLine(lineNo);
    if (inst != null) {
      basicBlock = m_ir.getBasicBlockForInstruction(inst);
    }
    return basicBlock;
  }
  
  public ISSABasicBlock getLastBasicBlockForLine(int lineNo) {
    ISSABasicBlock basicBlock = null;
    
    SSAInstruction inst = getLastInstructionForLine(lineNo);
    if (inst != null) {
      basicBlock = m_ir.getBasicBlockForInstruction(inst);
    }
    return basicBlock;
  }

  public String getMethodSignature() {
    return m_ir.getMethod().getSignature();
  }

  public boolean isStatic() {
    return m_ir.getMethod().isStatic();
  }
  
  public TypeReference getExceptionType(ISSABasicBlock currentBB, ISSABasicBlock catchBB) {
    TypeReference exceptionType = null;
    List<ISSABasicBlock> allCatchBB = 
      m_ir.getControlFlowGraph().getExceptionalSuccessors(currentBB);
    if (allCatchBB.contains(catchBB)) {
      exceptionType = getExceptionType(catchBB);
    }
    
    // the return value is null if catchBB is the exit block, which 
    // means we cannot find an explicit catch block in the current method.
    return exceptionType;
  }
  
  public TypeReference getExceptionType(ISSABasicBlock catchBB) {
    TypeReference exceptionType = null;
    Iterator<TypeReference> allCaughtExcepTypes = catchBB.getCaughtExceptionTypes();
    while (allCaughtExcepTypes.hasNext()) {
      exceptionType = (TypeReference) allCaughtExcepTypes.next();
      break;  // can there be more than one?
    }
    return exceptionType;
  }

  public ShrikeCFG getShrikeCFG() {
    SSACFG cfg = getcfg();

    ShrikeCFG shrikeCFG = null;
    try {
      Field fieldDelegate = cfg.getClass().getDeclaredField("delegate");
      boolean bAccessible = fieldDelegate.isAccessible();
      fieldDelegate.setAccessible(true);
      Object delegate = fieldDelegate.get(cfg);
      shrikeCFG = (delegate instanceof ShrikeCFG) ? (ShrikeCFG)delegate : null;
      fieldDelegate.setAccessible(bAccessible);
    } catch (Exception e) {
      //
    }

    return shrikeCFG;
  }

  private final IR                        m_ir;
  private final List<String>              m_paramList;
  private final Hashtable<String, String> m_paramMap;
}
