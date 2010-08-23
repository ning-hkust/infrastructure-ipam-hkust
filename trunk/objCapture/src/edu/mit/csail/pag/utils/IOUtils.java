package edu.mit.csail.pag.utils;

import java.io.*;

public class IOUtils {

    static public StringBuffer bufferedReaderToStringBuffer(BufferedReader in) {
        StringBuffer result = new StringBuffer();
        String inputLine;
        try {
            while ((inputLine = in.readLine()) != null) {
                result.append(inputLine + "\r\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading StringReader", e);
        }
        return result;
    }

    public static StringBuffer inputStreamToStringBuffer(InputStream stream) {
        InputStreamReader isr;
        isr = new InputStreamReader(stream);
        return bufferedReaderToStringBuffer(new BufferedReader(isr));
    }

    public static void writeToFile(String path, String s) throws IOException {
        FileOutputStream fileoutputstream = new FileOutputStream(path);
        DataOutputStream dataoutputstream = new DataOutputStream(fileoutputstream);
        dataoutputstream.writeBytes(s);
        dataoutputstream.flush();
        dataoutputstream.close();
    }

    public static int indexOfIth(String s, char c, int ith) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c)
                ith--;
            if (ith == 0)
                return i;
        }
        return -1;
    }
}
