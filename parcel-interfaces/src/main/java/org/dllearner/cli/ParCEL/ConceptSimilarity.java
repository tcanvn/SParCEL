package org.dllearner.cli.ParCEL;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.owl.CardinalityRestriction;
import org.dllearner.core.owl.DatatypeExactCardinalityRestriction;
import org.dllearner.core.owl.DatatypeMaxCardinalityRestriction;
import org.dllearner.core.owl.DatatypeMinCardinalityRestriction;
import org.dllearner.core.owl.ValueRestriction;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Intersection;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Negation;
import org.dllearner.core.owl.Nothing;
import org.dllearner.core.owl.ObjectAllRestriction;
import org.dllearner.core.owl.ObjectCardinalityRestriction;
import org.dllearner.core.owl.ObjectExactCardinalityRestriction;
import org.dllearner.core.owl.ObjectMaxCardinalityRestriction;
import org.dllearner.core.owl.ObjectMinCardinalityRestriction;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.ObjectSomeRestriction;
import org.dllearner.core.owl.ObjectValueRestriction;
import org.dllearner.core.owl.PropertyExpression;
import org.dllearner.core.owl.Restriction;
import org.dllearner.core.owl.Thing;
import org.dllearner.core.owl.Union;
import org.dllearner.kb.OWLFile;
import org.dllearner.utilities.owl.OWLAPIConverter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.owl.owlapi.OWLClassAssertionImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLIndividualAxiomImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLIndividualImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectComplementOfImpl;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;



/**
 * This class implements methods for calculating the concept similarity.
 *  
 * @author An C. Tran
 *
 */
public class ConceptSimilarity { 

	private AbstractReasonerComponent reasoner;
	private Set<Individual> instances;
	
	private static Logger logger = Logger.getLogger(ConceptSimilarity.class);
	
	public ConceptSimilarity() {
		this.reasoner = null;
	}
	
	
	
	public static double getConceptOverlapSimple(Set<Individual> coverC, Set<Individual> coverD) {
		
		//compute the intersection between C and D
		Set<Individual> intersection = new HashSet<Individual>();
		intersection.addAll(coverC);
		intersection.retainAll(coverD);
		
		int commonInstances = intersection.size();
		int allInstances = coverC.size() + coverD.size() - commonInstances;
		
		double dissimilarity = commonInstances / (double)coverC.size();
		if (dissimilarity < (commonInstances / (double)coverD.size()))
			dissimilarity = commonInstances / (double)coverD.size();
		
		return commonInstances / (double)allInstances * dissimilarity;
	}

	
	public ConceptSimilarity(AbstractReasonerComponent reasoner, Set<Individual> instances) {
		this.reasoner = reasoner;
		this.instances = new HashSet<Individual>();
		this.instances.addAll(instances);
	}
	
	/**
	 * Flatten disjunctive description into set of descriptions, e.g. A or (B and C) into {A, B and C}
	 * Note that this methods does not normalise the description. Therefore, it should be called after a normalisation call.
	 * 
	 * @param description Description to be flattened 
	 * 
	 * @return List of description in conjunctive normal form
	 */
	/*
	public static List<Description> disjunctiveNormalFormFlatten(Description description) {
		List<Description> result = new LinkedList<Description>();
		
		//check if the given description is in disjunctive normal form? return NULL if it is not
		if (!isDisjunctiveNormalForm(description))
			return null;
		
		if (description instanceof Union) {
			for (Description child : description.getChildren()) {
				result.addAll(disjunctiveNormalFormFlatten(child));
			}
		}
		else
			result.add(description);
		
		return result;
	}
	*/
	

	/**
	 * Check if a given description is in disjunctive normal form or not.
	 * 
	 * @param description Description to be checked
	 *  
	 * @return true if the given description is in disjunctive normal form, false otherwise
	 */
	public static boolean isDisjunctiveNormalForm(Description description) {
		
		if ((description instanceof NamedClass) || (description instanceof Thing) || description instanceof Nothing)
			return true;
		else if (description instanceof Negation)
			return isDisjunctiveNormalForm(description.getChild(0));
		else if (description instanceof Union) {
			for (Description child : description.getChildren())
				return isDisjunctiveNormalForm(child);
		}
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren())
				if (containDisjunction(child))
					return false;
			
