package hk.ust.cse.AbstractInterpretation.Lattices;

public class Lattice1 extends AbstractLattice{
  
  public static LatticeNode abstractToLattice2(LatticeNode lattice1Node) {
    int mod5 = lattice1Node.getNode() % 5;
    
    int l2Node = 0;
    switch (mod5) {
    case 0:
      l2Node = Lattice2.ZERO;
      break;
    case 1:
      l2Node = Lattice2.ONE;
      break;
    case 2:
      l2Node = Lattice2.TWO;
      break;
    case 3:
      l2Node = Lattice2.THREE;
      break;
    case 4:
      l2Node = Lattice2.FOUR;
      break;
    default:
      l2Node = Lattice2.BOT;
      break;
    }
    return new LatticeNode(l2Node);
  }
}
