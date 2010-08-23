package randoop;

public final class EqualsHashcodeContractViolated implements Expression {

	public EqualsHashcodeContractViolated() {
		/* empty */
	}

	public Object evaluate(Object... objects) {
		assert objects != null && objects.length == 2;
		Object o = objects[0];
		Object o2 = objects[1];
		// Both null. Trivially true.
		if (o == null && o2 == null)
			return true;
		// One null, the other not. Clearly not equal.
		if (o == null || o2 == null)
			return true;
		// Not equal.
		if (!o.equals(o2))
			return true;
		// Equal. Hashcode should be too.
		return o.hashCode() == o2.hashCode();
	}

	public int getArity() {
		return 2;
	}

	public String toCommentString() {
		throw new RuntimeException("Not implemented.");
	}

	public String toCodeString() {
		return null;
	}
}
