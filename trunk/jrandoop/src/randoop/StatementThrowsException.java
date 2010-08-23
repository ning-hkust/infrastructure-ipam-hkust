package randoop;

import java.io.ObjectStreamException;
import java.io.Serializable;

import randoop.ocat.OCATGlobals;

/**
 * An observation recording the exception that a particular statement threw
 * during execution.
 */
public class StatementThrowsException implements Observation, Serializable {

	private final Class<? extends Throwable> exceptionClass;
	private final Throwable exception;
	private String additionalInfo;

	public StatementThrowsException(Throwable exception) {
		if (exception == null)
			throw new IllegalArgumentException("exception cannot be null.");
		this.exceptionClass = exception.getClass();
		this.exception = exception;
		checkTargetException();
	}
	
	
	private Object writeReplace() throws ObjectStreamException {
		return new SerializableExceptionObservation(exceptionClass);
	}

	public String toString() {
		return "// throws exception of type " + exceptionClass.getName()
				+ Globals.lineSep;
	}

	/**
	 * The "try" half of the try-catch wrapper.
	 */
	public String toCodeStringPreStatement() {
		StringBuilder b = new StringBuilder();
		b.append("// The following exception was thrown during execution."
				+ Globals.lineSep);
		b.append("// This behavior will recorded for regression testing."
				+ Globals.lineSep);
		
		if (additionalInfo != null && additionalInfo.length() > 0)
		{
			b.append(additionalInfo);
		}
		
		b.append("try {" + Globals.lineSep + "  ");
		return b.toString();
	}

	/**
	 * The "catch" half of the try-catch wrapper.
	 */
	public String toCodeStringPostStatement() {
		StringBuilder b = new StringBuilder();
		String exceptionClassName = exceptionClass.getCanonicalName();
		
    // altered by Ning
		if (exceptionClassName == null) {
      exceptionClassName = "Exception";
    }
		
		b.append("  fail(\"Expected exception of type " + exceptionClassName
				+ "\");" + Globals.lineSep);
		b.append("} catch (");
		b.append(exceptionClassName);
		b.append(" e) {" + Globals.lineSep);
		b.append("  // Expected exception." + Globals.lineSep);
		if (OCATGlobals.targetException != null)
		{
			b.append("  //Check this exception is a target exception" + Globals.lineSep);
			b.append("  RandoopTest.checkException(e);" + Globals.lineSep);
		}
		b.append("}" + Globals.lineSep);
		return b.toString();
	}
	
	public void checkTargetException() {
		// 1. check whether the exception mode is on
		// 2. check this sequence threw an exception
		if (OCATGlobals.targetException != null ) {
			// 3. if the exception is the target exception
			// System.out.println(seq);
			String output = "";
			String exceptionClassName = exception.getClass().getCanonicalName();

			if (exceptionClassName.equals(OCATGlobals.targetException)) {
				StackTraceElement[] ste = exception.getStackTrace();

				boolean matched = true;
				String targetste[] = { OCATGlobals.stackTrace1,
						OCATGlobals.stackTrace2, OCATGlobals.stackTrace3,
						OCATGlobals.stackTrace4, OCATGlobals.stackTrace5 };

				for (int i = 0; i < targetste.length; i++) {
					if (targetste[i] != null) {
						if (!targetste[i].equals(ste[i].toString()))
							matched = false;
					} else {
						matched = false;
						break;
					}

				}

				if (matched) {
					// 4. set the stop flag
					output += "" + "\r\n";
					output += "//###Type0 Exception Pefectly Matched " + "\r\n";
					output += "//# Exception class: " + exception.getClass() + "\r\n";
					for (int i = 0; i < targetste.length; i++)
						output += "//# StackTrace" + i + ": " + targetste[i]
								+ "\r\n";
				} else {
					boolean onematched = false;
					output += "" + "\r\n";
					output += "//@@@ the same exception occured: "
							+ exception.getClass() + "\r\n";

					for (int i = 0; i < ste.length; i++) {
						output += "//@ StackTrace" + i + ": " + ste[i] + "\r\n";
						for (int j = 0; j < targetste.length; j++) {
							if (ste[i].toString().equals(targetste[j])) {
								onematched = true;
								output += "//###Type" + (j + 1) + " matched: "
										+ targetste[j] + "\r\n";
							}
						}
					}

					if (!onematched) {
						output += "//@@@ only the same exception name has been occured, but the location is different\r\n";
					} else
						output += "//@@@ exception has been found." + "\r\n";

					output += "" + "\r\n";
				}

			}
			//eo.additionalInfo = output;
			//System.out.println(output);
			additionalInfo = output;
		}
	}


	public String toCodeSequencePostStatement() {
		// TODO Auto-generated method stub
		return null;
	}


	public String toCodeSequencePreStatement() {
		// TODO Auto-generated method stub
		return null;
	}

}
