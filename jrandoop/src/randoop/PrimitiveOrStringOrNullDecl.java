package randoop;

import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import randoop.util.PrimitiveTypes;
import randoop.util.Reflection;
import randoop.util.StringEscapeUtils;
import randoop.util.Util;
import utilMDE.UtilMDE;

/**
 * Represents a primitive value. This type of statement doesn't actually
 * transform any state, but it works out nicely to represent primitives as
 * statements.
 * 
 * This decl info is for primitives, strings and nulls (of any type)
 */
public final class PrimitiveOrStringOrNullDecl implements StatementKind,
		Serializable {

	/** ID for parsing purposes (see StatementKinds.parse method) */
	public static final String ID = "prim";

	// State variables.
	private final Class<?> type;

	// This value is guaranteed to be null, a String, or a boxed primitive.
	private final Object value;

	private Object writeReplace() throws ObjectStreamException {
		return new SerializablePrimitiveOrStringOrNullDecl(type, value);
	}

	/**
	 * Constructs a PrimitiveOrStringOrNullDeclInfo of type t and value o
	 */
	public PrimitiveOrStringOrNullDecl(Class<?> t, Object o) {
		if (t == null)
			throw new IllegalArgumentException("t should not be null.");

		if (void.class.equals(t))
			throw new IllegalArgumentException("t should not be void.class.");

		if (t.isPrimitive()) {
			if (o == null)
				throw new IllegalArgumentException(
						"primitive-like values cannot be null.");
			if (!PrimitiveTypes.boxedType(t).equals(o.getClass()))
				throw new IllegalArgumentException("o.getClass()="
						+ o.getClass() + ",t=" + t);
			if (!PrimitiveTypes.isBoxedOrPrimitiveOrStringType(o.getClass()))
				throw new IllegalArgumentException(
						"o is not a primitive-like value.");
		}  else if (!t.equals(String.class) && o != null) {
			// if it's not primitive or string then must be null
			if (!PrimitiveTypes.isBoxedOrPrimitiveOrStringType(o.getClass()))
				throw new IllegalArgumentException(
						"value must be null for not primitive, not string type "
								+ t + " but was " + o);

		}

		this.type = t;
		this.value = o;

	}

	/**
	 * Indicates whether this PrimitiveOrStringOrNullDeclInfo is equal to o
	 */
	@Testable
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PrimitiveOrStringOrNullDecl))
			return false;
		if (this == o)
			return true;
		PrimitiveOrStringOrNullDecl other = (PrimitiveOrStringOrNullDecl) o;

		return this.type.equals(other.type)
				&& Util.equalsWithNull(this.value, other.value);
	}

	/**
	 * Returns a hash code value for this PrimitiveOrStringOrNullDeclInfo
	 */
	@Override
	public int hashCode() {
		return this.type.hashCode()
				+ (this.value == null ? 0 : this.value.hashCode());
	}

	/**
	 * Returns string representation of this PrimitiveOrStringOrNullDeclInfo
	 */
	@Override
	public String toString() {
		return toParseableString();
	}

	/**
	 * Executes this statement, given the inputs to the statement. Returns the
	 * results of execution as an ResultOrException object and can output
	 * results to specified PrintStream.
	 */
	public ExecutionOutcome execute(Object[] statementInput, PrintStream out) {
		assert statementInput.length == 0;
		return new NormalExecution(this.value, 0);
	}

	/**
	 * Extracts the input constraints for this PrimitiveOrStringOrNullDeclInfo
	 * 
	 * @return list of input constraints
	 */
	public List<Class<?>> getInputTypes() {
		return Collections.emptyList();
	}

	public void appendCode(Variable newVar, List<Variable> inputVars,
			StringBuilder b) {

		if (type.isPrimitive()) {

			b.append(PrimitiveTypes.boxedType(type).getName());
			b.append(" ");
			b.append(newVar.getName());
			b.append(" = new ");
			b.append(PrimitiveTypes.boxedType(type).getName());
			b.append("(");
			b.append(PrimitiveTypes.toCodeString(getValue()));
			b.append(");");
			b.append(Globals.lineSep);

		} else {
			b.append(Reflection.getCompilableName(type));
			b.append(" ");
			b.append(newVar.getName());
			b.append(" = ");
			b.append(PrimitiveTypes.toCodeString(getValue()));
			b.append(";");
			b.append(Globals.lineSep);
		}
	}

	/**
	 * Returns the value of this PrimitiveOrStringOrNullDeclInfo
	 */
	public Object getValue() {
		return value;
	}

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
	 * Returns the appropriate PrimitiveOrStringOrNullDeclInfo representative of
	 * the specified class c.
	 */
	public static PrimitiveOrStringOrNullDecl nullOrZeroDecl(Class<?> c) {
		if (String.class.equals(c))
			return new PrimitiveOrStringOrNullDecl(String.class, "");
		if (Character.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Character.TYPE, 'a'); // TODO
		// This
		// is
		// not
		// null
		// or
		// zero...
		if (Byte.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Byte.TYPE, (byte) 0);
		if (Short.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Short.TYPE, (short) 0);
		if (Integer.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Integer.TYPE, (Integer
					.valueOf(0)).intValue());
		if (Long.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Long.TYPE, (Long.valueOf(0))
					.longValue());
		if (Float.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Float.TYPE, (Float
					.valueOf(0)).floatValue());
		if (Double.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Double.TYPE, (Double
					.valueOf(0)).doubleValue());
		if (Boolean.TYPE.equals(c))
			return new PrimitiveOrStringOrNullDecl(Boolean.TYPE, false);
		return new PrimitiveOrStringOrNullDecl(c, null);
	}

	public String toParseableString() {

		String valStr = null;
		if (value == null) {
			valStr = "null";
		} else {
			Class<?> valueClass = PrimitiveTypes
					.primitiveType(value.getClass());

			if (String.class.equals(valueClass)) {
				valStr = "\"" + StringEscapeUtils.escapeJava(value.toString())
						+ "\"";
			} else if (char.class.equals(valueClass)) {
				valStr = Integer.toHexString((Character) value);
			} else {
				valStr = value.toString();
			}
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
	 */
	public static PrimitiveOrStringOrNullDecl parse(String s) {
		if (s == null)
			throw new IllegalArgumentException("s cannot be null.");
		// Extract type and value.
		String typeString = s.substring(0, s.indexOf(':'));
		String valString = s.substring(s.indexOf(':') + 1);
		Class<?> type = Reflection.classForName(typeString);
		Object value = null;

		if (type.equals(char.class)) {
			value = (char) Integer.parseInt(valString, 16);
		} else if (type.equals(byte.class)) {
			value = Byte.valueOf(valString);
		} else if (type.equals(short.class)) {
			value = Short.valueOf(valString);
		} else if (type.equals(int.class)) {
			value = Integer.valueOf(valString);
		} else if (type.equals(long.class)) {
			value = Long.valueOf(valString);
		} else if (type.equals(float.class)) {
			value = Float.valueOf(valString);
		} else if (type.equals(double.class)) {
			value = Double.valueOf(valString);
		} else if (type.equals(boolean.class)) {
			value = Boolean.valueOf(valString);
		} else if (type.equals(String.class)) {
			if (valString.equals("null")) {
				value = null;
			} else {
				value = valString;
				assert valString.charAt(0) == '"';
				assert valString.charAt(valString.length() - 1) == '"';
				value = UtilMDE.unescapeNonJava(valString.substring(1,
						valString.length() - 1));
			}
		} else {
			assert valString.equals("null");
			value = null;
		}

		return new PrimitiveOrStringOrNullDecl(type, value);
	}
}