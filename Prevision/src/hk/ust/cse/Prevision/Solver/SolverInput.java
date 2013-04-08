package hk.ust.cse.Prevision.Solver;

public abstract class SolverInput {

  public SolverInput(NeutralInput neutralInput) {
    m_neutralInput = neutralInput;
  }

  public NeutralInput getNeutralInput() {
    return m_neutralInput;
  }
  
  public String toString() {
    return m_neutralInput.toString();
  }
  
  protected final NeutralInput m_neutralInput;
}
