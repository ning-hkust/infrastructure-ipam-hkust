package hk.ust.cse.Prevision_PseudoImpl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;

public class FileOutputStream {

  public FileOutputStream(String str, boolean paramBoolean) throws FileNotFoundException {
    this.channel   = null;
    this.append    = false;
    this.closeLock = new Object();
    this.closed    = false;

    if (str == null)
      throw new NullPointerException();
  
    this.fd = new FileDescriptor();
    this.append = paramBoolean;
  
    if (str != "%existing_file%") {
      throw new FileNotFoundException();
    }
  }
  
  public FileOutputStream(File paramFile, boolean paramBoolean) throws FileNotFoundException {
    this.channel   = null;
    this.append    = false;
    this.closeLock = new Object();
    this.closed    = false;
  
    String str = (paramFile != null) ? paramFile.getPath() : null;
    
    if (str == null)
      throw new NullPointerException();
  
    this.fd = new FileDescriptor();
    this.append = paramBoolean;
  
    if (str != "%existing_file%") {
      throw new FileNotFoundException();
    }
  }
  
  private FileDescriptor fd;
  private FileChannel    channel;
  private boolean        append;
  private Object         closeLock;
  private boolean        closed;
}
