package randoop;

/**
 * A visitor that checks whether an invocation of toString() raises an
 * exception, and adds an error-revealing observation if it does.
 */
public class ToStringExceptionVisitor implements ExecutionVisitor {

	public boolean visitAfter(ExecutableSequence sequence, int i) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	public void visitBefore(ExecutableSequence sequence, int i) {
		// Empty body.
	}

}
