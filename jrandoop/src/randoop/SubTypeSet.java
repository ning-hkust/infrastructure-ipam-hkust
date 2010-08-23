/**
 * 
 */
package randoop;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import randoop.util.Reflection;

public class SubTypeSet {

	// The set of classes that have sequences. I.e. membership in this
	// set means that the SequenceCollection has one or more sequences that
	// create a value of the member type.
	public Set<Class<?>> typesWithsequences;

	// Maps a type to the list of subtypes that have sequences.
	// The list for a given type can be empty, which means that there
	// are no subtypes with sequences for the given type.
	public Map<Class<?>, List<Class<?>>> subTypesWithsequences;

	public SubTypeSet() {
		this.subTypesWithsequences = new LinkedHashMap<Class<?>, List<Class<?>>>();
		this.typesWithsequences = new LinkedHashSet<Class<?>>();
	}

	public void add(Class<?> c) {
		if (c == null)
			throw new IllegalArgumentException("c cannot be null.");
		if (typesWithsequences.contains(c))
			return;
		typesWithsequences.add(c);

		// Update existing entries
		for (Map.Entry<Class<?>, List<Class<?>>> e : subTypesWithsequences
				.entrySet()) {
			// If c can be used as e.getKey(), add it to its list
			if (Reflection.canBeUsedAs(c, e.getKey())) {
				e.getValue().add(c);
			}
		}
	}

	private void addQueryType(Class<?> c) {
		if (c == null)
			throw new IllegalArgumentException("c cannot be null.");
		Set<Class<?>> keySet = subTypesWithsequences.keySet();
		if (keySet.contains(c))
			return;

		List<Class<?>> classesWithsequencesForC = new LinkedList<Class<?>>();
		for (Class<?> classWithsequence : typesWithsequences) {
			if (Reflection.canBeUsedAs(classWithsequence, c)) {
				classesWithsequencesForC.add(classWithsequence);
			}
		}
		subTypesWithsequences.put(c, classesWithsequencesForC);
	}

	/**
	 * Returns an unmodifiable list of the typesWithsequences in this
	 * subTypesWithsequences that can be used as c. For each c2 in the result,
	 * Reflection.canBeUsedAs(c2, c).
	 */
	public List<Class<?>> getMatches(Class<?> c) {
		if (!subTypesWithsequences.containsKey(c)) {
			addQueryType(c);
		}
		return Collections.unmodifiableList(subTypesWithsequences.get(c));
	}

	// TODO create tests for this method.
	public boolean containsAssignableType(Class<?> c, Reflection.Match match) {
		if (!subTypesWithsequences.containsKey(c)) {
			addQueryType(c);
		}

		if (typesWithsequences.contains(c))
			return true;

		if (match == Reflection.Match.COMPATIBLE_TYPE) {
			return !subTypesWithsequences.get(c).isEmpty();
		}
		return false;
	}

	public int size() {
		return typesWithsequences.size();
	}

	public Set<Class<?>> getElements() {
		return typesWithsequences;
	}

}