package randoop;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import randoop.ocat.OCATGlobals;
import randoop.util.HeapMatcher;
import randoop.util.Log;
import randoop.util.PrimitiveTypes;
import randoop.util.Reflection;

public class ObjectCache implements Serializable {

	private static final long serialVersionUID = -8051750221965948545L;

	private StateMatcher sm;
	private final Map<String, Integer> classInstanceNum;
	private long InstanceNumMax=0;

	public ObjectCache() {
		//this.sm = sm;
		
		if (OCATGlobals.JJ_heapmatcher)
			sm = new HeapMatcher();
		else
			sm = new EqualsMethodMatcher();
		//new HeapShapeMatcher();
		
		this.classInstanceNum = new LinkedHashMap<String, Integer>();
	}

	public boolean setActiveFlags(ExecutableSequence sequence) {

		for (int i = 0; i < sequence.sequence.size(); i++) {

			// If statement was not executed, clear active flag.
			if (sequence.getResult(i) instanceof NotExecuted) {
				if (Log.isLoggingOn())
					Log.logLine("Statement " + i
							+ " was not executed (due to failures earlier in the sequence)."
							+ " Making inactive.");
				sequence.sequence.clearActiveFlag(i);
				continue;
			}

			// If exception thrown at index i, clear active flag.
			if (sequence.getResult(i) instanceof ExceptionalExecution) {
				if (Log.isLoggingOn())
					Log.logLine("Statement " + i
							+ " threw exception. Making inactive.");
				sequence.sequence.clearActiveFlag(i);
				continue;
			}

			assert sequence.getResult(i) instanceof NormalExecution;
			NormalExecution e = (NormalExecution) sequence.getResult(i);

			// If runtime value is null, clear active flag.
			if (e.getRuntimeValue() == null) {
				if (Log.isLoggingOn())
					Log.logLine("Object " + i + " is null. Making inactive.");
				sequence.sequence.clearActiveFlag(i);
				continue;
			}

			// Sanity check: object is of the correct type.
			Class<?> objectClass = e.getRuntimeValue().getClass();
			Class<?> constraintType = sequence.sequence.getStatementKind(i)
					.getOutputType();
			if (!Reflection.canBeUsedAs(objectClass, constraintType))
				throw new BugInRandoopException("objectClass="
						+ objectClass.getName() + ", constraingType="
						+ constraintType.getName());

			/**/
			// This part is disabled for ART since primitive data type will be
			// considered for each statement.
			// If runtime value is a primitive value, clear active flag.
			if (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(objectClass)) {
				if (Log.isLoggingOn())
					Log.logLine("Object " + i
							+ " is a primitive. Making inactive.");
				sequence.sequence.clearActiveFlag(i);
				continue;
			}

			// If runtime value is in object cache, clear active flag.
			if (OCATGlobals.JJ_nofilter == false)
			if (false /*!this.sm.add(e.getRuntimeValue())*/) { // altered by Ning: this.sm.add(e.getRuntimeValue()) causes OutOfMemoryError quickly
				if (Log.isLoggingOn())
					Log.logLine("Already created an object("
							+ e.getRuntimeValue().getClass().toString()
							+ ") equal to " + i + "th output. Making inactive");
				sequence.sequence.clearActiveFlag(i);
				continue;
			}

			addClassNumInstances(e.getRuntimeValue().getClass().getName());
			//if (Log.isLoggingOn())
			//	Log.logLine("Object " + i + " NOT set to inactive.");

		}
		return sequence.sequence.hasActiveFlags();
	}

	public int getClassNumInstances(String className)
	{
		Integer instanceNum = classInstanceNum.get(className);
		if (instanceNum == null)
		{
			//System.out.println("No coverage data for " + className);
			return 0;
		}
		return instanceNum;
	}
	
	public boolean addClassNumInstances(String className)
	{
		Integer instanceNum = classInstanceNum.get(className);
		if (instanceNum == null)
		{
			//System.out.println("No coverage data for " + className);
			instanceNum = 1;
		}
		instanceNum++;
		if (instanceNum > InstanceNumMax)
			InstanceNumMax = instanceNum;
		classInstanceNum.put(className, instanceNum);
		return true;
	}

	public long getMaxInstanceNum() {
		return InstanceNumMax;
	}	
}
