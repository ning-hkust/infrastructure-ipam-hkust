package edu.mit.csail.pag.utils;

public class StringExt {
    static public String getStringBetweenTwoStrings(String line, String startMarker, String endMarker) {
        int startMarkerIndex = line.indexOf(startMarker);
        if (startMarkerIndex == -1)
            throw new IllegalArgumentException("String does not contain start marker. String: " + line + " Start Marker:" + startMarker);
        int endMarkerIndex = line.indexOf(endMarker);
        if (endMarkerIndex == -1)
            throw new IllegalArgumentException("String does not contain end marker. String: " + line + " End Marker:" + endMarker);
        return line.substring(startMarkerIndex + startMarker.length(), endMarkerIndex);
    }

    static public String getStringFromStringToEnd(String line, String startMarker) {
        int startMarkerIndex = line.indexOf(startMarker);
        if (startMarkerIndex == -1)
            throw new IllegalArgumentException("String does not contain start marker. String: " + line + " Start Marker:" + startMarker);
        return line.substring(startMarkerIndex + startMarker.length());
    }

}
