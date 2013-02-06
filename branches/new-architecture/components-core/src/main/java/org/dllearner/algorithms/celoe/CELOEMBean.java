package org.dllearner.algorithms.celoe;


/**
 * Interface for a ParCELearner Bean 
 * 
 * @author An C. Tran
 *
 */
public interface CELOEMBean {

	public long getTotalDescriptions();
	public int getCurrentlyBestDescriptionLength();
	public double getCurrentlyBestAccuracy();
	public int getCurrentlyMaxExpansion();
		
}