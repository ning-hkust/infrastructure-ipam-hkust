package randoop;

import java.util.ArrayList;
import java.util.List;

/**
 * An execution visitor that checks a set of unary object contracts on the
 * values created by the sequence. It does this only after the last statement
 * has been executed. For each contract violation, the visitor adds an
 * Observation to the last index in the sequence.
 * 
 * If the sequence throws an exception, the visitor does not check any
 * contracts. If it does not throw an exception, it checks all contracts on each
 * object returned by each statement, except objects that are boxed primitives
 * or Strings.
 */
public final class ContractCheckingVisitor implements ExecutionVisitor {

	private List<ObjectContract> contracts;

	/**
	 * Create a new visitor that checks the given contracts after the last
	 * statement in a sequence is executed.
	 * 
	 * @param contracts
	 *            Expected to be unary contracts, i.e. for each contract
	 *            <code>c</code>, <code>c.getArity() == 1</code>.
	 */
	public ContractCheckingVisitor(List<ObjectContract> contracts) {
		this.contracts = new ArrayList<ObjectContract>();
		for (ObjectContract c : contracts) {
			if (c.getArity() != 1)
				throw new IllegalArgumentException(
						"Visitor accepts only unary contracts.");
			this.contracts.add(c);
		}
	}

	public void visitBefore(ExecutableSequence sequence, int i) {
		// no body.
	}

	/**
	 * If idx is the last index, checks contracts.
	 */
	public boolean visitAfter(ExecutableSequence s, int idx) {

		// We check contracts only after the last statement is executed.
		if (idx < s.sequence.size() - 1)
			return true;

		if (s.hasNonExecutedStatements()) {
			return true;
		}

		if (s.throwsException())
			return true;

		List<Observation> obsl = new ArrayList<Observation>();

		for (int i = 0; i < s.sequence.size(); i++) {

			ExecutionOutcome result = s.getResult(i);

			assert result instanceof NormalExecution;

			Class<?> outputType = s.sequence.getStatementKind(i)
					.getOutputType();

			if (outputType.equals(void.class))
				continue;
			if (outputType.equals(String.class))
				continue;
			if (outputType.isPrimitive())
				continue;

			Object runtimeValue = ((NormalExecution) result).getRuntimeValue();
			if (runtimeValue == null)
				continue;

			for (ObjectContract c : contracts) {

				ExecutionOutcome exprOutcome = ExpressionUtils.execute(c,
						((NormalExecution) result).getRuntimeValue());

				Observation obs = null;
				if (exprOutcome instanceof NormalExecution) {
					NormalExecution e = (NormalExecution) exprOutcome;
					if (e.getRuntimeValue().equals(true)) {
						continue; // Behavior ok.
					} else {
						List<Variable> vars = new ArrayList<Variable>();
						vars.add(s.sequence.getVariable(i));
						// Create an observation that records the actual value
						// returned by the expression, marking it as invalid
						// behavior.
						obs = new ExpressionEqFalse(c.getClass(), vars, e
								.getRuntimeValue());
					}

				} else {
					assert exprOutcome instanceof ExceptionalExecution;
					ExceptionalExecution e = (ExceptionalExecution) exprOutcome;
					List<Variable> vars = new ArrayList<Variable>();
					vars.add(s.sequence.getVariable(i));
					// Create an observation that records the exception thrown,
					// marking it as invalid behavior.
					obs = new ExpressionThrowsException(c.getClass(), vars, e
							.getException());
				}

				assert obs != null;
				obsl.add(obs);
			}
		}
		s.addObservations(idx, obsl);

		return true;
	}
}
