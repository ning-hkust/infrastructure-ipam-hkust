package randoop;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the fact that an expression, when evaluated, results in an exception.
 */
public class ExpressionThrowsException implements ContractViolation {

	// The expression whose runtime value this observation records.
	public Class<? extends ObjectContract> objcontract;

	// The variables over which the expression applies.
	public List<Variable> vars;

	// The kind of exception that the expression throws.
	private final Class<? extends Throwable> exceptionClass;

	public ExpressionThrowsException(
			Class<? extends ObjectContract> expression, List<Variable> vars,
			Throwable exception) {
		this.objcontract = expression;
		this.vars = new ArrayList<Variable>(vars);
		if (exception == null)
			throw new IllegalArgumentException("exception cannot be null.");
		this.exceptionClass = exception.getClass();
	}

	public String toCodeStringPreStatement() {
		return "";
	}

	public String toCodeStringPostStatement() {
		StringBuilder b = new StringBuilder();
		b.append(Globals.lineSep);
		b.append("// Checks "
				+ ExpressionUtils.localizeExpressionComment(objcontract, vars)
				+ Globals.lineSep);
		b.append("try {" + Globals.lineSep + "  ");

		String codeStr = null;
		try {
			codeStr = objcontract.newInstance().toCodeString();
		} catch (Exception e) {
			throw new Error(e);
		}
		if (codeStr == null) {
			b.append("  " + ExpressionUtils.toCodeString(objcontract, vars));
		} else {
			b
					.append("  "
							+ ExpressionUtils.localizeExpressionCode(
									objcontract, vars));
		}
		b.append(";" + Globals.lineSep);
		String exceptionClassName = exceptionClass.getCanonicalName();

    // altered by Ning
    if (exceptionClassName == null) {
      exceptionClassName = "Exception";
    }

		b.append("} catch (");
		b.append(exceptionClassName);
		b.append(" e) {" + Globals.lineSep);
		b.append("  fail(\"Unexpected exception: \" + e.toString());"
				+ Globals.lineSep);
		b.append("}" + Globals.lineSep);

		return b.toString();
	}

}
