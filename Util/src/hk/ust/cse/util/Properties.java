package hk.ust.cse.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Properties {

  /**
   * @param propFile: properties file path or properties file name in classpath
   */
  public Properties(String propFile) throws IOException {
    m_properties = new java.util.Properties();
    
    // try to find properties file 
    InputStream in = null;
    try {
      in = new FileInputStream(propFile);
    } catch (FileNotFoundException e) {
      in = this.getClass().getClassLoader().getResourceAsStream(propFile);
    }
    
    // load properties file
    if (in != null) {
      m_properties.load(in);
    }
    else {
      throw new FileNotFoundException("properties file: " + propFile + " not found!");
    }
  }
  
  public String readKey(String key) {
    return (String)m_properties.get(key);
  }
  
  private final java.util.Properties m_properties;
}
