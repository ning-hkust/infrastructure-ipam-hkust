package randoop.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import randoop.StateMatcher;
import randoop.util.HeapLinearizer.LinearizationKind;

public class HeapShapeMatcher implements StateMatcher {

	Set<String> cache = new LinkedHashSet<String>();

	public boolean add(Object object) {
		return cache.add(HeapLinearizer.linearize(object,
				LinearizationKind.SHAPE, false).toString());
	}

	public boolean contains(Object object)
	{
		List<Object> l = HeapLinearizer.linearize(object,
				LinearizationKind.SHAPE, false);
		String s = l.toString();

		return cache.contains(s);
	}
	
	public int size() {
		return cache.size();
	}

}
