package alltests;


import edu.mit.csail.pag.objcap.ObjCapture;
import edu.mit.csail.pag.randoopHelper.ObjCaptureWrapperCreator;

public abstract class TestGlobals{
   
    public static void setVerbose(){
    	ObjCaptureWrapperCreator.verbose = true;
    }

}
