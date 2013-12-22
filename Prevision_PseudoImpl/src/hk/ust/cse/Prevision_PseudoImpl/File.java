package hk.ust.cse.Prevision_PseudoImpl;

public class File {

  public File(java.io.File parentFile, String name) {
    if (name == "./") {
      __state__dir__       = 1;
      __state__exist__     = 1;
      __prop__parentFile__ = parentFile;
      __prop__name__       = name;
    }
    else if (name == "./does_not_exist") {
      __state__dir__       = 0;
      __state__exist__     = 0;
      __prop__parentFile__ = parentFile;
      __prop__name__       = name;
    }
    else {
      __state__dir__       = 2;
      __state__exist__     = 2;
      __prop__parentFile__ = parentFile;
      __prop__name__       = name;
    }
  }
  
  public boolean isDirectory() {
    return __state__exist__ == 1 && __state__dir__ == 1;
  }
  
  public java.io.File[] listFiles() {
    if (__state__exist__ == 0 || (__state__exist__ == 1 && __state__dir__ == 0)) {
      return null;
    }
    else {
      return new java.io.File[0];
    }
  }
  
  public String getName() {
    return __prop__name__;
  }
  
  public java.io.File getParentFile() {
    return __prop__parentFile__;
  }
  
  public int __state__dir__; /* 0: false, 1: true, 2: unknown */
  public int __state__exist__; /* 0: false, 1: true, 2: unknown */
  public String __prop__name__;
  public java.io.File __prop__parentFile__;
}
