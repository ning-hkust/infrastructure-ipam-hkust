package randoop;

import java.util.Set;

import randoop.ocat.OCATGlobals;
import randoop.util.Log;
import randoop.util.WeightedElement;

public class StatementWithWeight implements WeightedElement {

	private static final long serialVersionUID = -2826065596147048132L;

	private StatsForMethod stat4method;

	private long maxSelected=0;

	private Set<StatementKind> calles;

	private SequenceGeneratorStats stats;
	
	private boolean bMethod = false;
	private boolean bConstructor = false;
	private double classCoverage = 0.0;
	private double ratioNumInstance = 0.0;


	public StatementKind getStatement() {
		return stat4method.getStatement();
	}

	public StatementWithWeight(StatementKind statement,
			SequenceGeneratorStats stats, 
			Set<StatementKind> calles, ObjectCache objectCache) {
		this.stat4method = stats.getStatsForStatement(statement);
		this.maxSelected = stats.getGlobalStats().getCount(SequenceGeneratorStats.STAT_NOT_DISCARDED_MAX);
		this.calles = calles;
		this.stats = stats;
		
		String clsName="";
		if (statement instanceof RMethod)
		{
			clsName = ((RMethod)statement).getMethod().getDeclaringClass().getName();
			bMethod = true;
		}
		else if(statement instanceof RConstructor)
		{
			clsName = ((RConstructor)statement).getConstructor().getDeclaringClass().getName();
			bConstructor = true;
		}
		assert(clsName != "");		
		this.classCoverage = stats.getClassCoverage(clsName);
		//assert(classCoverage > 0);
		
		int numInstance = objectCache.getClassNumInstances(clsName);
		long numInstanceMax = objectCache.getMaxInstanceNum();
		
		if (numInstance == 0 || numInstanceMax == 0)
			ratioNumInstance = 1;
		else
			ratioNumInstance = numInstance / numInstanceMax;
	}

	private long getCalleesBranchTot() {
		long ret = 0;
		if (calles != null)
			for (StatementKind sk : calles) {
				StatsForMethod sfm = stats.getStatsForStatement(sk);
				ret = ret + sfm.getCount(SequenceGeneratorStats.STAT_BRANCHTOT);
			}

		return ret;
	}

	private long getCalleesBranchCov() {
		long ret = 0;
		if (calles != null)
			for (StatementKind sk : calles) {
				StatsForMethod sfm = stats.getStatsForStatement(sk);
				ret = ret + sfm.getCount(SequenceGeneratorStats.STAT_BRANCHCOV);
			}

		return ret;

	}

	// @Override
	public double getWeight() {
		double ret = 1;
		if (bMethod) ret=getWeightMethod();
		else if (bConstructor) ret=getWeightMethod();//ret=getWeightConstructor();
		
		return ret;
	}
	
	//Constructor Selection =  alpha * (1/class coverage) 
	//+ beta * the max number of all class instances/the number of the class instances.
	public double getWeightConstructor() {
		int alpha = 1, beta = 1;
		double ret = 1;
		
		if (classCoverage==0)
			ret =  0.1;//beta * (1/ratioNumInstance);
		else
			ret = alpha * (1/classCoverage);// + beta * (1/ratioNumInstance);
		
		return ret;
		//return 1;
	}
	
	//Method Selection = seta * class coverage + gamma * method coverage + zeta * maxselected / STAT_NOT_DISCARDED
	public double getWeightMethod() {
		long sel = stat4method.getCount(SequenceGeneratorStats.STAT_NOT_DISCARDED);
		long cov = stat4method.getCount(SequenceGeneratorStats.STAT_BRANCHCOV) + getCalleesBranchCov();
		long tot = stat4method.getCount(SequenceGeneratorStats.STAT_BRANCHTOT) + getCalleesBranchTot();

		double methodCoverage = 0;
		if (tot != 0)
			methodCoverage = cov/tot;

		double selectionRatio = 0;
		if (maxSelected != 0)
			selectionRatio = sel/maxSelected;
		
		double part1 = 0, part2 = 0, part3 = 0;

		double seta=OCATGlobals.cov_class_weight, gamma=OCATGlobals.cov_method_weight, zeta=OCATGlobals.sel_method_weight;
		
		if (classCoverage != 0)
			part1 = seta * 1/ classCoverage;
		
		if (methodCoverage != 0)
			part2 = gamma * 1 / methodCoverage;
		
		if (selectionRatio != 0)
			part3 = zeta * 1/selectionRatio;
		
		double ret = part1 + part2 + part3;
		
		if (ret == 0) ret = 1;
		return ret;
	}	

	public boolean printUncoveredMethods() {
		boolean ret = false;
		long sel = stat4method.getCount(SequenceGeneratorStats.STAT_SELECTED);
		long cov = stat4method.getCount(SequenceGeneratorStats.STAT_BRANCHCOV);
		long tot = stat4method.getCount(SequenceGeneratorStats.STAT_BRANCHTOT);

		long tot_c = getCalleesBranchTot();
		long cov_c = getCalleesBranchCov();
		if (cov != tot) {
			System.out.println(String.format("%4d/%4d = %2.1f, %4d, %4d", cov
					+ cov_c, tot + tot_c, (cov + cov_c) * 100
					/ (double) (tot + tot_c), cov_c, tot_c)
					+ " , " + stat4method.getStatement().toString());

			ret = true;

		}

		return ret;

	}
}
