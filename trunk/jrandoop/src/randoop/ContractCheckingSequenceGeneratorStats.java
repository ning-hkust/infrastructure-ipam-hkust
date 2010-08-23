package randoop;

import java.util.List;

public class ContractCheckingSequenceGeneratorStats extends
		SequenceGeneratorStats {
	public ContractCheckingSequenceGeneratorStats(
			List<StatementKind> statements, List<Class<?>> coverageClasses) {
		super(statements, coverageClasses);
	}

	private static final long serialVersionUID = -2475024176853398636L;
	// private static final StatName
	// STAT_SEQUENCE_OBJECT_CONTRACT_VIOLATED_LAST_STATEMENT =
	// new StatName("STAT_SEQUENCE_OBJECT_CONTRACT_VIOLATED_LAST_STATEMENT",
	// "ObjVio",
	// "Number of sequences where object contract violated after last
	// statement.", true);
	// private static final StatName
	// STAT_SEQUENCE_FORBIDDEN_EXCEPTION_LAST_STATEMENT =
	// new StatName("STAT_SEQUENCE_FORBIDDEN_EXCEPTION_LAST_STATEMENT", "ExVio",
	// "Number of sequences where bad exception was thrown after last
	// statement.", true);

	// public ContractCheckingSequenceGeneratorStats(List<StatementKind>
	// statements) {
	// super(statements);
	// addStats();
	// }

	// private void addStats() {
	// addKey(STAT_SEQUENCE_OBJECT_CONTRACT_VIOLATED_LAST_STATEMENT);
	// addKey(STAT_SEQUENCE_FORBIDDEN_EXCEPTION_LAST_STATEMENT);
	// }
	// public enum ExecutionSummary { NORMAL, NOT_NORMAL_NO_BUG, BUG }

	// public ExecutionSummary
	// updateSequenceLevelContractStatistics(ExecutableSequence es) {
	// if (es.hasNonExecutedStatements()) {
	// return ExecutionSummary.NOT_NORMAL_NO_BUG;
	// }

	// StatementKind lastStatement = es.sequence.getLastStatement();
	// int lastIndex = es.sequence.size() - 1;

	// if (es.hasDecoration(ExceptionObservation.class, lastIndex)) { // XXX
	// addToCount(lastStatement,
	// STAT_SEQUENCE_FORBIDDEN_EXCEPTION_LAST_STATEMENT, 1);
	// return ExecutionSummary.BUG;
	// }

	// if (es.hasDecoration(ExpressionEqualsVariable.class, lastIndex)) {
	// addToCount(lastStatement,
	// STAT_SEQUENCE_OBJECT_CONTRACT_VIOLATED_LAST_STATEMENT, 1);
	// return ExecutionSummary.BUG;
	// }

	// if (es.isNormalExecution()) {
	// return ExecutionSummary.NORMAL;
	// }
	// return ExecutionSummary.NOT_NORMAL_NO_BUG;
	// }

}
