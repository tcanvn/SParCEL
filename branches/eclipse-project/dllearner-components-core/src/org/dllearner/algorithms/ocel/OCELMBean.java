package org.dllearner.algorithms.ocel;

public interface OCELMBean {

	public long getTotalDescriptions();
	public int getCurrentlyBestDescriptionLength();
	public double getCurrentlyBestAccuracy();
	public int getCurrentlyMaxExpansion();
		
}