package hk.ust.cse.Prevision.TestCases;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class TestSuite {
  
  public List<String> getTests() {
    List<String> testNames = new ArrayList<String>();
    for (Method method : this.getClass().getMethods()) {
      String methodName = method.getName();
      if (methodName.matches("^test[0-9]+$")) {
        testNames.add(methodName);
      }
    }
    return testNames;
  }
}
