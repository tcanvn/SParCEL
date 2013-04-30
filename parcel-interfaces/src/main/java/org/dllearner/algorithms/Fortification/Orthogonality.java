package org.dllearner.algorithms.Fortification;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Intersection;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.NamedKBElement;
import org.dllearner.core.owl.Negation;
import org.dllearner.core.owl.Nothing;
import org.dllearner.core.owl.ObjectCardinalityRestriction;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.ObjectQuantorRestriction;
import org.dllearner.core.owl.Property;
import org.dllearner.core.owl.PropertyExpression;
import org.dllearner.core.owl.Restriction;
import org.dllearner.core.owl.Thing;
import org.dllearner.core.owl.Union;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.utilities.owl.OWLAPIConverter;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.apache.log4j.Logger;


import uk.ac.manchester.cs.owl.owlapi.OWLObjectIntersectionOfImpl;
import com.clarkparsia.pellet.owlapiv3.PelletReasoner;

/**
 * Orthogonality utility methods
 *   
 * @author An C. Tran
 *
 */
public class Orthogonality {
	
	
	private static Logger logger = Logger.getRootLogger();

	
	public static int orthogonalityCheck(PelletReasoner reasoner, OWLOntology ontology,
			Description desc1, Description desc2) {
		
		OWLClassExpression desc1OWL = OWLAPIConverter.getOWLAPIDescription(desc1);
		OWLClassExpression desc2OWL = OWLAPIConverter.getOWLAPIDescription(desc2);
		
		return orthogonalityCheck(reasoner, ontology, desc1OWL, desc2OWL);
	}
	/**
	 * Check for the orthogonality conditions given two descriptions A and B.
	 * This method will check for the satisfiability of: 
	 * 	1. (A and B), 
	 * 	2. (A and not B),
	 * 	3. (not A and B), 
	 * 	4. (not A and not B)

	 * @param reasoner a Pellet reasoner used to check the satisfiability
	 * @param ontology the KB ontology
	 * @param expr1 class expression 1
	 * @param expr2 class expression 2
	 * 
	 * @return O if the all four expressions is satisfiable, the corresponding condition (number) otherwise  
	 */
	public static int orthogonalityCheck(PelletReasoner reasoner, OWLOntology ontology,
			OWLClassExpression expr1, OWLClassExpression expr2) {
		
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		
		Set<OWLClassExpression> classSet = new TreeSet<OWLClassExpression>();
		OWLClassExpression conjunction[] = new OWLClassExpression[4];
		
		OWLClassExpression expr1Compl = expr1.getComplementNNF();
		OWLClassExpression expr2Compl = expr2.getComplementNNF();
		
		//--------------------
		//create conjunctions
		//--------------------
		/*
		
		//1. (A and B)
		classSet.add(expr1);	//1
		classSet.add(expr2);
		conjunction[0] = new OWLObjectIntersectionOfImpl(factory, classSet);
		
		//2. (A and not B)
		classSet.remove(expr2);
		classSet.add(expr2Compl);
		conjunction[1] = new OWLObjectIntersectionOfImpl(factory, classSet);		

		//3. (not A and B)
		classSet.clear();
		classSet.add(expr1Compl);
		classSet.add(expr2);
		conjunction[2] = new OWLObjectIntersectionOfImpl(factory, classSet);
		
		//4. (not A and not B)
		classSet.remove(expr2);
		classSet.add(expr2Compl);
		conjunction[3] = new OWLObjectIntersectionOfImpl(factory, classSet);

		
		//-------------------------
		//check the satisfiability
		//-------------------------
		for (int i=0; i<4; i++) {
			//System.out.print(i + "  ");
			if (!reasoner.isSatisfiable(conjunction[i]))
				return i+1;	//unsatisfiable
		}
		*/
		return 0;	//satisfiable
	}
	
	
	
