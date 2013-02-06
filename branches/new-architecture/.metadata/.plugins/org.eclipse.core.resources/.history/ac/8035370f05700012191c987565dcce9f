package org.dllearner.cli.ParCEL;

import java.util.HashSet;
import java.util.Set;

import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.core.owl.Individual;


/**
 * 
 * @author An C. Tran
 *
 */
public class ParCELExtraTestingNode {
	
	protected ParCELExtraNode extraNode;
	
	protected Set<Individual> coveredPositiveExamplesTestSet = new HashSet<Individual>();
	protected Set<Individual> coveredNegativeExamplestestSet = new HashSet<Individual>(); 
	

	public ParCELExtraTestingNode(ParCELExtraNode node) {
		extraNode = node;
	}
	
	
	public ParCELExtraTestingNode(ParCELExtraNode node, Set<Individual> coveredPositiveExamplesTestSet, 
			Set<Individual> coveredNegativeExamplesTestSet) {
		this.extraNode = node;
		this.coveredPositiveExamplesTestSet.addAll(coveredPositiveExamplesTestSet);
		this.coveredNegativeExamplestestSet.addAll(coveredNegativeExamplesTestSet);
	}


	public ParCELExtraNode getExtraNode() {
		return extraNode;
	}


	public void setExtraNode(ParCELExtraNode extraNode) {
		this.extraNode = extraNode;
	}


	public Set<Individual> getCoveredPositiveExamplesTestSet() {
		return coveredPositiveExamplesTestSet;
	}


	public void setCoveredPositiveExamplesTestSet(Set<Individual> coveredPositiveExamplesTestSet) {
		this.coveredPositiveExamplesTestSet = coveredPositiveExamplesTestSet;
	}


	public Set<Individual> getCoveredNegativeExamplestestSet() {
		return coveredNegativeExamplestestSet;
	}


	public void setCoveredNegativeExamplestestSet(Set<Individual> coveredNegativeExamplestestSet) {
		this.coveredNegativeExamplestestSet = coveredNegativeExamplestestSet;
	}
	
	
	
}
