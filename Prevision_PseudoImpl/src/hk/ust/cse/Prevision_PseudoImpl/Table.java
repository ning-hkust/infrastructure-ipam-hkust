package hk.ust.cse.Prevision_PseudoImpl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;


public class Table {

  public Table(int paramInt, float paramFloat) {
    count    = 0;
    modCount = 0;

    if (paramInt < 0)
      throw new IllegalArgumentException();

    if (paramFloat <= 0F)
      throw new IllegalArgumentException();
  }
  
  public Object put(HashCode key, Object value) {
    if (key != null && value != null && count >= 0 && key.__hashcode__ == count) {
      __table__ = new Object[count + 1];
      __table__[key.__hashcode__] = value;
      count++;
      return null; /* does not consider override */
    }
    else {
      count = -100; // make them never choose this path
      return null;
    }
  }
  
  public Object remove(HashCode key) {
    if (key != null && key.__hashcode__ == -1) {
      return null;
    }
    else if (key != null && count >= 0 && key.__hashcode__ == count - 1) {
      Object o = __table__[key.__hashcode__];
      __table__[key.__hashcode__] = null;
      __table__ = new Object[count - 1];
      count--;
      return o;
    }
    else {
      count = -100; // make them never choose this path
      return null;
    }
  }
  
  public Object get(HashCode key) {
    if (key != null && count >= 0) {
      if (key.__hashcode__ < 0) {
        return null;
      }
      else {
        if (key.__hashcode__ < count) {
          return __table__[key.__hashcode__];
        }
        else {
          return null;
        }
      }
    }
    else {
      count = -100; // make them never choose this path
      return null;
    }
  }

  public boolean containsKey(HashCode hashCode) {
    return hashCode.__hashcode__ >= 0 && hashCode.__hashcode__ < count && count == __table__.length;
  }
  
  public KeySet keySet() {
    return new KeySet(count);
  }
  
  public KeyEnumerator keys() {
    return new KeyEnumerator(count);
  }
  
  public EntrySet entrySet() {
    return new EntrySet(__table__, count);
  }

  public Collection values() {
    ArrayList l = new ArrayList();
    for (int i = 0; i < __table__.length; i++) {
      l.add(__table__[i]);
    }
    return l;
  }
  
  public static class EntrySet {

    public EntrySet(Object[] entries, int size) {
      if (size >= 0) {
        this.size = size;
        this.entries = entries;
      }
      else {
        throw new IllegalArgumentException();
      }
    }
    
    public EntryIterator iterator() {
      return new EntryIterator((Object[]) entries, size);
    }
    
    public int size;
    public Object[] entries;
  }
  
  public static class EntryIterator {
    public EntryIterator(Object[] entries, int size) {
      this.size = size;
      this.current = 0;
      this.entries = entries;
    }
    
    public boolean hasNext() {
      return current < size;
    }
    
    public java.util.Map.Entry next() {
      if (current < size) {
        return new AbstractMap.SimpleEntry(new HashCode(current), entries[current++]);
      }
      else {
        throw new NoSuchElementException();
      }
    }
    
    public int size;
    public int current;
    public Object[] entries;
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
  
  public static class KeyEnumerator {
    public KeyEnumerator(int size) {
      this.size = size;
      this.current = 0;
    }
    
    public boolean hasMoreElements() {
      return current < size;
    }
    
    public HashCode nextElement() {
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

  public int count;
  public int modCount;
  public Object[] __table__;
}
