package randoop;

import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;

import randoop.util.CollectionsExt;
import randoop.util.MethodReflectionCode;
import randoop.util.PrimitiveTypes;
import randoop.util.Reflection;
import randoop.util.ReflectionExecutor;
import utilMDE.Pair;

/**
 * Represents a method call.
 * 
 * The "R" stands for "Randoop", to underline the distinction from
 * java.lang.reflect.Method.
 */
public final class RMethod implements StatementKind, Serializable {

	private static final long serialVersionUID = -7616184807726929835L;

	/** ID for parsing purposes (see StatementKinds.parse method) */
	public static final String ID = "method";

	// State variable.
	private final Method method;

	// Cached values (for improved performance). Their values
	// are computed upon the first invocation of the respective
	// getter method.
	private List<Class<?>> inputTypesCached;

	private ArrayList<Pair<Object, HashSet<Variable>>> inputTypesCenterValue;

	private Class<?> outputTypeCached;

	private boolean hashCodeComputed = false;

	private int hashCodeCached = 0;

	private boolean isVoidComputed = false;

	private boolean isVoidCached = false;

	private boolean isStaticComputed = false;

	private boolean isStaticCached = false;
	

	  // A set of bits, where there is one bit associated with each argument.
	  // null flags are used during generation, to determine null has been used for the argument
	  public BitSet nullFlags;  

	  public boolean isNullUsed(int i) {
	    return nullFlags.get(i);
	  }

	  public void setAllActiveFlags() {
	    nullFlags.set(0, this.getInputTypes().size());
	  }

	  public void clearAllActiveFlags() {
	    nullFlags.clear(0, this.getInputTypes().size());
	  }

	  public void setNullFlag(int i) {
	    nullFlags.set(i);
	  }

	  public void clearNullFlag(int i) {
	    nullFlags.clear(i);
	  }  	

	private Object writeReplace() throws ObjectStreamException {
		return new SerializableRMethod(method);
	}

	/**
	 * Returns Method object represented by this MethodCallInfo
	 */
	public Method getMethod() {
		return this.method;
	}

	/*
	 * Creates MethodCallInfo from specified method by generating its input and
	 * output constraints.
	 */
	RMethod(Method method) {
		if (method == null)
			throw new IllegalArgumentException("method should not be null.");

		this.method = method;
		// TODO move this earlier in the process: check first that all
		// methods to be used can be made accessible.
		// XXX this should not be here but I get infinite loop when comment out
		this.method.setAccessible(true);

	    this.nullFlags = new BitSet(this.getInputTypes().size());
	    clearAllActiveFlags();		
	}

	/**
	 * Returns the statement corresponding to the given constructor.
	 */
	public static RMethod getRMethod(Method method) {
		return new RMethod(method);
	}

	@Override
	public String toString() {
		return toParseableString();
	}

	public void appendCode(Variable newVar, List<Variable> inputVars,
			StringBuilder b) {
		if (!isVoid()) {
			b.append(Reflection.getCompilableName(this.method.getReturnType()));
			String cast = "";
			b.append(" " + newVar.getName() + " = " + cast);
		}
		String receiverString = isStatic() ? null : inputVars.get(0).getName();
		appendReceiverOrClassForStatics(receiverString, b);

		b.append(".");
		b.append(getTypeArguments());
		b.append(this.method.getName() + "(");

		int startIndex = (isStatic() ? 0 : 1);
		for (int i = startIndex; i < inputVars.size(); i++) {
			if (i > startIndex)
				b.append(", ");

			// We cast whenever the variable and input types are not identical.
			if (!inputVars.get(i).getType().equals(getInputTypes().get(i))) 
				b.append("(" + getInputTypes().get(i).getCanonicalName() + ")");

			b.append(inputVars.get(i).getName());
		}

		b.append(");" + Globals.lineSep);
	}

	// XXX this is a pretty bogus workaround for a bug in javac (type inference
	// fails sometimes)
	// It is bogus because what we produce here may be different from correct
	// infered type.
	private String getTypeArguments() {
		TypeVariable<Method>[] typeParameters = method.getTypeParameters();
		if (typeParameters.length == 0)
			return "";
		StringBuilder b = new StringBuilder();
		Class<?>[] params = new Class[typeParameters.length];
		b.append("<");
		for (int i = 0; i < typeParameters.length; i++) {
			if (i > 0)
				b.append(",");
			Type firstBound = typeParameters[i].getBounds().length == 0 ? Object.class
					: typeParameters[i].getBounds()[0];
			params[i] = getErasure(firstBound);
			b.append(getErasure(firstBound).getCanonicalName());
		}
		b.append(">");
		// if all are object, then don't bother
		if (CollectionsExt.findAll(Arrays.asList(params), Object.class).size() == params.length)
			return "";
		return b.toString();
	}

