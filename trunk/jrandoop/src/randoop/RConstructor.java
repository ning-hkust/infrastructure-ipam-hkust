package randoop;

import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import randoop.util.ConstructorReflectionCode;
import randoop.util.Reflection;
import randoop.util.ReflectionExecutor;
import randoop.util.Util;

/**
 * Represents a constructor call. The inputs are parameters to the constructor
 * and outputs include the new object.
 * 
 * The "R" stands for "Randoop", to underline the distinction from
 * java.lang.reflect.Method.
 * 
 */
public final class RConstructor implements StatementKind, Serializable {

	/** ID for parsing purposes (see StatementKinds.parse method) */
	public static final String ID = "cons";

	// State variable.
	private final Constructor<?> constructor;

	// Cached values (for improved performance). Their values
	// are computed upon the first invocation of the respective
	// getter method.
	private List<Class<?>> inputTypesCached;

	private Class<?> outputTypeCached;

	private int hashCodeCached = 0;

	private boolean hashCodeComputed = false;

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
		return new SerializableRConstructor(constructor);
	}

	/*
	 * Creates ConstructorCallInfo from specified constructor by generating its
	 * input and output constraints.
	 */
	private RConstructor(Constructor<?> constructor) {
		if (constructor == null)
			throw new IllegalArgumentException(
					"constructor should not be null.");
		this.constructor = constructor;
		// TODO move this earlier in the process: check first that all
		// methods to be used can be made accessible.
		// XXX this should not be here but I get infinite loop when comment out
		this.constructor.setAccessible(true);

	    this.nullFlags = new BitSet(this.getInputTypes().size());
	    clearAllActiveFlags();		
	}

	/*
	 * Returns the constructor definining this ConstructorCallInfo
	 */
	public Constructor<?> getConstructor() {
		return this.constructor;
	}

	/**
	 * Returns the statement corresponding to the given constructor.
	 * 
	 * @param constructor
	 */
	@Testable
	public static RConstructor getRConstructor(Constructor<?> constructor) {
		return new RConstructor(constructor);
	}

	/**
	 * Returns concise string representation of this ConstructorCallInfo
	 */
	@Override
	public String toString() {
		return toParseableString();
	}

	// TODO integrate with below method
	public void appendCode(Variable varName, List<Variable> inputVars,
			StringBuilder b) {
		assert inputVars.size() == this.getInputTypes().size();

		Class<?> declaringClass = constructor.getDeclaringClass();
		boolean isNonStaticMember = !Modifier.isStatic(declaringClass
				.getModifiers())
				&& declaringClass.isMemberClass();
		assert Util.implies(isNonStaticMember, inputVars.size() > 0);

		// Note on isNonStaticMember: if a class is a non-static member class,
		// the
		// runtime signature of the constructor will have an additional argument
		// (as the first argument) corresponding to the owning object. When
		// printing
		// it out as source code, we need to treat it as a special case: instead
		// of printing "new Foo(x,y.z)" we have to print "x.new Foo(y,z)".

		// TODO the last replace is ugly. There should be a method that does it.
		String declaringClassStr = Reflection.getCompilableName(declaringClass);

		b.append(declaringClassStr
				+ " "
				+ varName.getName()
				+ " = "
				+ (isNonStaticMember ? inputVars.get(0) + "." : "")
				+ "new "
				+ (isNonStaticMember ? declaringClass.getSimpleName()
						: declaringClassStr) + "(");
		for (int i = (isNonStaticMember ? 1 : 0); i < inputVars.size(); i++) {
			if (i > (isNonStaticMember ? 1 : 0))
				b.append(", ");
			// We cast whenever the variable and input types are not identical.
			if (!inputVars.get(i).getType().equals(getInputTypes().get(i)))
				b.append("(" + getInputTypes().get(i).getCanonicalName() + ")");
			b.append(inputVars.get(i).getName());
		}
		b.append(");");
		b.append(Globals.lineSep);
	}

	@Testable
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (this == o)
			return true;
		if (!(o instanceof RConstructor))
			return false;
		RConstructor other = (RConstructor) o;
		if (!this.constructor.equals(other.constructor))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		if (!hashCodeComputed) {
			hashCodeComputed = true;
			hashCodeCached = this.constructor.hashCode();
		}
		return hashCodeCached;
	}

	/**
	 * Executes this statement, given the inputs to the statement. Returns the
	 * results of execution as an ResultOrException object and can output
	 * results to specified PrintStream.
	 */
	public ExecutionOutcome execute(Object[] statementInput, PrintStream out) {

		assert statementInput.length == this.getInputTypes().size();

		ConstructorReflectionCode code = new ConstructorReflectionCode(
				this.constructor, statementInput);

		long startTime = System.currentTimeMillis();
		Throwable thrown = ReflectionExecutor.executeReflectionCode(code, out);
		long totalTime = System.currentTimeMillis() - startTime;

		if (thrown == null) {
			return new NormalExecution(code.getReturnVariable(), totalTime);
		} else {
			return new ExceptionalExecution(thrown, totalTime);
		}
	}

	/**
	 * Extracts the input constraints for this ConstructorCallInfo
	 * 
	 * @return list of input constraints
	 */
	public List<Class<?>> getInputTypes() {
		if (inputTypesCached == null) {
			inputTypesCached = new ArrayList<Class<?>>(Arrays
					.asList(constructor.getParameterTypes()));
		}
		return inputTypesCached;
	}

	/**
	 * Returns constraint to represent new reference to this statement, namely
	 * the receiver that is generated.
	 */
	public Class<?> getOutputType() {
		if (outputTypeCached == null) {
			outputTypeCached = constructor.getDeclaringClass();
		}
		return outputTypeCached;
	}

	/**
	 * A string representing this constructor. The string is of the form:
	 * 
	 * CONSTRUCTOR
	 * 
	 * Where CONSTRUCTOR is a string representation of the constrctor signature.
	 * Examples:
	 * 
	 * java.util.ArrayList.<init>() java.util.ArrayList.<init>(java.util.Collection)
	 * 
	 */
	public String toParseableString() {
		return Reflection.getSignature(constructor);
	}

	public static StatementKind parse(String s) {
		return RConstructor.getRConstructor(Reflection
				.getConstructorForSignature(s));
	}
}
