package randoop;

import java.util.List;

import randoop.util.HeapLinearizer;
import randoop.util.HeapLinearizer.LinearizationKind;

public class EverythingIsDifferentMatcher implements StateMatcher {

	int size = 0;

	public boolean add(Object object) {
		size++;
		return true;
	}
	
	public boolean contains(Object object)
	{
		return false;
	}	

	public int size() {
		return size;
	}

}
