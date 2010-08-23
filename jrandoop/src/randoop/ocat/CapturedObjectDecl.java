package randoop.ocat;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import edu.mit.csail.pag.objcap.util.Serializer;

import randoop.ExceptionalExecution;
import randoop.ExecutionOutcome;
import randoop.Globals;
import randoop.NormalExecution;
import randoop.RMethod;
import randoop.StatementKind;
import randoop.Variable;
import randoop.util.Reflection;

/**
 * Represents a primitive value. This type of statement doesn't actually
 * transform any state, but it works out nicely to represent primitives as
 * statements.
 * 
 * This decl info is for primitives, strings and nulls (of any type)
 */

public final class CapturedObjectDecl implements StatementKind, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6591351900720259814L;

	/** ID for parsing purposes (see StatementKinds.parse method) */
	public static final String ID = "capobj";

	// State variables.
	private Class<?> type = null;
	private final String className;

	// This value is guaranteed to be null, a String, or a boxed primitive.
	// private final Object value;
	private final String fileName;

	/*
	 * private Object writeReplace() throws ObjectStreamException { return new
	 * SerializablePrimitiveOrStringOrNullDecl(type, value); }
	 */

	/**
	 * Constructs a CapturedObjectDecl
	 */

	public CapturedObjectDecl(String className, String fileName) {
		if (className == null)
			throw new IllegalArgumentException("className should not be null.");
		if (fileName == null)
			throw new IllegalArgumentException("fileName should not be null.");

		this.className = className;
		this.fileName = fileName;
		this.type = Reflection.classForName(className);

		if (this.type == null) {
			throw new IllegalArgumentException(className
					+ " class cannot be found.");
		}

		// this.value = fileName;

	}

	/**
	 * Indicates whether this CapturedObjectDecl is equal to o
	 * 
	 * @Testable
	 * @Override public boolean equals(Object o) { if (!(o instanceof
	 *           CapturedObjectDecl)) return false; if (this == o) return true;
	 *           CapturedObjectDecl other = (CapturedObjectDecl) o;
	 * 
	 *           return this.type.equals(other.type) &&
	 *           Util.equalsWithNull(this.value, other.value); }
	 */

	/**
	 * Returns a hash code value for this CapturedObjectDecl
	 * 
	 * @Override public int hashCode() { return this.type.hashCode() +
	 *           (this.value == null ? 0 : this.value.hashCode()); }
	 */

	/**
	 * Returns string representation of this CapturedObjectDecl
	 */
	@Override
	public String toString() {
		return toParseableString();
	}

	/**
	 * Executes this statement, given the inputs to the statement. Returns the
	 * results of execution as an ResultOrException object and can output
	 * results to specified PrintStream.
	 * 
	 * @param statementInput
	 *            array containing appropriate inputs to statement
	 * @param out
	 *            stream to output results of execution; can be null if you
	 *            don't want to print.
	 * @return results of executing this statement
	 */
	public ExecutionOutcome execute(Object[] statementInput, PrintStream out) {
		assert statementInput.length == 0;

		RMethod rm = null;
		try {
			rm = RMethod.getRMethod(Serializer.class.getMethod(
					"loadObjectFromFile", String.class));
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}

		Object[] inputVariables = new Object[1];
		inputVariables[0] = this.fileName;

		// assert (rm == null);
		ExecutionOutcome eout = rm.execute(inputVariables, System.out);
		if (eout instanceof NormalExecution) {
			type = ((NormalExecution) eout).getRuntimeValue().getClass();
		} else if (eout instanceof ExceptionalExecution) {
			System.out.println("Fail to load a captured object: "
					+ eout.toString() + " " + className + " " + this.fileName);
		}
		return eout;
	}

	/**
	 * Extracts the input constraints for this CapturedObjectDecl
	 * 
	 * @return list of input constraints
	 */
	public List<Class<?>> getInputTypes() {
		return Collections.emptyList();
	}

	public void appendCode(Variable newVar, List<Variable> inputVars,
			StringBuilder b) {

		// clazz obj = (clazz) Serializer.loadObjectFromFile(fileName);
		String fname = fileName.replaceAll("\\\\", "\\\\\\\\");
		b.append(className);
		b.append(" ");
		b.append(newVar.getName());
		b.append(" = (");
		b.append(className);
		b.append(") Serializer.loadObjectFromFile(\"");
		b.append(fname);
		b.append("\");");
		b.append(Globals.lineSep);

	}

	/**
	 * Returns the value of this CapturedObjectDecl
	 * 
	 * public Object getValue() { return value; }
	 */

	/**
	 * @return Returns the type.
	 */
	public Class<?> getType() {
		return this.type;
	}

	/**
	 * Returns constraint to represent new reference to this statement
	 */
	public Class<?> getOutputType() {
		return this.type;
	}

	/**
	 * Returns a string representation of this StatementKind, which can
	 * subsequently be used in this class's parse method. For a class C
	 * implementing the StatementKind interface, this method should return a
	 * String s such that parsing the string returns an object equivalent to
	 * this object, i.e. C.parse(this.s).equals(this).
	 */
	public String toParseableString() {

		String valStr = null;
		if (fileName == null) {
			valStr = "null";
		} else {
			valStr = fileName;
		}

		return type.getName() + ":" + valStr;
	}

	/**
	 * A string representing this primitive declaration. The string is of the
	 * form:
	 * 
	 * TYPE:VALUE
	 * 
	 * Where TYPE is the type of the primitive declaration, and VALUE is its
	 * value. If VALUE is "null" then the value is null (not the String "null").
	 * If TYPE is "char" then (char)Integer.parseInt(VALUE, 16) yields the
	 * character value.
	 * 
	 * Examples:
	 * 
	 * java.lang.String:null represents: String x = null java.lang.String:""
	 * represents: String x = ""; java.lang.String:" " represents: String x =
	 * " "; java.lang.String:"\"" represents: String x = "\"";
	 * java.lang.String:"\n" represents: String x = "\n";
	 * java.lang.String:"\u0000" represents: String x = "\u0000";
	 * java.lang.Object:null represents: Object x = null;
	 * [[Ljava.lang.Object;:null represents: Object[][] = null; int:0
	 * represents: int x = 0; boolean:false represents: boolean x = false;
	 * char:20 represents: char x = ' ';
	 * 
	 * @throws Exception
	 * 
	 */

	public static CapturedObjectDecl parse(String s) throws Exception {
		if (s == null)
			throw new IllegalArgumentException("s cannot be null.");
		// Extract type and value.
		String typeString = s.substring(0, s.indexOf(':'));
		String fileString = s.substring(s.indexOf(':') + 1);

		return new CapturedObjectDecl(typeString, fileString);
	}

}