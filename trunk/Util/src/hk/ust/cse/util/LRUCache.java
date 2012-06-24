package hk.ust.cse.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LRUCache {

  public LRUCache(int maxCache) {
    m_maxCache     = maxCache;
    m_usedSequence = new ArrayList<Object>();
    m_cache        = new HashMap<Object, Object>();
  }
  
  public void put(Object key, Object value) {
    Object cached = m_cache.get(key);
    if (cached == null) {
      if (m_cache.size() >= m_maxCache) {
        m_cache.remove(m_usedSequence.get(0));
        m_usedSequence.remove(0);
      }
      m_usedSequence.add(key);
      m_cache.put(key, value);
    }
    else {
      m_cache.put(key, value);
      m_usedSequence.remove(key);
      m_usedSequence.add(key);
    }
  }
  
  public Object find(Object key) {
    Object cached = m_cache.get(key);
    if (cached != null) {
      m_usedSequence.remove(key);
      m_usedSequence.add(key);
    }
    return cached;
  }
  
  private final int                     m_maxCache;
  private final List<Object>            m_usedSequence;
  private final HashMap<Object, Object> m_cache;
}
