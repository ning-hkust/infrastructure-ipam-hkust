package hk.ust.cse.Prevision_PseudoImpl;

import java.util.NoSuchElementException;

public class Map {

  public Map(int paramInt, float paramFloat) {
    size     = 0;
    modCount = 0;

    if (paramInt < 0)
      throw new IllegalArgumentException();

    if (paramFloat <= 0F)
      throw new IllegalArgumentException();
  }
  
  public Object put(HashCode key, Object value) {
    if (key.__hashcode__ == size && size >= 0) {
      __table__ = new Object[size + 1];
      __table__[key.__hashcode__] = value;
      size++;
      return null; /* does not consider override */
    }
    else {
      size = -100;
      return null;
    }
  }
  
  public Object remove(HashCode key) {
    if (key.__hashcode__ == -1) {
      return null;
    }
    else if (key.__hashcode__ == size - 1 && size >= 0) {
      Object o = __table__[key.__hashcode__];
      __table__[key.__hashcode__] = null;
      __table__ = new Object[size - 1];
      size--;
      return o;
    }
    else {
      size = -100;
      return null;
    }
  }
  
  public Object get(HashCode key) {
    if (key.__hashcode__ >= 0 && size >= 0) {
      if (key.__hashcode__ < size) {
        return __table__[key.__hashcode__];
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }
  
  public boolean containsKey(HashCode hashCode) {
    return hashCode.__hashcode__ >= 0 && hashCode.__hashcode__ < size;
  }
  
  public KeySet keySet() {
    return new KeySet(size);
  }
  
  public static class KeySet {

    public KeySet(int size) {
      if (size >= 0) {
        this.size = size;
      }
      else {
        throw new IllegalArgumentException();
      }
    }
    
    public KeyIterator iterator() {
      return new KeyIterator(size);
    }
    
    public int size;
  }
  
  public static class KeyIterator {
    public KeyIterator(int size) {
      this.size = size;
      this.current = 0;
    }
    
    public boolean hasNext() {
      return current < size;
    }
    
    public HashCode next() {
      if (current < size) {
        return new HashCode(current++);
      }
      else {
        throw new NoSuchElementException();
      }
    }
    
    public int size;
    public int current;
  }
  
  public int size;
  public int modCount;
  public Object[] __table__;
}
