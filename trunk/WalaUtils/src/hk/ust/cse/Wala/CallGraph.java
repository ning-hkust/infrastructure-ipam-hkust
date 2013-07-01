package hk.ust.cse.Wala;

import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;

public class CallGraph {
  // http://wala.sourceforge.net/wiki/index.php/UserGuide:PointerAnalysis
  public enum CallGraphBuilder {ZeroCFA, ZeroOneCFA, VanillaZeroOneCFA, 
    ZeroContainerCFA, ZeroOneContainerCFA, VanillaZeroOneContainerCFA}
  
  public CallGraph(AnalysisOptions options, AnalysisCache cache, 
      IClassHierarchy cha, AnalysisScope scope, CallGraphBuilder builder) throws IllegalArgumentException, CancelException {
    
    // create a call graph builder (use the ZeroOneCFA pointer analysis policy)
    SSAPropagationCallGraphBuilder callGraphBuilder = null;
    switch (builder) {
    case ZeroCFA:
      callGraphBuilder = Util.makeZeroCFABuilder(options, cache, cha, scope);
      break;
    case ZeroOneCFA:
      callGraphBuilder = Util.makeZeroOneCFABuilder(options, cache, cha, scope);
      break;
    case VanillaZeroOneCFA:
      callGraphBuilder = Util.makeVanillaZeroOneCFABuilder(options, cache, cha, scope);
      break;
    case ZeroContainerCFA:
      callGraphBuilder = Util.makeZeroContainerCFABuilder(options, cache, cha, scope);
      break;
    case ZeroOneContainerCFA:
      callGraphBuilder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);
      break;
    case VanillaZeroOneContainerCFA:
      callGraphBuilder = Util.makeVanillaZeroOneContainerCFABuilder(options, cache, cha, scope);
      break;
    default:
      callGraphBuilder = Util.makeZeroOneCFABuilder(options, cache, cha, scope);
      break;
    }

    // build call graph
    callGraphBuilder.makeCallGraph(options);
    
    // get constructed call graph and the pointer analysis information 
    // computed as a side-effect of call graph construction
    m_callGraph       = callGraphBuilder.getCallGraph();
    //System.err.println(CallGraphStats.getStats(m_callGraph));
    
    // not used at the moment
    //m_pointerAnalysis = callGraphBuilder.getPointerAnalysis();
    
    // not used at the moment
    //m_heapModel       = m_pointerAnalysis.getHeapModel();
    //m_heapGraph       = m_pointerAnalysis.getHeapGraph();
    //m_ctxSelector     = callGraphBuilder.getContextSelector();
  }
  
  public CGNode[] getDispatchTargets(CGNode caller, CallSiteReference site) {
    CGNode[] dispatchTargets = null;
    
    if (caller != null && site != null) {
      // get all possible targets at call site
      Set<CGNode> targetNodes = m_callGraph.getPossibleTargets(caller, site);
      
      // fill array
      dispatchTargets = new CGNode[targetNodes.size()];
      Iterator<CGNode> iter = targetNodes.iterator();
      for (int i = 0; iter.hasNext(); i++) {
        CGNode cgNode = (CGNode) iter.next();
        dispatchTargets[i] = cgNode;
      }
    }
    return dispatchTargets;
  }
  
  public CGNode getNode(IMethod m) {
    // get the exact method cgnode
    return m_callGraph.getNode(m, Everywhere.EVERYWHERE);
  }
  
  private final ExplicitCallGraph m_callGraph;
  //private final PointerAnalysis   m_pointerAnalysis;
  //private final HeapModel         m_heapModel;
  //private final HeapGraph         m_heapGraph;
  //private final ContextSelector   m_ctxSelector;
}
