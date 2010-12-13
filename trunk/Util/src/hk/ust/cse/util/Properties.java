package hk.ust.cse.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Properties {

  public Properties(String propFile) throws IOException, FileNotFoundException {
    m_properties = new java.util.Properties();
    m_properties.load(new FileInputStream(propFile));
  }
  
  public String readKey(String key) {
    return (String)m_properties.get(key);
  }
  
  private final java.util.Properties m_properties;
}
