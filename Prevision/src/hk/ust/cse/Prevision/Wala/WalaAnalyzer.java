package hk.ust.cse.Prevision.Wala;

import java.util.jar.JarFile;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

public class WalaAnalyzer {
  public WalaAnalyzer(String appJar) throws Exception {
    m_jarFile = new JarFile(appJar);
    
    // Create an object which caches IRs and related information, 
    // reconstructing them lazily on demand.
    m_irCache = new AnalysisCache();
    
    // Build an AnalysisScope which represents the set of classes to analyze. In particular,
    // we will analyze the contents of the appJar jar file and the Java standard libraries.
    m_scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(m_jarFile.getName(), 
        FileProvider.getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
    
    // Build a class hierarchy representing all classes to analyze.
    // This step will read the class files and organize them into a tree.
    m_cha = Jar2IRUtils.getClassHierarchy(m_jarFile.getName(), m_scope);
    
    // retrieve all the Main classes (classes with a 'main') as 'entry points'
    // entry points are useful to construct the call graph
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(m_scope, m_cha);
    
    // Set up options which govern analysis choices.  
    // In particular, we will use all Pi nodes when building the IR.
    m_options = new AnalysisOptions(m_scope, entrypoints);
    m_options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
    
    // construct call graph
    m_callGraph = new CallGraph(m_options, m_irCache, m_cha, m_scope);
  }
  
  public JarFile getJarFile() {
    return m_jarFile;
  }
  
  public AnalysisCache getAnalysisCache() {
    return m_irCache;
  }
  
  public ClassHierarchy getClassHierarchy() {
    return m_cha;
  }
  
  public AnalysisScope getAnalysisScope() {
    return m_scope;
  }
  
  public AnalysisOptions getAnalysisOptions() {
    return m_options;
  }
  
  public CallGraph getCallGraph() {
    return m_callGraph;
  }
  
  private final JarFile         m_jarFile;
  private final AnalysisCache   m_irCache;
  private final ClassHierarchy  m_cha;
  private final AnalysisScope   m_scope;
  private final AnalysisOptions m_options;
  private final CallGraph       m_callGraph;
}
