package postCreationTests;

import junit.framework.*;

public class AllPostCreationTests {

    public static Test suite() {
        TestSuite suite = new TestSuite("Post Creation Tests");
        // $JUnit-BEGIN$
        suite.addTestSuite(WrapperTests.class);
        // $JUnit-END$
        return suite;
    }

}