	/**
	 * Calculate the Jaccard score between 2 descriptions. J(A, B) = 1 - (| A \cap B | / | A \cup B |)
	 * 
	 * @param d1 First description
	 * @param d2 Second description
	 * 
	 * @return Jaccard distance between (d1, d2). The smaller the distance is, the more overlap the concepts are. 
	 */
	public static double jaccardDistance(Description d1, Description d2) {
		Set<NamedKBElement> flatten_d1 = Orthogonality.flattenDescription(d1);
		Set<NamedKBElement> flatten_d2 = Orthogonality.flattenDescription(d2); 
		
		int d1Size = flatten_d1.size();
		int d2Size = flatten_d2.size();
		
		//calculate the number of common elements between 2 descriptions
		flatten_d1.removeAll(flatten_d2);		
		int noOfCommonElements = d1Size - flatten_d1.size();
		
		//check the result
		flatten_d2.removeAll(flatten_d1);
		
		if (flatten_d2.size() != d2Size) {
			logger.error("Error in calcualting the interestion between two descriptions");
			return -1;
		}
		
		double score = (noOfCommonElements) / (double)(d1Size + d2Size - noOfCommonElements);	//the overlap between 2 concepts
		
		return (1 - score);		//the distance between 2 concepts
	}
	
	
	
	
	
	
	/**
	 * This method "flattens" a description into atomic axioms
	 * 
	 * @param d Description to be flattened
	 * 
	 * @return Set of atomic axioms (named classes, data properties, object properties) in the given description
	 */
	public static Set<NamedKBElement> flattenDescription(Description d) {
			
		Set<NamedKBElement> result = new HashSet<NamedKBElement>();
		
		if ((d instanceof Thing) || (d instanceof Nothing)) {
			//do nothing
		}		
		else if (d instanceof NamedClass) {
			result.add((NamedClass)d);
		}
		else if (d instanceof Negation) {	//NOT			
			result.addAll(flattenDescription(d.getChild(0)));
		}
		else if ((d instanceof Union) || (d instanceof Intersection)) {	
			for (Description child_des : d.getChildren()) {
				result.addAll(flattenDescription(child_des));
			}
		}
		else if (d instanceof Restriction) {
			PropertyExpression p = ((Restriction)d).getRestrictedPropertyExpression();
			
			if ((p instanceof ObjectProperty) || (p instanceof DatatypeProperty))
				result.add((Property)p);
			else {
				logger.error(p + " is not support for the current flatten algorithm");
			}
			
			if ((d instanceof ObjectCardinalityRestriction) || (d instanceof ObjectQuantorRestriction)) {
				Description child = ((Description)d).getChild(0);
				result.addAll(flattenDescription(child));
			}	
		}
					
		return result;
		
	}
	
	
	/**
	 * Score a counter partial definition using fortification training pos and neg sets
	 * 
	 * @param reasoner Reasoner used to calculate the coverage
	 * @param cpdef Counter partial definition is being scored 
	 * @param concept Learned concept, used to check the satisfiability of the cpdef
	 * @param pos Set of positive examples
	 * @param neg Set of negative examples
	 * 
	 * @return Score of the counter partial definition
	 */
	public static double fortificationScore(PelletReasoner reasoner, Description cpdef, Description concept,
			int noOfCp, int noOfCn, int noOfPos, int noOfNeg, int commonPos, int commonNeg) 
	{
		double cnFactor=1.2, cpFactor=0.5;
		//double commonFactor = 1.5;
		
		Intersection intersection = new Intersection(concept, new Negation(cpdef));		
		
		if (reasoner.isSatisfiable(OWLAPIConverter.getOWLAPIDescription(intersection)))
			return -1;
				
		double score1 = noOfCn/(double)noOfNeg*cnFactor - noOfCp/(double)noOfPos*cpFactor;
		//double score2 = noOfCn/(double)noOfNeg*cnFactor - noOfCp/(double)noOfPos*cpFactor - commonPos/(double)noOfPos*commonFactor + commonNeg/(double)noOfNeg*commonFactor;
		
		return score1;
	}
	
	
	
