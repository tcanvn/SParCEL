package org.dllearner.algorithms.ParCELEx;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Intersection;
import org.dllearner.utilities.owl.ConceptComparator;


/**
 * This class contains some utility functions for ParCELEx algorithm such as group the partial definitions, 
 * 	calculate intersection between a description and a set of counter partial definitions, etc.  
 *  
 * @author An C. Tran
 *
 */
public class ParCELExUtilities {

	/**
	 * Group the counter partial definitions in a partial definition using ConceptComparator
	 * so that the "relevant" counter partial definitions will ordered near each other for readability
	 * 
	 * @param definition A definition (description)
	 * 
	 * @return Description with the member counter partial definitions are grouped 
	 */
	public static Description groupDefinition(Description definition) {
		
		List<Description> children = definition.getChildren();
		
		List<Description> counterPartialDefinitions = new LinkedList<Description>();
		List<Description> partialDefinitions = new LinkedList<Description>();
		for (Description def : children) {
			if (def.toString().toLowerCase().contains("not "))
				counterPartialDefinitions.add(def);
			else
				partialDefinitions.add(def);
		}
		
		Collections.sort(counterPartialDefinitions, new ConceptComparator());
		Collections.sort(partialDefinitions, new ConceptComparator());
		
		for (Description cpd : counterPartialDefinitions) 
			partialDefinitions.add(cpd);
		
		Description result = new Intersection(partialDefinitions);
		
		return result;
	}
	
	
	/**
	 * Create an Intersection object given a description and a set of descriptions
	 *  
	 * @param description A description
	 * @param counterDefinitions Set of descriptions
	 * 
	 * @return An Intersection object of the given description and the set of descriptions
	 */
	public static Description createIntersection(Description description, Set<ParCELExtraNode> counterDefinitions, boolean setUsed) {
		LinkedList<Description> descriptionList = new LinkedList<Description>();
		
		descriptionList.add(description);
		for (ParCELExtraNode node : counterDefinitions) {
			descriptionList.add(node.getDescription());
			if (setUsed)
				node.setType(ParCELExNodeTypes.COUNTER_PARTIAL_DEFINITION_USED);
		}
		
		return new Intersection(descriptionList);
	}

}