	private static Class<?> getErasure(Type t) {
		if (t instanceof Class)
			return (Class<?>) t;
		if (t instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) t;
			return getErasure(pt.getRawType());
		}
		if (t instanceof TypeVariable) {
			TypeVariable<?> tv = (TypeVariable<?>) t;
			Type[] bounds = tv.getBounds();
			Type firstBound = bounds.length == 0 ? Object.class : bounds[0];
			return getErasure(firstBound);
		}
		if (t instanceof GenericArrayType)
			throw new UnsupportedOperationException(
					"erasure of arrays not implemented " + t);
		if (t instanceof WildcardType)
			throw new UnsupportedOperationException(
					"erasure of wildcards not implemented " + t);
		throw new IllegalStateException("unexpected type " + t);
	}

	private void appendReceiverOrClassForStatics(String receiverString,
			StringBuilder b) {
		if (isStatic()) {
			String s2 = this.method.getDeclaringClass().getName().replace('$',
					'.');
			// TODO combine this with last if clause
			b.append(s2);
		} else {
			Class<?> expectedType = getInputTypes().get(0);
			String canonicalName = expectedType.getCanonicalName();
			boolean mustCast = canonicalName != null
					&& PrimitiveTypes
							.isBoxedPrimitiveTypeOrString(expectedType)
					&& !expectedType.equals(String.class);
			if (mustCast) {
				// this is a little paranoid but we need to cast primitives in
				// order to get them boxed.
				b.append("((" + canonicalName + ")" + receiverString + ")");
			} else {
				b.append(receiverString);
			}
		}
	}

	@Testable
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RMethod))
			return false;
		if (this == o)
			return true;
		RMethod other = (RMethod) o;
		if (!this.method.equals(other.method))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		if (!hashCodeComputed) {
			hashCodeComputed = true;
			hashCodeCached = this.method.hashCode();
		}
		return hashCodeCached;
	}

	public ExecutionOutcome execute(Object[] statementInput, PrintStream out) {

		assert statementInput.length == getInputTypes().size();

		Object receiver = null;
		int paramsLength = getInputTypes().size();
		int paramsStartIndex = 0;
		if (!isStatic()) {
			receiver = statementInput[0];
			paramsLength--;
			paramsStartIndex = 1;
		}

		Object[] params = new Object[paramsLength];
		for (int i = 0; i < params.length; i++) {
			params[i] = statementInput[i + paramsStartIndex];
		}

		MethodReflectionCode code = new MethodReflectionCode(this.method,
				receiver, params);

		long startTime = System.currentTimeMillis();
		Throwable thrown = ReflectionExecutor.executeReflectionCode(code, out);
		long totalTime = System.currentTimeMillis() - startTime;

		ExecutionOutcome ret;
		if (thrown == null) {
      ret = new NormalExecution(code.getReturnVariable(), totalTime);
		} else {
			ret = new ExceptionalExecution(thrown, totalTime);
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	public void updateCenterValue(Variable newval, Object newobj, int i) {
		if (newobj == null)
			return;
		if (inputTypesCenterValue == null)
			getTypeCenterValues();

		Object cenval = inputTypesCenterValue.get(i).a;
		HashSet<Variable> setUsedVals = inputTypesCenterValue.get(i).b;
		if (setUsedVals == null)
			setUsedVals = new HashSet<Variable>();
		int called = setUsedVals.size();
		setUsedVals.add(newval);
		//if (setUsedVals.size() % 10 == 0)
//			System.out.println("####" + setUsedVals.size());

		if (cenval instanceof Boolean)
			inputTypesCenterValue.set(i, new Pair(newobj, setUsedVals));
		else if (cenval instanceof Integer) {
			Integer nn = ((Integer) (cenval) * called + (Integer) newobj)
					/ (called + 1);
			inputTypesCenterValue.set(i, new Pair(nn, setUsedVals));
		} else if (cenval instanceof Short) {
			Short nn = new Short(
					(short) (((Short) (cenval) * called + (Short) newobj) / (called + 1)));
			inputTypesCenterValue.set(i, new Pair(nn, setUsedVals));
		} else if (cenval instanceof Long) {
			Long nn = ((Long) (cenval) * called + (Long) newobj) / (called + 1);
			inputTypesCenterValue.set(i, new Pair(nn, setUsedVals));
		} else if (cenval instanceof Float) {
			Float nn = ((Float) (cenval) * called + (Float) newobj)
					/ (called + 1);
			inputTypesCenterValue.set(i, new Pair(nn, setUsedVals));
		} else if (cenval instanceof Double) {
			Double nn = ((Double) (cenval) * called + (Double) newobj)
					/ (called + 1);
			inputTypesCenterValue.set(i, new Pair(nn, setUsedVals));
		} else if (cenval instanceof Byte) {
			int nn = (int) ((Byte) cenval * called + (Byte) newobj)
					/ (called + 1);
			byte bb = (byte) nn;
			inputTypesCenterValue.set(i, new Pair(bb, setUsedVals));
		} else if (cenval instanceof Character) {
			int nn = (int) ((Character) cenval * called + (Character) newobj)
					/ (called + 1);
			char bb = (char) nn;
			inputTypesCenterValue.set(i, new Pair(bb, setUsedVals));
		} else if (cenval instanceof String) {
			inputTypesCenterValue.set(i, new Pair(null, setUsedVals));
		} else if (newval.sequence.getStatementKind(newval.index)
				.getOutputType().isArray()) {
			inputTypesCenterValue.set(i, new Pair(null, setUsedVals));
		}

	}

	// JJ
	public boolean isUsedValue(Variable var, int argIndex) {
		HashSet<Variable> setUsedVals = inputTypesCenterValue.get(argIndex).b;
		if (setUsedVals == null)
			return false;
		if (setUsedVals.contains(var))
			return true;

		return false;
	}

	// JJ
	@SuppressWarnings("unchecked")
	public List<Pair<Object, HashSet<Variable>>> getTypeCenterValues() {
		if (inputTypesCenterValue == null) {
			getInputTypes();
			inputTypesCenterValue = new ArrayList<Pair<Object, HashSet<Variable>>>(
					inputTypesCached.size());
			for (int i = 0; i < inputTypesCached.size(); i++) {
				Object o = inputTypesCached.get(i);
				if (PrimitiveTypes
						.isBoxedOrPrimitiveOrStringType(inputTypesCached.get(i))
						|| inputTypesCached.get(i).isPrimitive()) {
					Object obj = null;
					if (o instanceof Boolean || o.equals(Boolean.class)
							|| o.toString().equals("boolean"))
						obj = new Boolean(true);

					else if (o instanceof Integer || o.equals(Integer.class)
							|| o.toString().equals("int"))
						obj = new Integer(0);

					else if (o instanceof Short || o.equals(Short.class)
							|| o.toString().equals("short"))
						obj = new Short((short) 0);

					else if (o instanceof Long || o.equals(Long.class)
							|| o.toString().equals("long"))
						obj = new Long(0);

					else if (o instanceof Float || o.equals(Float.class)
							|| o.toString().equals("float"))
						obj = new Float(0);

					else if (o instanceof Double || o.equals(Double.class)
							|| o.toString().equals("double"))
						obj = new Double(0);

					else if (o instanceof Byte || o.equals(Byte.class)
							|| o.toString().equals("byte"))
						obj = new Byte((byte) 0);

					else if (o instanceof Character
							|| o.equals(Character.class)
							|| o.toString().equals("char"))
						obj = new Character(' ');

					else if (o instanceof String || o.equals(String.class)
							|| o.toString().equals("String"))
						obj = new String(" ");

					if (obj != null) {
						Pair pair = new Pair(obj, null);
						inputTypesCenterValue.add(pair);
					} else
						inputTypesCenterValue.add(new Pair(null, null));
				} else
					inputTypesCenterValue.add(new Pair(null, null));
			}
		}
		return inputTypesCenterValue;
	}

	/**
	 * Extracts the input constraints for this MethodCallInfo
	 * 
	 * @return list of input constraints
	 */
	public List<Class<?>> getInputTypes() {
		if (inputTypesCached == null) {
			Class<?>[] methodParameterTypes = method.getParameterTypes();
			inputTypesCached = new ArrayList<Class<?>>(
					methodParameterTypes.length + (isStatic() ? 0 : 1));
			if (!isStatic())
				inputTypesCached.add(method.getDeclaringClass());
			for (int i = 0; i < methodParameterTypes.length; i++) {
				inputTypesCached.add(methodParameterTypes[i]);
			}
		}
		return inputTypesCached;
	}

	/**
	 * Returns constraint to represent new reference to this statement. Returns
	 * null if method represented by this MethodCallInfo is a void method,
	 * returns the return value otherwise.
	 */
	public Class<?> getOutputType() {
		if (outputTypeCached == null) {
			outputTypeCached = method.getReturnType();
		}
		return outputTypeCached;
	}

	private boolean isVoid() {
		if (!isVoidComputed) {
			isVoidComputed = true;
			isVoidCached = void.class.equals(this.method.getReturnType());
		}
		return isVoidCached;
	}

	/**
	 * Returns true if method represented by this MethodCallInfo is a static
	 * method.
	 */
	public boolean isStatic() {
		if (!isStaticComputed) {
			isStaticComputed = true;
			isStaticCached = Modifier.isStatic(this.method.getModifiers());
		}
		return this.isStaticCached;
	}

	/**
	 * A string representing this method. The string is of the form:
	 * 
	 * METHOD
	 * 
	 * Where METHOD is a string representation of the method signature.
	 * Examples:
	 * 
	 * java.util.ArrayList.get(int)
	 * java.util.ArrayList.add(int,java.lang.Object)
	 */
	public String toParseableString() {
		return Reflection.getSignature(method);
	}

	public static StatementKind parse(String s) {
		return RMethod.getRMethod(Reflection.getMethodForSignature(s));
	}
}
