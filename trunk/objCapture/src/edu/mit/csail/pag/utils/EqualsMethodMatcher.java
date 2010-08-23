package edu.mit.csail.pag.utils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import randoop.util.HeapLinearizer;
import randoop.util.HeapLinearizer.LinearizationKind;

public class EqualsMethodMatcher implements StateMatcher {

	Set<Object> cache = new LinkedHashSet<Object>();

	public boolean add(Object object) {
		try {
			return this.cache.add(object);
			/*
			 * for(Object obj : cacheLst) { if (obj == object && obj.getClass() ==
			 * object.getClass()) { return false; } } return
			 * this.cacheLst.add(object);
			 */
		} catch (Exception e) {
			// This could happen, because we're actually running code under
			// test.
			return false;
		}
	}
	
	public boolean contains(Object object)
	{
		try {
			if (object == null) return true;
			return cache.contains(object);
		} catch (Exception e) {
			// This could happen, because we're actually running code under
			// test.
			return false;
		}			
	}	

	public int size() {
		return this.cache.size();
	}
}
