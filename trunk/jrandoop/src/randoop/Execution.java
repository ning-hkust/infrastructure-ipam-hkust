package randoop;

import java.util.Arrays;

/**
 * Represents the unfolding execution of a sequence.
 * 
 * Stores information in a list of ExecutionOutcome objects, one for each
 * statement in the sequence.
 */
public final class Execution {

	// The execution outcome of each statement.
	protected final ExecutionOutcome[] theList;

	// The sequence whose execution results this object stores.
	protected final Sequence owner;

	/**
	 * Create an Execution to store the execution results of the given sequence.
	 * The list of outcomes is initialized to NotExecuted for every statement.
	 */
	public Execution(Sequence owner) {
		this.owner = owner;
		this.theList = new ExecutionOutcome[owner.size()];
		Arrays.fill(theList, NotExecuted.create());
	}

	/** The size of the list. */
	public int size() {
		return theList.length;
	}

	/** Set the i-th slot to the given outcome. */
	public void set(int i, ExecutionOutcome outcome) {
		if (i < 0 || i >= theList.length)
			throw new IllegalArgumentException("wrong index " + i);
		if (outcome == null)
			throw new IllegalArgumentException("outcome cannot be null.");
    theList[i] = outcome;
	}

	/** Get the outcome in the i-th slot. */
	public ExecutionOutcome get(int i) {
		if (i < 0 || i >= theList.length)
			throw new IllegalArgumentException("wrong index.");
		return theList[i];
	}

  // altered by Ning
  public void clear() {
    for (int i = 0; i < theList.length; i++) {
      theList[i] = null;
    }
  }

	public ExecutionOutcome getResult(Variable v) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}
}
