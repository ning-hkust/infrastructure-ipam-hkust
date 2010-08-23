package randoop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import randoop.util.Reflection;

/**
 * A sequence that can be mutated (unlike a Sequence, which is immutable). The
 * "M" stands for "Mutable".
 */
public class MSequence {

	public List<MStatement> statements;

	/**
	 * Checks the following well-formedness properties for every statement in
	 * the list of statements:
	 * <ul>
	 * <li> The number of inputs is the same as the input of inputs specified by
	 * the statement kind.
	 * <li> Type type of each input is compatible with the type required by the
	 * statement kind.
	 * <li> The input variables come from earlier statements.
	 * <li> The result variable does not appear in earlier statements.
	 * </ul>
	 */
	public void checkRep() {
		Set<MVariable> prevVars = new LinkedHashSet<MVariable>();
		for (MStatement st : statements) {
			assert st.inputs.size() == st.statementKind.getInputTypes().size();
			for (int i = 0; i < st.inputs.size(); i++) {
				MVariable in = st.inputs.get(i);
				assert prevVars.contains(in) : this;
				assert in.owner == this : this;
				assert Reflection.canBeUsedAs(in.getType(), st.statementKind
						.getInputTypes().get(i));
			}
			assert !prevVars.contains(st.result);
			prevVars.add(st.result);
		}
	}

	public MSequence makeCopy() {
		MSequence newSeq = new MSequence();

		// Create a list of new variables, one per index.
		List<MVariable> newvars = new ArrayList<MVariable>();
		for (int i = 0; i < size(); i++) {
			newvars.add(new MVariable(newSeq, getVariable(i).getName()));
		}

		// Create a list of new statements that use the new variables.
		List<MStatement> statements = new ArrayList<MStatement>();
		for (int i = 0; i < size(); i++) {
			MStatement sti = this.statements.get(i);
			List<MVariable> newinputs = new ArrayList<MVariable>();
			for (MVariable v : sti.inputs) {
				newinputs.add(newvars.get(v.getDeclIndex()));
			}
			statements.add(new MStatement(sti.statementKind, newinputs, newvars
					.get(i)));
		}

		// Set the statements of the new sequence to the new statements.
		newSeq.statements = statements;
		checkRep();
		return newSeq;
	}

	/**
	 * Returns all the indices of statements where v is an input to the
	 * statement.
	 */
	public List<Integer> getUses(MVariable v) {
		List<Integer> uses = new ArrayList<Integer>();
		// All uses will come after declaration.
		for (int i = v.getDeclIndex() + 1; i < size(); i++) {
			if (statements.get(i).inputs.contains(v))
				uses.add(i);
		}
		return uses;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		for (MStatement st : statements) {
			b.append(st.toString());
			b.append(Globals.lineSep);
		}
		return b.toString();
	}

	public MStatement getDeclaringStatement(MVariable v) {
		return statements.get(getIndex(v));
	}

	public MStatement getStatement(int i) {
		return statements.get(i);
	}

	public int getIndex(MVariable v) {
		if (v == null)
			throw new IllegalArgumentException();
		if (v.owner != this)
			throw new IllegalArgumentException();
		for (int i = 0; i < statements.size(); i++) {
			MStatement st = statements.get(i);
			if (st.result == v)
				return i;
		}
		throw new BugInRandoopException();
	}

	public int size() {
		return statements.size();
	}

	public MVariable getVariable(int i) {
		if (i < 0 || i >= this.size())
			throw new IllegalArgumentException();
		return this.statements.get(i).result;
	}

	public Sequence toImmutableSequence() {
		Sequence seq = new Sequence();
		for (int i = 0; i < this.size(); i++) {
			List<Variable> inputs = new ArrayList<Variable>();
			for (MVariable sv : this.statements.get(i).inputs) {
				inputs.add(seq.getVariable(sv.getDeclIndex()));
			}
			seq = seq.extend(this.statements.get(i).statementKind, inputs);
		}
		return seq;
	}

	/** A compilable (Java source code) representation of this sequence. */
	public String toCodeString() {
		return toImmutableSequence().toCodeString();
	}

	/**
	 * Inserts the statements making up the given sequence into this sequence,
	 * at the given index. The parameter sequence is left unchanged. The
	 * receiver sequence is mutated. For convenience, returns a mapping from
	 * variables in the parameter sequence to their corresponding variables in
	 * the modified receiver sequence.
	 * 
	 * @param index
	 *            The index at which to insert the given sequence.
	 * @param seq
	 *            The sequence to insert.
	 * @return A mapping from variables in the parameter sequence to their
	 *         corresponding variables in this sequence.
	 */
	public Map<MVariable, MVariable> insert(int index, MSequence seq) {
		if (seq == null) {
			throw new IllegalArgumentException("seq cannot be null.");
		}
		if (index < 0 || index >= size()) {
			String msg = "Invalid index: " + index + " for sequence "
					+ toString();
			throw new IllegalArgumentException(msg);
		}

		List<MStatement> sts = new ArrayList<MStatement>();

		Map<MVariable, MVariable> varmap = new LinkedHashMap<MVariable, MVariable>();

		for (MStatement oldst : seq.statements) {

			// Create input list for statement.
			List<MVariable> newInputs = new ArrayList<MVariable>(oldst.inputs
					.size());
			for (MVariable var : oldst.inputs) {
				MVariable newvar = varmap.get(var);
				assert newvar != null;
				assert newvar.owner == this;
				newInputs.add(newvar);
			}
			MVariable newvar = new MVariable(this, oldst.result.getName());
			varmap.put(oldst.result, newvar);
			MStatement newStatement = new MStatement(oldst.statementKind,
					newInputs, newvar);
			sts.add(newStatement);
		}

		this.statements.addAll(index, sts);
		checkRep();
		return varmap;
	}

	public List<MVariable> getInputs(int statementIndex) {
		return statements.get(statementIndex).inputs;
	}

	public StatementKind getStatementKind(int statementIndex) {
		return statements.get(statementIndex).statementKind;
	}
}
