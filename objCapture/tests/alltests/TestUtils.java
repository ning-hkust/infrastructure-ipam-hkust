package alltests;

import java.io.*;
import java.util.*;

import junit.framework.*;

import edu.mit.csail.pag.utils.Files;
import edu.mit.csail.pag.utils.Util;

public class TestUtils extends Assert {
    private TestUtils() {
        // no instances
    }

    /*
     * This is required because the gold-result file on disk is in a specific line-ending format and the actual result
     * may be in a different format. SO directly comparing them would be incorrect. So we compare line by line.
     */
    public static void assertEqualIgnoreLineBreaks(File goldResultFile, String actualResult) throws FileNotFoundException, IOException {
        assertIgnoreLineBreaks(goldResultFile, actualResult, false);
    }

    public static void assertEqualIgnoreWhiteSpaces(File goldResultFile, String actualResult) throws FileNotFoundException, IOException {
        assertIgnoreLineBreaks(goldResultFile, actualResult, true);
    }

    public static void assertIgnoreLineBreaks(File goldResultFile, String actualResult, boolean ignoreWhiteSpaces) throws FileNotFoundException, IOException {
        BufferedReader reader1 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(actualResult.getBytes())));
        BufferedReader reader2 = new BufferedReader(new FileReader(goldResultFile));
        List<String> modelLines = Files.readWhole(reader1);
        List<String> fileLines = Files.readWhole(reader2);

        assertEqualLines(modelLines, fileLines, ignoreWhiteSpaces);
        reader1.close();
        reader2.close();
    }

    private static void assertEqualLines(List<String> modelLines, List<String> fileLines, boolean ignoreWhiteSpaces) {
        if (fileLines.size() != modelLines.size()) {
            assertEquals(asBigString(fileLines), asBigString(modelLines));
            throw new IllegalStateException("should never get here");
        }
        for (int i = 0, n = modelLines.size(); i < n; i++) {
            if (ignoreWhiteSpaces) {
                String expectedLine = fileLines.get(i).trim();
                String actualLine = modelLines.get(i).trim();
                if (!Arrays.equals(expectedLine.split(" "), actualLine.split(" ")))
                    throw new ComparisonFailure("difference on line:" + i, expectedLine, actualLine);
            } else {
                assertEquals("difference on line:" + i, fileLines.get(i), modelLines.get(i));
            }
        }
    }

    public static void assertEqualAsBigString(File goldResultFile, String actualResult) throws FileNotFoundException, IOException {
        List<String> modelLines = Files.readWhole(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(actualResult.getBytes()))));
        List<String> fileLines = Files.readWhole(new BufferedReader(new FileReader(goldResultFile)));

        assertEquals(asBigString(sort(fileLines)), asBigString(sort(modelLines)));
    }

    public static List<String> sort(List<String> lines) {
        return new ArrayList<String>(new TreeSet<String>(lines));
    }

    public static String asBigString(List<String> lines) {
        StringBuilder b = new StringBuilder(16 * 1024);
        for (String line : lines) {
            b.append(line);
            b.append(Util.newLine);
        }
        return b.toString();
    }
}
