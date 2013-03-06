package hk.ust.cse.Prevision_PseudoImpl;

import java.util.Collection;
import java.util.NoSuchElementException;

public class List {
  
  public boolean addAll(Collection<?> paramCollection) {
    java.util.Iterator<?> iter = paramCollection.iterator();
    while (iter.hasNext()) {
      Object object = (Object) iter.next();
      elementData[size++] = object;
    }
    return paramCollection.size() != 0;
  }
  
  public Object[] toArray() {
    return elementData;
  }
  
  public java.util.Iterator iterator() {
    return new Iterator(elementData);
  }
  
  public static class Iterator implements java.util.Iterator {
    private Iterator(Object[] elementData) {
      this.cursor = 0;
      this.elementData = elementData;
    }
    
    public boolean hasNext() {
      return cursor < elementData.length;
    }
    
    public boolean hasPrevious() {
      return (this.cursor > 0);
    }
    
    public Object next() {
      if (cursor < elementData.length) {
        return elementData[cursor++];
      }
      else {
        throw new NoSuchElementException();
      }
    }
    
    public Object previous() {
      if (cursor > 0) {
        return elementData[--cursor];
      }
      else {
        throw new NoSuchElementException();
      }
    }
    
    @Override
    public void remove() {
    }
    
    public int cursor;
    public Object[] elementData;
  }
  
  public int size;
  public Object[] elementData;
}
