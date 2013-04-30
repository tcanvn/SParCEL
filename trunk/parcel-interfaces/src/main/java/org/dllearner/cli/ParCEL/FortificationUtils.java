package org.dllearner.cli.ParCEL;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.celoe.CELOE.PartialDefinition;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Intersection;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Negation;
import org.dllearner.core.owl.Nothing;
import org.dllearner.core.owl.ObjectValueRestriction;
import org.dllearner.core.owl.PropertyExpression;
import org.dllearner.core.owl.Restriction;
import org.dllearner.core.owl.Thing;
import org.dllearner.core.owl.Union;
import org.dllearner.core.owl.ValueRestriction;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.owl.OWLAPIConverter;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;

public class FortificationUtils {
	
	private static Logger logger = Logger.getLogger(FortificationUtils.class);

	
	//fortification strategies			
	public static String[] strategyNames = {"TCScore", "CSScore", "FVScore", "SIMILARITY-NEG_POS", "NEW-JACCARD OVERLAP", "NEW-JACCARD DISTANCE", "COMBINATION", "Random"};
	//public static String[] strategyShortNames = {"TCScore", "CSScore", "FVScore", "Sim-NEG_POS", "NEW-JOverlap", "NEW-JDistance", "Combination"};
	
	//constants used to index the fortification strategies in the result array 
	public static final int TRAINING_COVERAGE_INDEX = 0, CONCEPT_OVERL_SIM_INDEX = 1,
			FORTIFICATION_VALIDATION_INDEX = 2, SIMILARITY_POS_NEG_INDEX = 3,
			NEW_JACCARD_OVERLAP_INDEX = 4, NEW_JACCARD_DISTANCE_INDEX = 5,
			CONBINATION_INDEX = 6, RANDOM_INDEX=7;
	
	
	public static class CoverageComparator implements Comparator<CELOE.PartialDefinition> {
		@Override
		public int compare(CELOE.PartialDefinition p1, CELOE.PartialDefinition p2) {
			if (p1.getCoverage() > p2.getCoverage())
				return -1;
			else if (p1.getCoverage() < p2.getCoverage())
				return 1;
			else if (p1.getDescription().getLength() >	p2.getDescription().getLength())//the same coverage
				return -1;
			else if (p1.getDescription().getLength() < p2.getDescription().getLength())
				return 1;
			else
				return new ConceptComparator().compare(p1.getDescription(), p2.getDescription());
				
		}
	}	

	
	public static double fortificationScore(PelletReasoner reasoner, Description cpdef, Description concept,
			int noOfCp, int noOfCn, int noOfPos, int noOfNeg, int commonPos, int commonNeg, int maxLength) 
	{
		double cnFactor=1.2, cpFactor=0.5, lenFactor = 0.1; 
		double commonFactor = 1.5;
		
		//Intersection intersection = new Intersection(concept, new Negation(cpdef));		
		
		//if (reasoner.isSatisfiable(OWLAPIConverter.getOWLAPIDescription(intersection)))
		//	return 0;
				
		double score1 = noOfCn/(double)noOfNeg*cnFactor - (noOfCp/(double)noOfPos)*cpFactor + lenFactor*(1-cpdef.getLength()/maxLength);
		double score2 = noOfCn/(double)noOfNeg*cnFactor - noOfCp/(double)noOfPos*cpFactor - commonPos/(double)noOfPos*commonFactor + commonNeg/(double)noOfNeg*commonFactor;
		
		return score1;
	}
	
	
	public static String getCpdefStringOldScore(CELOE.PartialDefinition cpdef, String baseURI, Map<String, String> prefixes) {
		DecimalFormat df = new DecimalFormat();
		
		String result = cpdef.getDescription().toKBSyntaxString(baseURI, prefixes)  
				+ "(l=" + cpdef.getDescription().getLength() 
				+ ", cn_training=" + df.format(cpdef.getCoverage())
				+ ", ortho=" + df.format(cpdef.getAdditionValue(0))
				+ ", cn_test=" + Math.round(cpdef.getAdditionValue(1))
				+ ", old-jaccard OL=" + df.format(1-cpdef.getAdditionValue(2))				
				+ ", new-jaccard OL=" + df.format(cpdef.getAdditionValue(9))
				+ ", fort_training_score(cn_test)=" + df.format(cpdef.getAdditionValue(3))
				+ ", simAll=" + df.format(cpdef.getAdditionValue(4))
				+ ", simPos=" + df.format(cpdef.getAdditionValue(5))
				+ ", simNeg=" + df.format(cpdef.getAdditionValue(6))
				+ ", combinedScore=" + df.format(cpdef.getAdditionValue(8))
				+ ")"; 
				
		return result;
	}
	
	
	public static String getCpdefString(CELOE.PartialDefinition cpdef, String baseURI, Map<String, String> prefixes) {
		DecimalFormat df = new DecimalFormat();
		
		String result = cpdef.getDescription().toKBSyntaxString(baseURI, prefixes)  
				+ "(l=" + cpdef.getDescription().getLength() 
				+ ", trainingCn=" + df.format(cpdef.getCoverage())
				+ ", TCScore=" + df.format(cpdef.getAdditionValue(TRAINING_COVERAGE_INDEX))
				+ ", CSScore=" + df.format(cpdef.getAdditionValue(CONCEPT_OVERL_SIM_INDEX))
				+ ", FVScore=" + Math.round(cpdef.getAdditionValue(FORTIFICATION_VALIDATION_INDEX))
				+ ", jaccard_Sim=" + df.format(cpdef.getAdditionValue(NEW_JACCARD_OVERLAP_INDEX))				
				+ ", jaccard_Dist=" + df.format(1-cpdef.getAdditionValue(NEW_JACCARD_DISTANCE_INDEX))
				+ ", Sim_NegPos=" + df.format(cpdef.getAdditionValue(SIMILARITY_POS_NEG_INDEX))
				+ ", combination=" + df.format(cpdef.getAdditionValue(CONBINATION_INDEX))
				+ ")"; 
				
		return result;
	}

	
	/**
	 * Convert an array of double that contains accuracy/completeness/correctness into a string (from the 1st..6th elements)
	 * 
	 * @param df Decimal formatter 
	 * @param arr Array of double (7 elements)
	 * 
	 * @return A string of double values
	 */
	public static String arrayToString(int[] arr) {
		String result = "[" + arr[0];
		
		for (int i=1; i<arr.length; i++)
			result += (";" + arr[i]);
		
		result += "]";
		
		return result;
	}
	
	
	/**
	 * Convert an array of double that contains accuracy/completeness/correctness into a string (from the 1st..6th elements)
	 * 
	 * @param df Decimal formatter 
	 * @param arr Array of double (7 elements)
	 * 
	 * @return A string of double values
	 */
	public static String arrayToString(DecimalFormat df, double[] arr) {
		String result = "[" + df.format(arr[0]);
		
		for (int i=1; i<arr.length; i++)
			result += (";" + df.format(arr[i]));
		
		result += "]";
		
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
	
	
	
	public static Description normalise(Description d) {
		return normalise(0, d);
	}
	

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
	 * Get the primary concepts at the 1st level of a description
	 * 
	 * @param description
	 * @return A set of primary concepts at the 1st level of a description
	 */
	public static Set<Description> getPrimaries(Description description) {
		Set<Description> result = new HashSet<Description>();
		
		//if NamedClass, Thing, Nothing ==> return these axioms
		if ((description instanceof NamedClass) ||(description instanceof Thing) ||	(description instanceof Nothing))
			result.add(description);
		
		//negation: not(C) ==> call this method recursively for C's children, return not(C's children) set
		else if (description instanceof Negation) {
			Set<Description> primNegation = getPrimaries(description.getChild(0));
			if (primNegation.size() > 0) {
				for (Description d : primNegation)
				result.add(new Negation(d));
			}
		}
		
		//Intersection or Union: call this method recursively for its children, return set of its children 
		else if ((description instanceof Intersection) || (description instanceof Union)) {
			for (Description child : description.getChildren()) {
			
				Set<Description> tmp = getPrimaries(child);
				for (Description des : tmp)
					if (!result.contains(des))
							result.add(des);
			}
		}		
		
		return result;
	} //getPrimaries()
	
	
	/**
	 * Flatten a disjunctive normal form into list of descriptions, e.g. (A or B or (A and D)) --> {A, B, (A and D)}
	 * This method does not perform the normalisation. Therefore, it should be called after a disjunctive normalisation.
	 * 
	 * @param description
	 * @return
	 */
	public static Set<Description> flattenDisjunctiveNormalDescription(Description description) {
		Set<Description> result = new HashSet<Description>();

		
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
	 * Compute overlap between 2 descriptions (Nicola paper) 
	 * 
	 * @param coverC
	 * @param coverD
	 * @return
	 */
	public static double getConceptOverlapSimple(Set<Individual> coverC, Set<Individual> coverD) {
		
		if (coverC.size() == 0 || coverD.size() == 0)
			return 0;
		
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
	
	
	
	
	/**
	 * This is for testing utility methods in this class
	 * 
	 * @param args
	 */
	public static void main(String []args) {
				
	}
	
	/**
	 * Used to hold the fortification accuracy
	 * 
	 * @author An C. Tran
	 *
	 */
	public static class FortificationResult {
		public double[] fortificationCompleteness;
		public double[] fortificationCorrectness;
		public double[] fortificationAccuracy;
		public double[] fortificationFmeasure;
		
		public double[] fortificationAccuracyStepByStep;
		public double[] fortificationFmeasureStepByStep;
		public double[] fortificationCorrectnessStepByStep;
		public double[] fortificationCompletenessStepByStep;
	}
	
	
	public static int[] getMultiStepFortificationStep(long noOfCpdef) {
		double[] fortificationStep = {0.05, 0.1, 0.2, 0.3, 0.4, 0.5};	//do the fortification at 5, 10, 20, 30, 40, and 50%
		int noOfFortification = fortificationStep.length;
		
		int[] fortificationDefinitions = new int[noOfFortification];	//corresponding number of cpdef for each fortification		
		for (int i=0; i<noOfFortification; i++) {
			if (noOfCpdef > 10)
				fortificationDefinitions[i] = (int)Math.round(Math.ceil(fortificationStep[i]*noOfCpdef));
			else {
				fortificationDefinitions[i] = (i < noOfCpdef)? i+1 : (int)noOfCpdef;
			}
		}
		
		return fortificationDefinitions;
	}
	
	
	/**
	 * Calculate the fortification accuracy with 5%, 10%, 20%, 30%, 40% and 50% of cpdef
	 * 	and for each cpdef as well<br>
	 * 
	 * Steps:
	 * 	1. Calculate the original value of the metrics (without fortification)
	 * 	2. For each CPDEF:
	 * 		1) Calculate the CPDEF coverage (pos/neg)
	 * 		2) Accumulate the covered pos/neg
	 * 		3) Compute common covered pos/neg between the learnt concept and CPDEF
	 * 		4) ... 
	 * 
	 * @param rs Reasoner used to check coverage
	 * @param concept Learned concept
	 * @param cpdefs Set of counter partial definitions
	 * @param testSetPos Set of positive examples 
	 * @param testSetNeg Set of negative examples
	 * 
	 * @return Accuracy, completeness and correctness of the fortification. The first value is the value without fortification, 
	 * 	the 6 next value is for fortification with 5%, 10%, 20%, 30%, 40% and 50% of the best counter partial definitions. 
	 */
	public static FortificationResult fortifyAccuracyMultiSteps(AbstractReasonerComponent rs, Description concept, 
			SortedSet<CELOE.PartialDefinition> cpdefs, Set<Individual> testSetPos, Set<Individual> testSetNeg, boolean negated) {
		
			
		double[] fortificationStepByPercent = {0.05, 0.1, 0.2, 0.3, 0.4, 0.5};	//do the fortification at 5, 10, 20, 30, 40, and 50%
		int noOfFortificationStep = fortificationStepByPercent.length;
		
		//how many cpdef for each step?
		int[] fortificationStepByDefinitions = getMultiStepFortificationStep(cpdefs.size());
		
				
		//contain the fortification values by PERCENTAGE
		double[] fortCompletenessPercentage = new double[noOfFortificationStep+1];	//+1 for the original value, i.e. without fortification
		double[] fortCorrectnessPercentage = new double[noOfFortificationStep+1];
		double[] fortAccuracyPercentage = new double[noOfFortificationStep+1];
		double[] fortFmeasurePercentage= new double[noOfFortificationStep+1];
		
		//full fortification by EACH cpdef
		double[] fortAccuracyFull = new double[cpdefs.size()];
		double[] fortFmeasureFull = new double[cpdefs.size()];
		double[] fortCorrectnessFull = new double[cpdefs.size()];
		double[] fortCompletenessFull = new double[cpdefs.size()];
		
		
		//------------------------------
		//ORIGINAL accuracy + fmeasure
		//	i.e. without fortification
		//------------------------------
		int posSize = testSetPos.size();
		int negSize = testSetNeg.size();
		
		Set<Individual> conceptCp = rs.hasType(concept, testSetPos);
		Set<Individual> conceptCn = rs.hasType(concept, testSetNeg);
		
		double orgPrecision = ((conceptCp.size() + conceptCn.size()) == 0)? 
				0 : conceptCp.size() / (double)(conceptCp.size() + conceptCn.size());
		double orgRecall = conceptCp.size() / (double)posSize;
		double orgFmeasure = 100 * Heuristics.getFScore(orgRecall, orgPrecision);
		
		//store the original accuracy into the first element of the returning result 
		fortCompletenessPercentage[0] = 100 * conceptCp.size() / (double)posSize;
		fortCorrectnessPercentage[0] = 100 * ((negSize - conceptCn.size()) / (double)negSize);
		fortAccuracyPercentage[0] = 100 * (conceptCp.size() + negSize - conceptCn.size()) / (double)(posSize + negSize);
		fortFmeasurePercentage[0] = orgFmeasure;
		
		
		//----------------------------
		//start the fortification
		//----------------------------
		int cpdefUsed=0;	//number of cpdef used in the fortification
		int fortPercentageStep = 0;		
		
		//accumulated cp and cn
		Set<Individual> accumulateFortificationCp = new HashSet<Individual>();	//number of pos covered by the fortification
		Set<Individual> accumulateFortificationCn = new HashSet<Individual>();	//number of neg covered by the fortification
		
		int priorNoOfAccumulatedCp = conceptCp.size();
		int priorNoOfAccumulatedCn = conceptCn.size();
		
		for (CELOE.PartialDefinition orgCpd : cpdefs) {
			
			Description cpd;
			
			if (negated)	//the counter partial definition is negated before (ParCEL-Ex), get the original
				cpd = orgCpd.getDescription().getChild(0);
			else
				cpd = orgCpd.getDescription();
			
			
			cpdefUsed++;	//number of cpdef used
			
			//calculate cn , cp of the cpdef
			Set<Individual> cpdefCp = rs.hasType(cpd, testSetPos);
			Set<Individual> cpdefCn = rs.hasType(cpd, testSetNeg);


			//accumulate cn, cp
			accumulateFortificationCp.addAll(cpdefCp);	
			accumulateFortificationCn.addAll(cpdefCn);	
						
			//----------------------------------------
			//calculate the cp and cn
			Set<Individual> cpTmp = new HashSet<Individual>();
			Set<Individual> cnTmp = new HashSet<Individual>();
			cpTmp.addAll(accumulateFortificationCp);
			cnTmp.addAll(accumulateFortificationCn);
			
			//find the common pos/neg covered by the learnt concept and cpdef 
			cpTmp.removeAll(conceptCp);		//find the common covered pos between concept and cpdef
			cnTmp.removeAll(conceptCn);		//find the common covered neg between concept and cpdef
			
			int updatedCp = conceptCp.size() - (accumulateFortificationCp.size() - cpTmp.size());		//some pos may be removed by cpdef
			int updatedCn = conceptCn.size() - (accumulateFortificationCn.size() - cnTmp.size());		//some neg may be covered by cpdef
			
			//-----------------------------------------------
			//DISPLAY the cpdefs that change the accuracy
			//	Debugging purpose
			//-----------------------------------------------
			//if (cpTmp.size() < fortificationCp.size() || cnTmp.size() < fortificationCn.size()) {
			if ((priorNoOfAccumulatedCp != updatedCp) || (priorNoOfAccumulatedCn != updatedCn)) {
				logger.debug(cpdefUsed + ". " + orgCpd.getId() + ". " 
						+ FortificationUtils.getCpdefString(orgCpd, null, null)
						+ ", cp=" + cpdefCp + ", cn=" + cpdefCn
						//+ ", removed pos=" + (fortificationCp.size() - cpTmp.size()) 
						//+ ", removed neg=" + (fortificationCn.size() - cnTmp.size()));
						+ ", removed pos=" + (priorNoOfAccumulatedCp - updatedCp) 
						+ ", removed neg=" + (priorNoOfAccumulatedCn - updatedCn));
			}
			
			
			//calculate f-measure
			//precision = correct pos / all pos classified = cp / (cp + cn) 
			//recall = correct pos / no of pos = cp / pos.size()
			double precision = ((updatedCp + updatedCn) == 0)? 0 : updatedCp / (double)(updatedCp + updatedCn);
			double recall = updatedCp / (double)posSize;
			double fmeasure = 100 * Heuristics.getFScore(recall, precision);
			

			
			//----------------------------------------
			//if it is the fortification step, calculate the accuracy
			if (fortPercentageStep < noOfFortificationStep && cpdefUsed >= fortificationStepByDefinitions[fortPercentageStep]) {
	
				fortAccuracyPercentage[fortPercentageStep+1] = 100 * (updatedCp + (negSize - updatedCn)) / (double)(posSize + negSize);
				fortFmeasurePercentage[fortPercentageStep+1] = fmeasure;				
				fortCorrectnessPercentage[fortPercentageStep+1] = 100 * (negSize - updatedCn) / (double)negSize;
				fortCompletenessPercentage[fortPercentageStep+1] = 100 * updatedCp / (double)posSize;
				
				fortPercentageStep++;
				//if (fortStep >= noOfFortification)	//if the fortification reach the max number of steps, break
				//	break;
				
			}	//calculate accuracy for a fortification step
			
			//assign the full step value after each cpdef
			//if (cpdefUsed <= fortificationDefinitions[noOfFortification-1]) {
			fortAccuracyFull[cpdefUsed-1] = 100 * (updatedCp + (negSize - updatedCn)) / (double)(posSize + negSize);
			fortFmeasureFull[cpdefUsed-1] = fmeasure;
			fortCorrectnessFull[cpdefUsed-1] = 100 *  (negSize - updatedCn) / (double)negSize;	// = un/neg (un=neg-cn)
			fortCompletenessFull[cpdefUsed-1] = 100 * updatedCp / (double)posSize;
			
			//}			
			
			priorNoOfAccumulatedCp = updatedCp;
			priorNoOfAccumulatedCn = updatedCn;			
			
		} //each cpdef
		
		
		//sometime, the number of cpdef is too small ==> some fortification steps are not assigned the value
		//therefore, we need to assign value for them
		while (fortPercentageStep < noOfFortificationStep) {
			fortAccuracyPercentage[fortPercentageStep+1] = fortAccuracyPercentage[fortPercentageStep]; 
			fortFmeasurePercentage[fortPercentageStep+1] = fortFmeasurePercentage[fortPercentageStep]; 			
			fortCorrectnessPercentage[fortPercentageStep+1] = fortCorrectnessPercentage[fortPercentageStep];
			fortCompletenessPercentage[fortPercentageStep+1] = fortCompletenessPercentage[fortPercentageStep];
			fortPercentageStep++;
		}
		
		//return the result
		
		FortificationResult result = new FortificationResult();
		
		//percentage
		result.fortificationCompleteness = fortCompletenessPercentage;
		result.fortificationCorrectness = fortCorrectnessPercentage;
		result.fortificationAccuracy = fortAccuracyPercentage;
		result.fortificationFmeasure = fortFmeasurePercentage;
		
		//full steps
		result.fortificationAccuracyStepByStep = fortAccuracyFull;
		result.fortificationFmeasureStepByStep = fortFmeasureFull;
		result.fortificationCorrectnessStepByStep = fortCorrectnessFull;
		result.fortificationCompletenessStepByStep = fortCompletenessFull; 
		
		return result;
		
	}	//fortify method
	
	
	/**
	 * Sort descreasingly according to the additional value of the fortifying definitions
	 * 
	 * @author An C. Tran
	 *
	 */
	public static class AdditionalValueComparator implements Comparator<CELOE.PartialDefinition> {		
		int index = 0;
		boolean descending;
		
		public AdditionalValueComparator(int index) {
			this.index = index;
			this.descending = true;
		}
		

		public AdditionalValueComparator(int index, boolean descending) {
			this.index = index;
			this.descending = descending;
		}

		
		@Override
		public int compare(PartialDefinition pdef1, PartialDefinition pdef2) {
			if (pdef1.getAdditionValue(index) > pdef2.getAdditionValue(index)) {
				if (this.descending)
					return -1;
				else
					return 1;
			}
			else if (pdef1.getAdditionValue(index) < pdef2.getAdditionValue(index)) {
				if (this.descending)
					return 1;
				else
					return -1;
			}
			else
				return new ConceptComparator().compare(pdef1.getDescription(), pdef2.getDescription());
			
		}
		
	}
	
	
	/**
	 * Compare two URIs
	 * 
	 * @author An C. Tran
	 *
	 */
	public static class URIComparator implements Comparator<Individual> {
		@Override
		public int compare(Individual o1, Individual o2) {
			return o1.getURI().compareTo(o2.getURI());
		}
		
	}
	
}
