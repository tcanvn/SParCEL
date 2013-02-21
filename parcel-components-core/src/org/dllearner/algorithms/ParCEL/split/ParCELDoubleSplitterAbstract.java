package org.dllearner.algorithms.ParCEL.split;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Individual;

/**
 * This is the interface for data type property splitting strategies in ParCEL: Given a set of
 * positive and negative examples and a reasoner with the ontology loaded, calculate the splits for
 * data type properties in the ontology
 * 
 * @author An C. Tran
 * 
 */

public interface ParCELDoubleSplitterAbstract {

	  
	public void init() throws ComponentInitException;
	

	/**
	 * Calculate the split
	 * 
	 * @return A list of mapped data type properties and their splits
	 */
	public Map<DatatypeProperty, List<Double>> computeSplits();

	
	/**
	 * Get the reasoner used by the splitter
	 * 
	 * @return The reasoner
	 */
	public AbstractReasonerComponent getReasoner();

	
	/**
	 * Set the reasoner for the splitter
	 * 
	 * @param reasoner
	 */
	public void setReasoner(AbstractReasonerComponent reasoner);

	
	/**
	 * Get the positive examples used by the splitter
	 * 
	 * @return Set of positive examples
	 */
	public Set<Individual> getPositiveExamples();

	
	/**
	 * Set the positive examples will be used to calculate the splits
	 * 
	 * @param positiveExamples
	 */
	public void setPositiveExamples(Set<Individual> positiveExamples);

	
	/**
	 * Get the positive examples used by the splitter
	 * 
	 * @return Set of negative examples
	 */
	public Set<Individual> getNegativeExamples();

	
	/**
	 * Assign the negative example set
	 * 
	 * @param negativeExamples
	 */
	public void setNegativeExamples(Set<Individual> negativeExamples);

}
