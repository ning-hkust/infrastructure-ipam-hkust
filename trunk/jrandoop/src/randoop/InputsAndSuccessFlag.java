/**
 * 
 */
package randoop;

import java.util.List;

import utilMDE.Pair;

/**
 * Return type of an InputSelector's method selectsequencesAndMap(..). It
 * encapsulates a list of sequences and an inputMapping on those sequences.
 * 
 */
public class InputsAndSuccessFlag {

	public InputsAndSuccessFlag(boolean success,
			List<Pair<Sequence, Variable>> sequences) {
		this.success = success;
		this.sequences = sequences;
	}

	public boolean success;

	public List<Pair<Sequence, Variable>> sequences;
}
