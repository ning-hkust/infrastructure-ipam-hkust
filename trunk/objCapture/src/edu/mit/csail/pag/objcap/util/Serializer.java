package edu.mit.csail.pag.objcap.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.thoughtworks.xstream.XStream;

public class Serializer {
	// Use XStream to serialize the stream
	static XStream xstream = new XStream();

	//JJ
	static public Object loadObjectFromFile(String fileName){
		Object obj = null;
		try {
      Reader outt = new FileReader(new File(fileName));
      obj = Serializer.loadObject(outt);
      outt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (obj == null)
			throw new IllegalArgumentException("fileName is not correct.");
		
		return obj;
	}
	
	static public Object loadObject(Reader reader) {
		return xstream.fromXML(reader);
	}
	
	static public Object loadObject(InputStream iStream) {
		return xstream.fromXML(iStream);
	}

	static public Object loadObject(String xmlString) {
		return xstream.fromXML(xmlString);
	}

	public static void storeObject(Object object, Writer out) {
		xstream.toXML(object, out);
	}

	public static void storeObject(Object object, OutputStream out) {
		xstream.toXML(object, out);
	}
	public static String toXMLString(Object object) {
		if (object == null) {
			return null;
		}
		return xstream.toXML(object);
	}

	public static byte[] toXMLZippedByteArray(Object object) throws IOException {
		if (object == null) {
			return null;
		}

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		GZIPOutputStream gzOut = new GZIPOutputStream(bout);

		xstream.toXML(object, gzOut);
		gzOut.close();
		bout.close();
		return bout.toByteArray();
	}

	public static Object loadObject(byte[] gzippedByteArray) throws IOException {
		if (gzippedByteArray == null) {
			return null;
		}
		ByteArrayInputStream bin = new ByteArrayInputStream(gzippedByteArray);
		GZIPInputStream gzIn = new GZIPInputStream(bin);

		Object toRet = xstream.fromXML(gzIn);

		gzIn.close();
		bin.close();

		return toRet;
	}
}
