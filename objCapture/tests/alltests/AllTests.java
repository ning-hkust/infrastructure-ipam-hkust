package alltests;

import postCreationTests.AllPostCreationTests;
import junit.framework.*;
import junit.textui.*;

public class AllTests {

    public static Test suite() {
        TestSuite suite = new TestSuite("Test for objCapture project");
        // $JUnit-BEGIN$
        suite.addTest(AllPostCreationTests.suite());
        // $JUnit-END$
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(AllTests.suite());
    }
}