	/**
	 * Calculate the fortification accuracy with 5%, 10%, 20%, 30%, 40% and 50% of cpdef
	 * 	and for each cpdef as well
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
	public static Orthogonality.FortificationResult fortifyAccuracyMultiSteps(AbstractReasonerComponent rs, Description concept, 
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
		Set<Individual> fortificationCp = new HashSet<Individual>();	//number of pos covered by the fortification
		Set<Individual> fortificationCn = new HashSet<Individual>();	//number of neg covered by the fortification
		
		int accumulateCp = conceptCp.size();
		int accumulateCn = conceptCn.size();
		
		for (CELOE.PartialDefinition orgCpd : cpdefs) {
			
			Description cpd;
			
			if (negated)	//the counter partial definition is negated before (ParCEL-Ex), get the original
				cpd = orgCpd.getDescription().getChild(0);
			else
				cpd = orgCpd.getDescription();
			
			
			cpdefUsed++;	//number of cpdef used
			
			//calculate cn , cp of the cpdef
			Set<Individual> cp = rs.hasType(cpd, testSetPos);
			Set<Individual> cn = rs.hasType(cpd, testSetNeg);
						
			//int previousFortificationCp = fortificationCp.size();
			//int previousFortificationCn = fortificationCn.size();
			
			//accumulate cn, cp
			fortificationCp.addAll(cp);
			fortificationCn.addAll(cn);
						
			//----------------------------------------
			//calculate the cp and cn
			Set<Individual> cpTmp = new HashSet<Individual>();
			Set<Individual> cnTmp = new HashSet<Individual>();
			cpTmp.addAll(fortificationCp);
			cnTmp.addAll(fortificationCn);
			
			cpTmp.removeAll(conceptCp);		//find the common covered pos between concept and cpdef
			cnTmp.removeAll(conceptCn);		//find the common covered neg between concept and cpdef
			
			int updatedCp = conceptCp.size() - (fortificationCp.size() - cpTmp.size());		//some pos may be removed by cpdef
			int updatedCn = conceptCn.size() - (fortificationCn.size() - cnTmp.size());		//some neg may be covered by cpdef
			
			//-----------------------------------------------
			//DISPLAY the cpdefs that change the accuracy
			//	Debugging purpose
			//-----------------------------------------------
			//if (cpTmp.size() < fortificationCp.size() || cnTmp.size() < fortificationCn.size()) {
			if ((accumulateCp != updatedCp) || (accumulateCn != updatedCn)) {
				logger.debug(cpdefUsed + ". " + orgCpd.getId() + ". " 
						+ getCpdefString(orgCpd, null, null)
						+ ", cp=" + cp + ", cn=" + cn
						//+ ", removed pos=" + (fortificationCp.size() - cpTmp.size()) 
						//+ ", removed neg=" + (fortificationCn.size() - cnTmp.size()));
						+ ", removed pos=" + (accumulateCp - updatedCp) 
						+ ", removed neg=" + (accumulateCn - updatedCn));
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
			
			accumulateCp = updatedCp;
			accumulateCn = updatedCn;			
			
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
		
		Orthogonality.FortificationResult result = new Orthogonality.FortificationResult();
		
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
	
	
	public static String getCpdefString(CELOE.PartialDefinition cpdef, String baseURI, Map<String, String> prefixes) {
		DecimalFormat df = new DecimalFormat();
		
		String result = cpdef.getDescription().toKBSyntaxString(baseURI, prefixes)  
				+ "(l=" + cpdef.getDescription().getLength() 
				+ ", cn_training=" + df.format(cpdef.getCoverage())
				+ ", ortho=" + df.format(cpdef.getAdditionValue(0))
				+ ", cn_test=" + Math.round(cpdef.getAdditionValue(1))
				+ ", jaccard=" + df.format(cpdef.getAdditionValue(2))				
				+ ", fort_training_score(cn_test)=" + df.format(cpdef.getAdditionValue(3))
				+ ", simAll=" + df.format(cpdef.getAdditionValue(4))
				+ ", simPos=" + df.format(cpdef.getAdditionValue(5))
				+ ", simNeg=" + df.format(cpdef.getAdditionValue(6))
				+ ")"; 
				
		return result;
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
	
}
