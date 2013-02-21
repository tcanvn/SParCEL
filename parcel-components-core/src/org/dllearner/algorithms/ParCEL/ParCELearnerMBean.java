package org.dllearner.algorithms.ParCEL;


/**
 * Interface for a ParCELearner Bean 
 * 
 * @author An C. Tran
 *
 */
public interface ParCELearnerMBean {

	public long getTotalDescriptions();
	public int getCurrentlyBestDescriptionLength();
	public double getCurrentlyBestAccuracy();
	public int getWorkerPoolSize();
	public int getSearchTreeSize();
	public int getCurrentlyMaxExpansion();
}