			return true;
		}
		else if (description instanceof Restriction) {
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();
		
			if ((pro instanceof DatatypeProperty) || (description instanceof ObjectValueRestriction))	
				return true;

			return !(containDisjunction(description.getChild(0)));
		}
		
		return false;
	}	//isDisjunctiveNormalForm
	
	
	/**
	 * Check if the given description contain disjunction or not. This method aims to support the disjunctive normal form checking
	 * 
	 * @param description Description to check
	 * 
	 * @return true if the given description contains disjunction, false otherwise  
	 */
	public static boolean containDisjunction(Description description) {
		if (description.getLength() <= 2)
			return false;
		else if (description instanceof Union)
			return true;
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren())
				if (containDisjunction(child))
					return true;
			return false;
		}
		else if (description instanceof Restriction) {
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();			
			if (pro instanceof DatatypeProperty)	//data properties have no union
				return false;
			else { 	//object properties
				for (Description child : description.getChildren())
					return containDisjunction(child);	
			}
		}
		return false;
	}	//containDisjunction
	
	
	
	/**
	 * Flatten a disjunctive normal form into list of descriptions, e.g. (A or B or (A and D)) --> {A, B, (A and D)}
	 * This method does not perform the normalisation. Therefore, it should be called after a disjunctive normalisation.
	 * 
	 * @param description
	 * @return
	 */
	public static List<Description> flattenDisjunctiveNormalDescription(Description description) {
		List<Description> result = new LinkedList<Description>();
		
		/*
		if (!isDisjunctiveNormalForm(description)) {
			System.out.println("**ERROR - " + description + " is not in disjunctive normal form");
			return null;
		}
		*/
		
		if (description instanceof Union) {
			for (Description child : description.getChildren()) {
				if (child instanceof Union)
					result.addAll(flattenDisjunctiveNormalDescription(child));
				else
					result.add(child);
			}	
		}
		else {
			result.add(description);
		}
			
		return result;
	}
	
	
	
	/**
	 * Normalise a description into disjunctive normal form
	 * 
	 * @param description Description to be normalised
	 * 
	 * @return Description in disjunctive normal form
	 */
	public static Description normalise(int level, Description description) {
		
		/*
		for (int i=0; i<level; i++)
			System.out.print("-");
		
		System.out.println("-normalise (l= " + description.getLength() + "): " + description);
		*/
		
		//class, Thing, Nothing
		if ((description instanceof NamedClass) || (description instanceof Thing) || (description instanceof Nothing))			
			return description;

		//Negation
		else if (description instanceof Negation) {
			Description norm = normalise(level+1, description.getChild(0));
			
			if (norm instanceof Intersection) {	//not(and(A, B, C)) = or(not(A), not(B), not(C))
				List<Description> children = new LinkedList<Description>();
				
				for (Description child : norm.getChildren())
					children.add(new Negation(child));
				return new Union(children);
			}
			else if (norm instanceof Union) {
				List<Description> children = new LinkedList<Description>();
				
				for (Description child : norm.getChildren())
					children.add(new Negation(child));
				return new Intersection(children);
				
			}
			else 
				return new Negation(norm);
		} //negation
		
		//Union
		else if (description instanceof Union) {	//A or B or C ...
			List<Description> children = new LinkedList<Description>();
			
			//normalise all description's children
			for (Description child : description.getChildren()) {
				Description norm = normalise(level+1,child);
				
				if (norm instanceof Union)
					children.addAll(norm.getChildren());
				else
					children.add(norm);
			}
			
			return new Union(children);
		} //union		
		
		//Intersection
		else if (description instanceof Intersection) {	//A and B and C ...
			List<Description> children = new LinkedList<Description>();
			
			Description firstUnion = null;
			
			for (Description child : description.getChildren()) {		
				Description norm = normalise(level+1, child);
				
				if (norm instanceof Intersection)	//A and B
					children.addAll(norm.getChildren());
				else if (norm instanceof Union) {
					
					//if the first Union is found, keep it for transformation: A and (B or C) = (A and B) or (A and C)
					if (firstUnion == null)		 
						firstUnion = norm;
					else 
						children.add(norm);
				}
				else
					children.add(norm);
			}	//for each child of the description
			
			if (firstUnion == null)
				return new Intersection(children);
			else {	//transform: A and (B or C) ==> (A and B) or (A and C)				
				
				List<Description> unionChildren = new LinkedList<Description>();				
				for (Description child : firstUnion.getChildren()) {
					List<Description> tmp = new LinkedList<Description>();	//contains Intersections
					tmp.add(child);
					tmp.addAll(children);
					
					unionChildren.add(new Intersection(tmp));
				}
				
				return new Union(unionChildren);	//normalise(level+1, new Union(unionChildren));
			}
				
		} //intersection
		
		//restrictions
		else if (description instanceof Restriction) {
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();			
			if ((pro instanceof DatatypeProperty) || (description instanceof ValueRestriction))	//datatype property does not need to be normalised	
				return description;		
			else { 	//object properties, normalise its Range
				//normalise the range of restriction and replace it with the normalised range
				if (description.getChildren().size() == 0)
					logger.warn("**** ERROR: Restriction [" + description + "] has no child");
				
				Description newChild = normalise(level+1,description.getChild(0));
				description.replaceChild(0, newChild);
				return description;
			}
		}
		
		return null;
	} //normalise()
	
	
	/**
	 * Extract the atomic/primary concepts at the top level of a description
	 * Why List, not Set????
	 * 
	 * @param description
	 * @return
	 */
	public static List<Description> prim(Description description) {
		List<Description> result = new LinkedList<Description>();
		
		if ((description instanceof NamedClass) ||(description instanceof Thing) ||
				(description instanceof Nothing))
			result.add(description);		
		else if (description instanceof Negation) {
			List<Description> primNegated = prim(description.getChild(0));	
			if (primNegated.size() > 0)
				result.add(description);	//TODO: wrong here???
		}
		else if ((description instanceof Intersection) || (description instanceof Union)) {
			for (Description child : description.getChildren()) {
			
				List<Description> tmp = prim(child);
				for (Description des : tmp)
					if (!result.contains(des))
							result.add(des);
			}
		}		
		
		return result;
	} //prim()
	
	
	
	public static Set<Description> primSet(Description description) {
		Set<Description> result = new HashSet<Description>();
		
		if ((description instanceof NamedClass) ||(description instanceof Thing) ||
				(description instanceof Nothing))
			result.add(description);
		else if (description instanceof Negation) {
			Set<Description> primNegation = primSet(description.getChild(0));
			if (primNegation.size() > 0) {
				for (Description d : primNegation)
				result.add(new Negation(d));
			}
		}
		else if ((description instanceof Intersection) || (description instanceof Union)) {
			for (Description child : description.getChildren()) {
			
				Set<Description> tmp = primSet(child);
				for (Description des : tmp)
					if (!result.contains(des))
							result.add(des);
			}
		}		
		
		return result;
	} //prim()
	
	/**
	 * Return a list of properties used in a given description.
	 * Why List, not Set???
	 *  
	 * @param description Description
	 * 
	 * @return List of properties in the description
	 */
	public static List<PropertyExpression> getProperty(Description description) {
		List<PropertyExpression> result = new LinkedList<PropertyExpression>();
		
		if ((description instanceof Restriction))
				result.add(((Restriction)description).getRestrictedPropertyExpression());
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren()) {
				
				//do not use addAll to avoid duplicate
				List<PropertyExpression> tmp = getProperty(child); 
				for (PropertyExpression pro : tmp)
					if (!result.contains(pro))
						result.add(pro);
			}
		}
		
		return result;
	} //getProperty()
	
	
	
	public static Set<PropertyExpression> getPropertySet(Description description) {
		Set<PropertyExpression> result = new HashSet<PropertyExpression>();
		
		if ((description instanceof Restriction))
				result.add(((Restriction)description).getRestrictedPropertyExpression());
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren()) {
				
				//do not use addAll to avoid duplicate???
				List<PropertyExpression> tmp = getProperty(child); 
				for (PropertyExpression pro : tmp)
					if (!result.contains(pro))
						result.add(pro);
			}
		}
		
		return result;
	} //getPropertySet()
	
	/**
	 * Get the Range of a property in a given description 
	 * 
	 * @param property Property
	 * @param description Description 
	 * 
	 * @return Range of the given property in the description
	 */
	public static Description val(PropertyExpression property, Description description) {
		List<Description> innerVal = valInner(property, description);
		
		if (innerVal.size() == 0)
			return Thing.instance;
		else if (innerVal.size() == 1)
			return innerVal.get(0);
		else			
			return new Intersection(innerVal);
	} //val()
	
	
	private static List<Description> valInner(PropertyExpression property, Description description) {
		List<Description> result = new LinkedList<Description>();
		
		//restriction
		if (description instanceof Restriction) {
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();			
			if (pro == property) {
				if (pro instanceof DatatypeProperty) {	//for datatype property, val(.) = {Thing}
					if (!result.contains(Thing.instance))
						result.add(Thing.instance);
				}
				else if (!(description instanceof ValueRestriction)) {	//object property ==> get its range
					if (description.getChildren().size() == 0)
						logger.warn("***ERROR: Description [" + description + "] has no child");
					
					result.add(description.getChild(0));
				}
					
			}
			
		}
		
		//intersection
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren()) {
				
				//add each element to avoid the duplication
				List<Description> tmp = new LinkedList<Description>();
				tmp = valInner(property, child);
				for (Description t : tmp) {
					if (!result.contains(t))
						result.add(t);
				}
			}
		}
		
		//other types of Description is not taken into consideration
		
		return result;
	}
	
	
	public static int min(PropertyExpression property, Description description) {
		int m = minInner(property, description);
		if (m == Integer.MAX_VALUE)
			m = 0;
		
		return m;
	}
	

	
	private static int minInner(PropertyExpression property, Description description) {
		int m = Integer.MAX_VALUE;
		
		//restriction
		if ((description instanceof DatatypeMinCardinalityRestriction) || 
				(description instanceof DatatypeExactCardinalityRestriction) ||
				(description instanceof ObjectMinCardinalityRestriction) ||
				(description instanceof ObjectExactCardinalityRestriction)) {
			
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();
			
			if (pro == property) {	
				int cardinary = ((CardinalityRestriction)description).getCardinality();
				m = (cardinary < m)? cardinary : m;
			}
		}
					
		//intersection
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren()) {
				int cardinary = minInner(property, child);
				m = (cardinary < m)? cardinary : m;				
			}
		}
		return m;
	}

	
	private static int max(PropertyExpression property, Description description) {
		int m = maxInner(property, description);
		if (m == Integer.MIN_VALUE)
			m = 0;
		
		return m;
	}
	

	private static int maxInner(PropertyExpression property, Description description) {
		int m = Integer.MIN_VALUE;
		
		//restriction
		if ((description instanceof DatatypeMaxCardinalityRestriction) || 
				(description instanceof DatatypeExactCardinalityRestriction) ||
				(description instanceof ObjectMaxCardinalityRestriction) ||
				(description instanceof ObjectExactCardinalityRestriction)) {
			
			PropertyExpression pro = ((Restriction)description).getRestrictedPropertyExpression();
			
			if (pro == property) {	
				int cardinary = ((CardinalityRestriction)description).getCardinality();
				m = (cardinary > m)? cardinary : m;
			}
		}
					
		//intersection
		else if (description instanceof Intersection) {
			for (Description child : description.getChildren()) {
				int cardinary = maxInner(property, child);
				m = (cardinary > m)? cardinary : m;				
			}
		}
		return m;
	}

	
	
	/**
	 * Calculate Sp(C, D) - the similarity between atomic concepts of two descriptions
	 * 
	 * @param C
	 * @param D
	 * 
	 * @return
	 */
	public double simPrim(Description C, Description D) {
		
		//System.out.print("  ** sPrim(" + C + ", " + D +") = ");
				
		Set<Individual> coverC = PE(C);
		Set<Individual> coverD = PE(D);
		
		if ((coverC == null) || (coverD == null)) {
			//System.out.println(" 0 - null coverage returned");
			return 0;
		}
		
		Set<Individual> copyCoverC = new HashSet<Individual>();
		copyCoverC.addAll(coverC);
		
		copyCoverC.removeAll(coverD);
		
		int intersection = coverC.size() - copyCoverC.size();
		int union = (coverC.size() + coverD.size() - intersection);
		
		double result = intersection / (double) union; 
	
		//System.out.println(intersection + "/" + union + " = " + result);
		
		return result;
	}
	
	
	public double simPrim(List<Description> C, List<Description> D) {
		Description intersectC, interesctD;
		
		if (C.size() < 1 || D.size() < 1)
			return -1;
		
		if (C.size() > 1)
			intersectC = new Intersection(C);
		else 
			intersectC = C.get(0);
		
		if (D.size() > 1)
			interesctD = new Intersection(D);
		else
			interesctD = D.get(0);
		
		return simPrim(intersectC, interesctD);
	} //simPrim()
	

	/**
	 * Calculate the similarity of the properties/roles.<br>
	 * <ol>
	 *   <li>find all properties of C, D</li>
	 *   <li>for each property p: calculate s_p = sim(p, val(p, C), val(p, D))</li>
	 *   <li>return 1/n(sum(s_p))
	 * </ol>
	 * 
	 * @param C
	 * @param D
	 * @return
	 */
	public double simRole(List<PropertyExpression> allPro, Description C, Description D) {
		
		if (allPro.size() == 0) {
			//System.out.println("  ** simRole([] , " + C + ", " + D +") = 1");
			return 1;	
		}

		double sum = 0;
		
		/*
		List<PropertyExpression> proC = getProperty(C);
		List<PropertyExpression> proD = getProperty(D);
		List<PropertyExpression> allPro = new LinkedList<PropertyExpression>(proC);
				
		//calculate allPro
		for (PropertyExpression p : proD) {
			if (!allPro.contains(p))
				allPro.add(p);
		}
		*/
		
		//calculate simRole for each property
		for (PropertyExpression p : allPro) {
			Description valCP = val(p, C);
			Description valDP = val(p, D);
			
			//System.out.println("  ** simRole(" + p + ", " + C + ", " + D +"):");
			sum += similarity(valCP, valDP);
		}		
		
		return 1d/allPro.size()*sum;
	} //simRole()
	
	
	/**
	 * Calculate similarity between two descriptions.<br>
	 * sim(C, D) = a * ( simPrim(prim(C), prim(D)) + 1/n * sum_pi(simRole(pi, C, D)) + 1/n * sum_pi(simNum(pi, C, D))) 
	 * 
	 * @param C
	 * @param D
	 * @return
	 */
	private double similarityInner(List<PropertyExpression> allPro, Description C, Description D) {
		
		double sp = 0;
		double sr = 1;
		double sn = 1;
		
		List<Description> primC = prim(C);
		List<Description> primD = prim(D);				
		
		sp = simPrim(primC, primD);
		
		if (allPro.size() > 0) {
			sn = simNum(allPro, C, D);		
			sr = simRole(allPro, C, D);
			return (1/3d)*(sp + sr + sn);
		}
		else
			return sp;
		
		//return (1/3d)*(sp + sr + sn);
		
	}
	
	
	public double similarity(Description C, Description D) {
		
		/*
		if (containDisjunction(C)) {
			//System.out.println("ERROR - [" + C + "] contains disjunction");
			return -1;
		}
		
		if (containDisjunction(D)) {
			//System.out.println("ERROR - [" + D + "] contains disjunction");
			return -1;
		}
		*/
		
		List<PropertyExpression> proC = getProperty(C);
		List<PropertyExpression> proD = getProperty(D);
		
		List<PropertyExpression> allPro= new LinkedList<PropertyExpression>();
		allPro.addAll(proC);
		for (PropertyExpression p : proD) {
			if (!allPro.contains(p)) 
				allPro.add(p);
		}

		return similarityInner(allPro, C, D);
	}
	

	/**
	 * 
	 * @param C
	 * @param D
	 * @return
	 */
	public double disjunctiveSimilarity(Description C, Description D) {
		double sim  = 0;
				
		//System.out.println("****normalise (l=" + C.getLength() + "): " + C);
		Description normaliseC = normalise(0, C);
		
		//System.out.println("****flattening (l=" + normaliseC.getLength() + "): " + normaliseC);
		List<Description> flattenC = flattenDisjunctiveNormalDescription(normaliseC);		
		
		//System.out.println("****flattening result (l=" + flattenC.size() + "): " + flattenC);
		

		//System.out.println("****normalise (l=" + D.getLength() + "): " + D);
		Description normaliseD = normalise(0, D);
		
		//System.out.println("****flattening (l=" + normaliseC.getLength() + "): " + normaliseD);
		List<Description> flattenD = flattenDisjunctiveNormalDescription(normaliseD);		
		
		//System.out.println("****flattening result (l=" + flattenD.size() + "): " + flattenD);

		

		for (Description childC : flattenC) {
			for (Description childD : flattenD) {
				//System.out.println("* similarity(" + childC + ", " + childD + "):");
				double sim_i = similarity(childC, childD);
				sim = (sim < sim_i)? sim_i : sim;
				//System.out.println(" ==> return: " + sim);
			}
		}
		
		return sim;
	} //disjunctiveSimilarity()
	
	
	/**
	 * Compute similarity between a description and a set of descriptions (max similarity will be returned)
	 * 
	 * @param descriptions Set of descriptions
	 * @param D A description
	 * @return
	 */
	public double disjunctiveSimilarity(Set<Description> descriptions, Description D) {
		double similarity = 0;
		
		for (Description C : descriptions) {
			double s_tmp = disjunctiveSimilarity(C, D);
			similarity = (s_tmp > similarity) ? s_tmp : similarity;
		}
		
		return similarity;
	}
	
	
	/**
	 * Compute similarity between a description and a set of ParCELNodes (max)
	 *   
	 * @param descriptions Set of descriptions form of PerCELExNode
	 * @param D A description
	 * 
	 * @return Maximal similarity between D and the set of descriptions
	 */
	public double disjunctiveSimilarityEx(Set<ParCELExtraNode> descriptions, Description D) {
		double similarity = 0;
		
		for (ParCELExtraNode C : descriptions) {
			double s_tmp = disjunctiveSimilarity(C.getDescription(), D);
			similarity = (s_tmp > similarity) ? s_tmp : similarity;
		}
		
		return similarity;
	}
	/**
	 * Calculate similarity between numeric roles/properties of two descriptions<br>
	 * 
	 * simNum(C, D) = 1/n * sum_pi(sn(pi, min(pi, C), max(pi, C), min(pi, D), max(pi, D))) 
	 * 
	 * @param C
	 * @param D
	 * 
	 * @return
	 */
	public static double simNum(List<PropertyExpression> allPro, Description C, Description D) {
		
		//System.out.println("  ** simNum(" + C + ", " + D +"):");
		
		if (allPro.size() == 0) {
			//System.out.println("\t==> return: 1");
			return 1;	
		}
		
		double sn = 0;
		
				
		/*
		List<PropertyExpression> roleC = getProperty(C);
		List<PropertyExpression> roleD = getProperty(D);
		
		Set<PropertyExpression> allProperty = new HashSet<PropertyExpression>();
		allProperty.addAll(roleC);
		allProperty.addAll(roleD);
		*/
		
			
		for (PropertyExpression property : allPro) {
			int minC = min(property, C);
			int maxC = max(property, C);
			int minD = min(property, D);
			int maxD = max(property, D);
			
			double tmp = simNumInner(minC, maxC, minD, maxD);
			sn += tmp;
			
			//System.out.println("\tsn(" + property + ", (" + minC + ", " + maxC +") , (" + minD + ", " + maxD +")) = " + tmp);
		}
		
		//System.out.println("\t==> return: " + sn + "/" + allPro.size() + "=" + (sn/(double)allPro.size()));
		
		return (sn/(double)allPro.size());	
	} //simNum()
	
	/**
	 * Ref. paper "A similarity measure for the ALN Description Logic"
	 * 
	 * @param minC
	 * @param maxC
	 * @param minD
	 * @param maxD
	 * @return (minMax - maxMin + 1)/(double)(maxMax - minMin + 1);
	 */
	private static double simNumInner(int minC, int maxC, int minD, int maxD) {
		int minMax = (maxC < maxD)? maxC : maxD;
		int maxMin = (minC > minD)? minC : minD;
		
		int maxMax = (maxC > maxD)? maxC : maxD;
		int minMin = (minC < minD)? minC : minD; 
		
		//if (minMax > maxMin)
			return (minMax - maxMin + 1)/(double)(maxMax - minMin + 1);
		//else
			//return 0;
	}

	
	/**
	 * Simulates the reasoner to return the number of instances covered by a description
	 *  
	 * @param description
	 * 
	 * @return Number of instances covered by the given description
	 */
	private Set<Individual> PE(Description description) {
		
		if (reasoner != null) {
			return reasoner.hasType(description, instances);			
		}
		
		//below is for testing this class
		Set<Individual> result = new HashSet<Individual>();
		
		if ((description instanceof Thing) || 
				description.toKBSyntaxString().equalsIgnoreCase(Example2.person.toKBSyntaxString())) {
			
			String[] tmp = {"meg", "bod", "pat", "gwen", "ann", "sue", "tom"};
			for (String s : tmp)
				result.add(new Individual(s));			
		}
		
		else if (description.toKBSyntaxString().equalsIgnoreCase(Example2.male.toKBSyntaxString())) {
			String[] tmp = {"bod", "pat", "tom"};
			for (String s : tmp)
				result.add(new Individual(s));	
		}
		
		else if ((description.toKBSyntaxString().equalsIgnoreCase(Example2.notMale.toKBSyntaxString())) || 
				(description.toKBSyntaxString().equalsIgnoreCase(Example2.person_and_not_male.toKBSyntaxString()))) {
			
			String[] tmp = {"meg", "gwen", "ann", "sue"};
			for (String s : tmp)
				result.add(new Individual(s));	
		}
		else if (description.toKBSyntaxString().equalsIgnoreCase(Example2.notMale.toKBSyntaxString())) {
			String[] tmp = {"dog", "cat"};
			for (String s : tmp)
				result.add(new Individual(s));				
		}
		else 
			return null;

		return result;
	}
	
	
	
	/**
	 * MAIN: for testing
	 * 
	 * @param args
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException {

		
		ConceptSimilarity similarityChecker = new ConceptSimilarity();
		
		/*
		//check for the CWA		
		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("../examples/family-benchmark/family-benchmark.owl"));
		OWLDataFactory dataFactory = manager.getOWLDataFactory();

		PelletReasoner pelletReasoner = PelletReasonerFactory.getInstance().createReasoner(ontology);

		System.out.println("Pellet reasoner created: " + pelletReasoner.getRootOntology());
		
		OWLClass female = dataFactory.getOWLClass(IRI.create("http://www.benchmark.org/family#Female"));
		
		OWLClass male = dataFactory.getOWLClass(IRI.create("http://www.benchmark.org/family#Male"));
		OWLIndividual f10f172 = dataFactory.getOWLNamedIndividual(IRI.create("http://www.benchmark.org/family#F10F172"));
		
		OWLClassExpression notMale = new OWLObjectComplementOfImpl(dataFactory, male);
		
		OWLClassAssertionAxiom femaleF10F172 = dataFactory.getOWLClassAssertionAxiom(female, f10f172);
		OWLClassAssertionAxiom maleF10F172 = dataFactory.getOWLClassAssertionAxiom(male, f10f172);
		OWLClassAssertionAxiom notMaleF10F172 = dataFactory.getOWLClassAssertionAxiom(notMale, f10f172);
		
		System.out.println("Female class expression created: " + female.toString());
		System.out.println("f10f172 individual created: " + f10f172.toString());
		System.out.println("class assertion created: " + femaleF10F172);
		System.out.println("negation of Male created: " + notMale);
		
		
		System.out.println("F10F172 is a Female: " + pelletReasoner.isEntailed(femaleF10F172));
		System.out.println("F10F172 is a Male: " + pelletReasoner.isEntailed(maleF10F172));
		System.out.println("F10F172 is not a Male: " + pelletReasoner.isEntailed(notMaleF10F172));
		

		System.out.println("============");
		
		NamedClass dlPerson = new NamedClass("Person");
		NamedClass dlMale = new NamedClass("Male");
		NamedClass dlFemale = new NamedClass("Female");
		
		Negation dlNotMale = new Negation(dlMale);
		
		//System.out.println("notMale axiom: " + dlNotMale);
		
		//System.out.println("notMale converted by OWLAPIConverter: " + OWLAPIConverter.getOWLAPIDescription(dlNotMale));

		
		System.out.println("Similarity between Male and Female: " + similarityChecker.disjunctiveSimilarity(dlMale, dlFemale));
		
		 
		//System.exit(0);
		 */

		
		
		
		/*
		NamedClass A = new NamedClass("A");
		NamedClass B = new NamedClass("B");
		NamedClass C = new NamedClass("C");
		NamedClass D = new NamedClass("D");
		NamedClass E = new NamedClass("E");
		NamedClass F = new NamedClass("F");
		
		Union EorF = new Union(E, F);	//E or F
		Intersection Dand_EorF = new Intersection(D, EorF);	//(D and (E or F))
		Intersection Cand__Dand_EorF = new Intersection(C, Dand_EorF);	//(C and (D and (E or F))
		Union AorB = new Union(A, B);
		Union description = new Union(AorB, Cand__Dand_EorF);	//(A or B) or (C and (D and (E or F)))
		
		Intersection AandB = new Intersection(A, B);
		
		Union AorB_or_EorF = new Union(AorB, EorF);
		
		Negation not_AorB = new Negation(AorB_or_EorF);
		
		Description result = normalise(description);
		
		ObjectProperty hasChild = new ObjectProperty("hasChild");
		NamedClass personA = new NamedClass("Person_A");
		
		DatatypeProperty hasStarttime = new DatatypeProperty("hasStartTime");
		
		ObjectAllRestriction allChild = new ObjectAllRestriction(hasChild, personA);
		
		ObjectSomeRestriction someChild = new ObjectSomeRestriction(hasChild, EorF);
		
		ObjectMaxCardinalityRestriction maxChild = new ObjectMaxCardinalityRestriction(5, hasChild, AandB);
				
		//DatatypeMaxCardinalityRestriction maxDatatype = new DatatypeMaxCardinalityRestriction(hasStarttime, new IntMaxValue(5), 5);
		
		DatatypeSomeRestriction datatyepSome = new DatatypeSomeRestriction(hasStarttime, new IntMaxValue(5));
		
		Intersection personA_hasStarttime = new Intersection(personA, datatyepSome);
	
		System.out.println("------------ normalise test ---------------");
		System.out.println(allChild + " --> " + normalise(allChild));
		System.out.println(someChild + " --> " + normalise(someChild));
		System.out.println(maxChild + " --> " + normalise(maxChild));

		Description r = normalise(personA_hasStarttime);		
		System.out.println(personA_hasStarttime + " --> " + r);
		
		System.out.println(Cand__Dand_EorF + " --disj.flatten--> " + disjunctiveNormalFormFlatten(Cand__Dand_EorF));
		
		System.out.println(Cand__Dand_EorF + " --normalise--> " + normalise(Cand__Dand_EorF) + "--disj.flatten-->" + disjunctiveNormalFormFlatten(normalise(Cand__Dand_EorF)));
		
		System.out.println("------------ isDisjunctive test ---------------");
		
		System.out.println(maxChild + " is disjunctive: " + isDisjunctiveNormalForm(maxChild));		
		System.out.println(Cand__Dand_EorF + " is disjunctive: " + isDisjunctiveNormalForm(Cand__Dand_EorF));
		
		System.out.println("------------ prim(C) ---------------");
		Intersection AandB_and_maxChild = new Intersection(A, B, maxChild);
		System.out.println("prim(" + AandB_and_maxChild + ") = " + prim(AandB_and_maxChild));
		*/
		
		
		
		System.out.println("----------------");
		System.out.println("Example 1:");
		System.out.println("----------------");
		
		System.out.println("C = " + Example1.C.toManchesterSyntaxString(null, null));
		System.out.println("D = " + Example1.D.toManchesterSyntaxString(null, null));
		
		System.out.println("prim(C1) = " + prim(Example1.C1));
		System.out.println("prim(D1) = " + prim(Example1.D1));
		
		System.out.println("Properties(C1) = " + getProperty(Example1.C1));
		System.out.println("Properties(D1) = " + getProperty(Example1.D1));
		
		List<Description> flattenC = flattenDisjunctiveNormalDescription(Example1.C);
		List<Description> flattenD = flattenDisjunctiveNormalDescription(normalise(0, Example1.D));
		
		System.out.println("Flatten(C) = " + flattenC);
		System.out.println("Flatten(D) = " + flattenD);
	
		System.out.println("similarity(C, D) = " + similarityChecker.disjunctiveSimilarity(Example1.C, Example1.D));
		
		//------------------------------------------------
		//example 2: A similarity measure for the ALN DL
		//------------------------------------------------
		/*
		Thing thing = Thing.instance;
		NamedClass person = new NamedClass("Person");
		NamedClass male = new NamedClass("Male");
		ObjectProperty marriedTo = new ObjectProperty("marriedTo");
		ObjectProperty hasChild = new ObjectProperty("hasChild");
		Negation notMale = new Negation(male);
		Intersection person_and_not_male = new Intersection(person, notMale);
		
		ObjectAllRestriction marriedTo_all_Person = new ObjectAllRestriction(marriedTo, person);		
		ObjectAllRestriction marriedTo_all_person_and_not_male = new ObjectAllRestriction(marriedTo, person_and_not_male);
		ObjectCardinalityRestriction hasChild_less_1 = new ObjectMaxCardinalityRestriction(1, hasChild, thing);
		ObjectCardinalityRestriction hasChild_less_2 = new ObjectMaxCardinalityRestriction(2, hasChild, thing);
		
		Description CC = new Intersection(person, marriedTo_all_Person, hasChild_less_1);
		Description DD = new Intersection(male, marriedTo_all_person_and_not_male, hasChild_less_2);
		*/
		
		System.out.println("\n----------------");
		System.out.println("Example 2:");
		System.out.println("----------------");
		
		System.out.println(" C = " + Example2.C.toManchesterSyntaxString(null, null));
		System.out.println(" D = " + Example2.D.toManchesterSyntaxString(null, null));
		
		
		//List<Description> flattenC2 = flattenDisjunctiveNormalDescription(Example2.C);
		//List<Description> flattenD2 = flattenDisjunctiveNormalDescription(normalise(0, Example2.D));
		
		//System.out.println("Flatten(C) = " + flattenC2);
		//System.out.println("Flatten(D) = " + flattenD2);
		
		System.out.println("prim(C) = " + prim(Example2.C));
		System.out.println("prim(D) = " + prim(Example2.D));
		
		List<PropertyExpression> proC = getProperty(Example2.C);
		List<PropertyExpression> proD = getProperty(Example2.D);
		
		System.out.println("Properties(C) = " + proC);
		System.out.println("Properties(D) = " + proD);
		
		List<PropertyExpression> allPro = new LinkedList<PropertyExpression>();
		allPro.addAll(proC);
		for (PropertyExpression p : proD) { 
			if (!proC.contains(proD))
				proC.add(p);
		}
		System.out.println("Properties(C, D) = " + allPro);
		
		System.out.println("val(marriedTo, C) = " + val(Example2.marriedTo, Example2.C));
		System.out.println("val(marriedTo, D) = " + val(Example2.marriedTo, Example2.D));
		
		System.out.println("val(hasChild, C) = " + val(Example2.hasChild, Example2.C));
		System.out.println("val(hasChild, D) = " + val(Example2.hasChild, Example2.D));
		//val(Example2.hasParent, Example2.D);
		System.out.println("val(hasParent, D) = " + val(Example2.hasParent, Example2.D));
		
		System.out.println("sp(prim(C), prim(D)) = " + similarityChecker.simPrim(prim(Example2.C), prim(Example2.D)));
		System.out.println("sp(val(C), val(D)) = " + 
				similarityChecker.simPrim(val(Example2.marriedTo, Example2.C), val(Example2.marriedTo, Example2.D)));
		
		System.out.println("min(hasChild, C) = " + min(Example2.hasChild, Example2.C));
		System.out.println("min(hasChild, D) = " + min(Example2.hasChild, Example2.D));
		System.out.println("min(marriedTo, C) = " + min(Example2.marriedTo, Example2.C));
		System.out.println("min(marriedTo, D) = " + min(Example2.marriedTo, Example2.D));

		
		System.out.println("max(hasChild, C) = " + max(Example2.hasChild, Example2.C));
		System.out.println("max(hasChild, D) = " + max(Example2.hasChild, Example2.D));
		System.out.println("max(marriedTo, C) = " + max(Example2.marriedTo, Example2.C));
		System.out.println("max(marriedTo, D) = " + max(Example2.marriedTo, Example2.D));
		
		System.out.println("----------------");
		System.out.println("calculation");
		System.out.println("----------------");
		
		//System.out.println("sn (C, D) = " + simNum(Example2.C, Example2.D));
		//System.out.println("similarity(person, animal) = " + similarityChecker.disjunctiveSimilarity(Example2.person, Example2.animal));
		System.out.println("similarity(C, D) = " + similarityChecker.disjunctiveSimilarity(Example2.C, Example2.D));
		
		Union person_or_not_male_and_person = new Union(Example2.person_and_not_male, Example2.person);
		
		System.out.println(normalise(0, person_or_not_male_and_person));
		
	}
	
	
	private static class Example1 {
		//---------------------------------------------------
		// example 1: a dissimilarity measure for the ALC DL 
		//---------------------------------------------------
		public static NamedClass A1 = new NamedClass("A1");
		public static NamedClass A2 = new NamedClass("A2");
		public static NamedClass A3 = new NamedClass("A3");
		public static NamedClass A4 = new NamedClass("A4");
		public static NamedClass B1 = new NamedClass("B1");
		public static NamedClass B2 = new NamedClass("B2");
		public static NamedClass B3 = new NamedClass("B3");
		public static NamedClass B4 = new NamedClass("B4");
		public static NamedClass B5 = new NamedClass("B5");
		public static NamedClass B6 = new NamedClass("B6");
		
		public static ObjectProperty Q = new ObjectProperty("Q");
		public static ObjectProperty R = new ObjectProperty("R");
		public static ObjectProperty S = new ObjectProperty("S");
		public static ObjectProperty T = new ObjectProperty("T");
		
		// \some R. B1
		public static ObjectSomeRestriction R_some_B1 = new ObjectSomeRestriction(R, B1);
		
		// \all Q. (A4 and B5)
		public static Intersection A4_and_B5 = new Intersection(A4, B5);
		public static ObjectAllRestriction Q_all__A4_and_B5 = new ObjectAllRestriction(Q, A4_and_B5);
		
		// \all T. (\all Q. (A4 and B5))
		public static ObjectAllRestriction T_all__Q_all__A4_and_B5 = new ObjectAllRestriction(T, Q_all__A4_and_B5);
		
		// \some R. A3
		public static ObjectSomeRestriction R_some_A3 = new ObjectSomeRestriction(R, A3);
		
		// \some R. B2
		public static ObjectSomeRestriction R_some_B2 = new ObjectSomeRestriction(R, B2);
		
		// \all S. B3
		public static ObjectAllRestriction S_all_B3 = new ObjectAllRestriction(S, B3);
		
		
		// \all T. (B6 and B4)
		public static Intersection B6_and_b4 = new Intersection(B6, B4);
		public static ObjectAllRestriction T_all__B6_and_B4 = new ObjectAllRestriction(T, B6_and_b4);
		
		public static Description C1 = new Intersection(A2, R_some_B1, T_all__Q_all__A4_and_B5);
		public static Description C = new Union(C1, A1);
		
		public static Description D1 = new Intersection(A1, B2, R_some_A3, R_some_B2, S_all_B3, T_all__B6_and_B4);
		public static Description D = new Union(D1, B2);
	}

	
	private static class Example2 {
		public static Thing thing = Thing.instance;
		public static NamedClass person = new NamedClass("Person");
		public static NamedClass male = new NamedClass("Male");
		public static ObjectProperty marriedTo = new ObjectProperty("marriedTo");
		public static ObjectProperty hasChild = new ObjectProperty("hasChild");
		public static Negation notMale = new Negation(male);
		public static Intersection person_and_not_male = new Intersection(person, notMale);
		public static Union person_or_not_male = new Union(person, notMale);
		
		public static NamedClass animal = new NamedClass("animal");		
		
		public static ObjectSomeRestriction marriedTo_all_Person = new ObjectSomeRestriction(marriedTo, person);		
		public static ObjectAllRestriction marriedTo_all_person_and_not_male = new ObjectAllRestriction(marriedTo, person_and_not_male);		
		public static ObjectAllRestriction marriedTo_all_person_or_not_male = new ObjectAllRestriction(marriedTo, person_or_not_male);
		public static ObjectCardinalityRestriction hasChild_less_1 = new ObjectMaxCardinalityRestriction(1, hasChild, thing);
		public static ObjectCardinalityRestriction hasChild_less_2 = new ObjectMaxCardinalityRestriction(2, hasChild, thing);
		
		public static Description C = new Intersection(person, marriedTo_all_Person, hasChild_less_1);
		public static Description D = new Intersection(male, marriedTo_all_person_or_not_male, hasChild_less_2, marriedTo_all_Person);
		
		public static ObjectProperty hasParent = new ObjectProperty("hasParent");		
	}
	
	

}
 