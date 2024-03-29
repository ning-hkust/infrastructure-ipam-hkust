package hk.ust.cse.Wala;

import hk.ust.cse.Wala.CallGraph.CallGraphBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

public class WalaAnalyzer {
  
  //by default, do not compute call graph at first
  public WalaAnalyzer(String appJar) throws Exception {
    // Create an object which caches IRs and related information, 
    // reconstructing them lazily on demand.
    m_irCache = new AnalysisCache();
    
    // Build an AnalysisScope which represents the set of classes to analyze. In particular,
    // we will analyze the contents of the appJar jar file and the Java standard libraries.
    m_scope = AnalysisScopeReader.makePrimordialScope(
        FileProvider.getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

    // add jar file to analysis scope
    addJarFile(appJar);
  }
  
  public void addJarFile(String appJar) throws Exception {
    JarFile jarFile = new JarFile(appJar);
    addJarFile(jarFile);
  }
  
  public void addJarFile(JarFile jarFile) throws Exception {
    m_scope.addToScope(ClassLoaderReference.Application, jarFile);
    
    if (m_jarFiles == null) {
      m_jarFiles = new ArrayList<JarFile>();
    }
    m_jarFiles.add(jarFile);
    
    // Build a class hierarchy representing all classes to analyze.
    // This step will read the class files and organize them into a tree.
    m_cha = Jar2IRUtils.getClassHierarchy(jarFile.getName(), m_scope);
  
    // Set up options which govern analysis choices.  
    // In particular, we will use all Pi nodes when building the IR.
    m_options = new AnalysisOptions(m_scope, new HashSet<Entrypoint>());
    m_options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
  }
 
  public void recomputeCallGraph(Iterable<IMethod> additionalEntryPoints, CallGraphBuilder builder) {
    // retrieve all the Main classes (classes with a 'main') as 'entry points'
    // entry points are useful to construct the call graph
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(m_scope, m_cha);
    
    // add entry points to a new set
    HashSet<Entrypoint> totalPoints = new HashSet<Entrypoint>();
    if (entrypoints != null) {
      Iterator<Entrypoint> iter = entrypoints.iterator();
      while (iter.hasNext()) {
        totalPoints.add((Entrypoint)iter.next());
      }
    }
    
    // add in additional entry points
    if (additionalEntryPoints != null) {
      Iterator<IMethod> iter = additionalEntryPoints.iterator();
      while (iter.hasNext()) {
        totalPoints.add(new DefaultEntrypoint(iter.next(), m_cha));
      }
    }
    
    // set new entry points
    m_options.setEntrypoints(totalPoints);
    
    // construct call graph
    try {
      m_callGraph = new CallGraph(m_options, m_irCache, m_cha, m_scope, builder);
    } catch (CancelException e) {e.printStackTrace(); /* should not throw */}
  }
  
  public List<JarFile> getJarFiles() {
    return m_jarFiles;
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
  
  private List<JarFile>   m_jarFiles;
  private AnalysisCache   m_irCache;
  private ClassHierarchy  m_cha;
  private AnalysisScope   m_scope;
  private AnalysisOptions m_options;
  private CallGraph       m_callGraph;
}
