package hk.ust.cse.AbstractInterpretation.Lattices;

public class Lattice2 extends AbstractLattice{
  public static final int BOT    = 0; /* use as error */
  public static final int ZERO   = 1;
  public static final int ONE    = 2;
  public static final int TWO    = 3;
  public static final int THREE  = 4;
  public static final int FOUR   = 5;
  public static final int TOP    = 6; /* use as unknown */
  
  // define the lowest upper bound
  private static int[][] s_join_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    ZERO,   ONE,    TWO,    THREE,   FOUR,   TOP},
   /*ZERO*/ new int[]{ZERO,   ZERO,   TOP,    TOP,    TOP,     TOP,    TOP},
    /*ONE*/ new int[]{ONE,    TOP,    ONE,    TOP,    TOP,     TOP,    TOP},
    /*TWO*/ new int[]{TWO,    TOP,    TOP,    TWO,    TOP,     TOP,    TOP},
  /*THREE*/ new int[]{THREE,  TOP,    TOP,    TOP,    THREE,   TOP,    TOP},
   /*FOUR*/ new int[]{FOUR,   TOP,    TOP,    TOP,    TOP,     FOUR,   TOP},
    /*TOP*/ new int[]{TOP,    TOP,    TOP,    TOP,    TOP,     TOP,    TOP},
  };
  
  // define the greatest lower bound
  private static int[][] s_meet_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     BOT,    BOT},
   /*ZERO*/ new int[]{BOT,    ZERO,   BOT,    BOT,    BOT,     BOT,    ZERO},
    /*ONE*/ new int[]{BOT,    BOT,    ONE,    BOT,    BOT,     BOT,    ONE},
    /*TWO*/ new int[]{BOT,    BOT,    BOT,    TWO,    BOT,     BOT,    TWO},
  /*THREE*/ new int[]{BOT,    BOT,    BOT,    BOT,    THREE,   BOT,    THREE},
   /*FOUR*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     FOUR,   FOUR},
    /*TOP*/ new int[]{BOT,    ZERO,   ONE,    TWO,    THREE,   FOUR,   TOP},
  };
  
  // define the lattice hierarchy
  private static boolean[][] s_leq_table = new boolean[][]{ 
                       /* BOT     ZERO     ONE     TWO     THREE    FOUR     TOP */  
    /*BOT*/ new boolean[]{true,   true,    true,   true,   true,    true,    true},
   /*ZERO*/ new boolean[]{false,  true,    false,  false,  false,   false,   true},
    /*ONE*/ new boolean[]{false,  false,   true,   false,  false,   false,   true},
    /*TWO*/ new boolean[]{false,  false,   false,  true,   false,   false,   true},
  /*THREE*/ new boolean[]{false,  false,   false,  false,  true,    false,   true},
   /*FOUR*/ new boolean[]{false,  false,   false,  false,  false,   true,    true},
    /*TOP*/ new boolean[]{false,  false,   false,  false,  false,   false,   true},
  };

  // define plus
  private static int[][] s_func_plus_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     BOT,    BOT},
   /*ZERO*/ new int[]{BOT,    ZERO,   ONE,    TWO,    THREE,   FOUR,   TOP},
    /*ONE*/ new int[]{BOT,    ONE,    TWO,    THREE,  FOUR,    ZERO,   TOP},
    /*TWO*/ new int[]{BOT,    TWO,    THREE,  FOUR,   ZERO,    ONE,    TOP},
  /*THREE*/ new int[]{BOT,    THREE,  FOUR,   ZERO,   ONE,     TWO,    TOP},
   /*FOUR*/ new int[]{BOT,    FOUR,   ZERO,   ONE,    TWO,     THREE,  TOP},
    /*TOP*/ new int[]{BOT,    TOP,    TOP,    TOP,    TOP,     TOP,    TOP},
  };
  
  // define minus
  private static int[][] s_func_minus_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     BOT,    BOT},
   /*ZERO*/ new int[]{BOT,    ZERO,   FOUR,   THREE,  TWO,     ONE,    TOP},
    /*ONE*/ new int[]{BOT,    ONE,    ZERO,   FOUR,   THREE,   TWO,    TOP},
    /*TWO*/ new int[]{BOT,    TWO,    ONE,    ZERO,   FOUR,    THREE,  TOP},
  /*THREE*/ new int[]{BOT,    THREE,  TWO,    ONE,    ZERO,    FOUR,   TOP},
   /*FOUR*/ new int[]{BOT,    FOUR,   THREE,  TWO,    ONE,     ZERO,   TOP},
    /*TOP*/ new int[]{BOT,    TOP,    TOP,    TOP,    TOP,     TOP,    TOP},
  };
  
  // define multiply
  private static int[][] s_func_multiply_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     BOT,    BOT},
   /*ZERO*/ new int[]{BOT,    ZERO,   ZERO,   ZERO,   ZERO,    ZERO,   ZERO},
    /*ONE*/ new int[]{BOT,    ZERO,   ONE,    TWO,    THREE,   FOUR,   TOP},
    /*TWO*/ new int[]{BOT,    ZERO,   TWO,    FOUR,   ONE,     THREE,  TOP},
  /*THREE*/ new int[]{BOT,    ZERO,   THREE,  ONE,    FOUR,    TWO,    TOP},
   /*FOUR*/ new int[]{BOT,    ZERO,   FOUR,   THREE,  TWO,     ONE,    TOP},
    /*TOP*/ new int[]{BOT,    ZERO,   TOP,    TOP,    TOP,     TOP,    TOP},
  };
  
  // define divide
  private static int[][] s_func_divide_table = new int[][]{ 
                   /* BOT     ZERO    ONE     TWO     THREE    FOUR    TOP */  
    /*BOT*/ new int[]{BOT,    BOT,    BOT,    BOT,    BOT,     BOT,    BOT},
   /*ZERO*/ new int[]{BOT,    BOT,    ZERO,   ZERO,   ZERO,    ZERO,   ZERO},
    /*ONE*/ new int[]{BOT,    BOT,    TOP,    TOP,    TOP,     TOP,    TOP},
    /*TWO*/ new int[]{BOT,    BOT,    TOP,    TOP,    TOP,     TOP,    TOP},
  /*THREE*/ new int[]{BOT,    BOT,    TOP,    TOP,    TOP,     TOP,    TOP},
   /*FOUR*/ new int[]{BOT,    BOT,    TOP,    TOP,    TOP,     TOP,    TOP},
    /*TOP*/ new int[]{BOT,    BOT,    TOP,    TOP,    TOP,     TOP,    TOP},
  };
  
  // initialize
  {
    s_tables.put("join", s_join_table);
    s_tables.put("meet", s_meet_table);
    s_tables.put("leq", s_leq_table);
    
    s_tables.put("+", s_func_plus_table);
    s_tables.put("-", s_func_minus_table);
    s_tables.put("*", s_func_multiply_table);
    s_tables.put("/", s_func_divide_table);
    
    // the desired property
    s_desired_property = TWO;
  }
}
