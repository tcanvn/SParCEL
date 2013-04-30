package org.dllearner.cli.ParCEL;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELAbstract;
import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.algorithms.ParCEL.ParCELPosNegLP;
import org.dllearner.algorithms.ParCELEx.ParCELExAbstract;
import org.dllearner.algorithms.celoe.CELOE; 
import org.dllearner.algorithms.celoe.CELOE.PartialDefinition;
import org.dllearner.cli.CrossValidation;
import org.dllearner.cli.ParCEL.Orthogonality.FortificationResult;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.statistics.Stat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

/**
 * Cross validation procedure with fortification for ParCEL
 * 
 *    In this class, the learner will be called 2 times: the 1st is for learning the counter partial definitions,
 *    	the 2nd is for learning the partial definitions.
 *    
 *    This is used for datasets in which the partial definitions are found "so fast" 
 *    	that causes the counter partial definitions generated is "too small".   
 *  
 * @author actran
 * 
 */

public class ParCELFortifiedCrossValidation2Phases extends CrossValidation {

	//pdef
	private Stat noOfPdefStat;
	private Stat noOfUsedPdefStat;
	private Stat avgPartialDefinitionLengthStat;
	
	//cpdef
	private Stat noOfCpdefStat;
	private Stat noOfCpdefUsedStat;
	private Stat avgCpdefLengthStat;	
	private Stat totalCPDefLengthStat;
	private Stat avgCpdefCoverageTrainingStat;
	
	
	//learning time
	private Stat learningTime;
	
	
	//fortify strategy statistical variables
	/*
	private Stat accuracyFortifyStat;
	private Stat correctnessFortifyStat;
	private Stat completenessFortifyStat;
	private Stat fmeasureFortifyStat;
	//private Stat avgFortifiedPartialDefinitionLengthStat;
	*/

	
	//blind fortification
	private Stat accuracyBlindFortifyStat;
	private Stat correctnessBlindFortifyStat;
	private Stat completenessBlindFortifyStat;
	private Stat fmeasureBlindFortifyStat;
	
	
	//labeled fortification
	private Stat labelFortifyCpdefTrainingCoverageStat;
	private Stat noOfLabelFortifySelectedCpdefStat;
	private Stat avgLabelCpdefLengthStat;
	private Stat labelFortifiedDefinitionLengthStat;
	private Stat accuracyLabelFortifyStat;
	private Stat correctnessLabelFortifyStat;
	private Stat completenessLabelFortifyStat;
	private Stat fmeasureLabelFortifyStat;

	

	//multi-step fortification
	protected Stat[][] accuracyFortifyStepStat;		//hold the fortified accuracy at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] completenessFortifyStepStat;	//hold the fortified completeness at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] correctnessFortifyStepStat;	//hold the fortified correctness at 5,10,20,30,40,50% (multi-strategies)
	protected Stat[][] fmeasureFortifyStepStat;	//hold the fortified correctness at 5,10,20,30,40,50% (multi-strategies)

	protected Stat[] noOfCpdefUsedMultiStepFortStat;
	
	
	protected double[][] accuracyFullStep;
	protected double[][] fmeasureFullStep;
	
	//protected Stat avgNoOfFortifiedDefinitions;

	Logger logger = Logger.getLogger(this.getClass());

	protected boolean interupted = false;

	/**
	 * Default constructor
	 */

	public ParCELFortifiedCrossValidation2Phases(AbstractCELA la, PosNegLP lp, AbstractReasonerComponent rs,
			int folds, boolean leaveOneOut, int noOfRuns) {
		super(la, lp, rs, folds, leaveOneOut, noOfRuns);
	}

	/**
	 * This is for PDLL cross validation
	 * 
	 * @param la
	 * @param lp
	 * @param rs
	 * @param folds
	 * @param leaveOneOut
	 * @param noOfRuns Number of k-fold runs, i.e. the validation will run kk times of k-fold validations 
	 */
	public ParCELFortifiedCrossValidation2Phases(AbstractCELA la, ParCELPosNegLP lp, AbstractReasonerComponent rs,
			int folds, boolean leaveOneOut, int noOfRuns) {

		super(); // do nothing

		//--------------------------
		//setting up 
		// 
		//--------------------------
		DecimalFormat df = new DecimalFormat();	

		// the training and test sets used later on
		List<Set<Individual>> trainingSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> trainingSetsNeg = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsNeg = new LinkedList<Set<Individual>>();
		List<Set<Individual>> fortificationSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> fortificationSetsNeg = new LinkedList<Set<Individual>>();

		
		// get examples and shuffle them too
		Set<Individual> posExamples = lp.getPositiveExamples();
		List<Individual> posExamplesList = new LinkedList<Individual>(posExamples);
		Collections.shuffle(posExamplesList, new Random(1));			
		Set<Individual> negExamples = lp.getNegativeExamples();
		List<Individual> negExamplesList = new LinkedList<Individual>(negExamples);
		Collections.shuffle(negExamplesList, new Random(2));
		
		String baseURI = rs.getBaseURI();
		Map<String, String> prefixes = rs.getPrefixes();

		//----------------------
		//end of setting up
		//----------------------

		// sanity check whether nr. of folds makes sense for this benchmark
		if(!leaveOneOut && (posExamples.size()<folds && negExamples.size()<folds)) {
			System.out.println("The number of folds is higher than the number of "
					+ "positive/negative examples. This can result in empty test sets. Exiting.");
			System.exit(0);
		}


		// calculating where to split the sets, ; note that we split
		// positive and negative examples separately such that the 
		// distribution of positive and negative examples remains similar
		// (note that there are better but more complex ways to implement this,
		// which guarantee that the sum of the elements of a fold for pos
		// and neg differs by at most 1 - it can differ by 2 in our implementation,
		// e.g. with 3 folds, 4 pos. examples, 4 neg. examples)
		int[] splitsPos = calculateSplits(posExamples.size(),folds);
		int[] splitsNeg = calculateSplits(negExamples.size(),folds);
		
		
		//for orthogonality check
		long orthAllCheckCount[] = new long[5];
		orthAllCheckCount[0] = orthAllCheckCount[1] = orthAllCheckCount[2] = orthAllCheckCount[3] = orthAllCheckCount[4] = 0;
		
		long orthSelectedCheckCount[] = new long[5];
		orthSelectedCheckCount[0] = orthSelectedCheckCount[1] = orthSelectedCheckCount[2] = orthSelectedCheckCount[3] = orthSelectedCheckCount[4] = 0;
		
	

		//System.out.println(splitsPos[0]);
		//System.out.println(splitsNeg[0]);

		// calculating training and test sets
		for(int i=0; i<folds; i++) {
			
			//test sets
			Set<Individual> testPos = getTestingSet(posExamplesList, splitsPos, i);
			Set<Individual> testNeg = getTestingSet(negExamplesList, splitsNeg, i);
			testSetsPos.add(i, testPos);
			testSetsNeg.add(i, testNeg);
			
			//fortification training sets
			Set<Individual> fortPos = getTestingSet(posExamplesList, splitsPos, (i+1) % folds);
			Set<Individual> fortNeg = getTestingSet(negExamplesList, splitsNeg, (i+1) % folds);
			fortificationSetsPos.add(i, fortPos);
			fortificationSetsNeg.add(i, fortNeg);
			
			//training sets
			Set<Individual> trainingPos = getTrainingSet(posExamples, testPos); 
			Set<Individual> trainingNeg = getTrainingSet(negExamples, testNeg);
			
			trainingPos.removeAll(fortPos);
			trainingNeg.removeAll(fortNeg);
			
			trainingSetsPos.add(i, trainingPos);
			trainingSetsNeg.add(i, trainingNeg);		
		}	

	

		//---------------------------------
		//k-fold cross validation
		//---------------------------------

		Stat runtimeAvg = new Stat();
		Stat runtimeMax = new Stat();
		Stat runtimeMin = new Stat();
		Stat runtimeDev = new Stat();
		
		Stat learningTimeAvg = new Stat();
		Stat learningTimeMax = new Stat();
		Stat learningTimeMin = new Stat();
		Stat learningTimeDev = new Stat();

		Stat noOfPartialDefAvg = new Stat();
		Stat noOfPartialDefDev = new Stat();
		Stat noOfPartialDefMax = new Stat();
		Stat noOfPartialDefMin = new Stat();

		Stat avgPartialDefLenAvg = new Stat();
		Stat avgPartialDefLenDev = new Stat();
		Stat avgPartialDefLenMax = new Stat();
		Stat avgPartialDefLenMin = new Stat();

		Stat avgFortifiedPartialDefLenAvg = new Stat();
		Stat avgFortifiedPartialDefLenDev = new Stat();
		Stat avgFortifiedPartialDefLenMax = new Stat();
		Stat avgFortifiedPartialDefLenMin = new Stat();
		
		Stat defLenAvg = new Stat();
		Stat defLenDev = new Stat();
		Stat defLenMax = new Stat();
		Stat defLenMin = new Stat();

		Stat trainingAccAvg = new Stat();
		Stat trainingAccDev= new Stat();
		Stat trainingAccMax = new Stat();
		Stat trainingAccMin = new Stat();

		Stat trainingCorAvg = new Stat();
		Stat trainingCorDev = new Stat();
		Stat trainingCorMax = new Stat();
		Stat trainingCorMin = new Stat();

		Stat trainingComAvg = new Stat();
		Stat trainingComDev = new Stat();
		Stat trainingComMax = new Stat();
		Stat trainingComMin = new Stat();

		Stat testingAccAvg = new Stat();
		Stat testingAccMax = new Stat();
		Stat testingAccMin = new Stat();
		Stat testingAccDev = new Stat();
		
		Stat fortifyAccAvg = new Stat();
		Stat fortifyAccMax = new Stat();
		Stat fortifyAccMin = new Stat();
		Stat fortifyAccDev = new Stat();


		Stat testingCorAvg = new Stat();
		Stat testingCorMax = new Stat();
		Stat testingCorMin = new Stat();
		Stat testingCorDev = new Stat();
		
		Stat fortifyCorAvg = new Stat();
		Stat fortifyCorMax = new Stat();
		Stat fortifyCorMin = new Stat();
		Stat fortifyCorDev = new Stat();
		
		Stat testingComAvg = new Stat();		
		Stat testingComMax = new Stat();
		Stat testingComMin = new Stat();
		Stat testingComDev = new Stat();
				
		Stat fortifyComAvg = new Stat();
		Stat fortifyComMax = new Stat();
		Stat fortifyComMin = new Stat();
		Stat fortifyComDev = new Stat();
		
		
		Stat testingFMeasureAvg = new Stat();
		Stat testingFMeasureMax = new Stat();
		Stat testingFMeasureMin = new Stat();
		Stat testingFMeasureDev = new Stat();
		
		Stat trainingFMeasureAvg = new Stat();
		Stat trainingFMeasureMax = new Stat();
		Stat trainingFMeasureMin = new Stat();
		Stat trainingFMeasureDev = new Stat();
		
		Stat fortifyFmeasureAvg = new Stat();
		Stat fortifyFmeasureMax = new Stat();
		Stat fortifyFmeasureMin = new Stat();
		Stat fortifyFmeasureDev = new Stat();
		
		
		Stat noOfDescriptionsAgv = new Stat();
		Stat noOfDescriptionsMax = new Stat();
		Stat noOfDescriptionsMin = new Stat();
		Stat noOfDescriptionsDev = new Stat();
				
		Stat noOfCounterPartialDefinitionsAvg = new Stat();
		Stat noOfCounterPartialDefinitionsDev = new Stat();
		Stat noOfCounterPartialDefinitionsMax = new Stat();
		Stat noOfCounterPartialDefinitionsMin = new Stat();
		
		Stat noOfCounterPartialDefinitionsUsedAvg = new Stat();
		Stat noOfCounterPartialDefinitionsUsedDev = new Stat();
		Stat noOfCounterPartialDefinitionsUsedMax = new Stat();
		Stat noOfCounterPartialDefinitionsUsedMin = new Stat();

		
		/*
		long orthAllCheckCountFold[] = new long[5];
		long orthSelectedCheckCountFold[] = new long[5];

		long orthAllCheckCountTotal[] = new long[5];
		long orthSelectedCheckCountTotal[] = new long[5];
		
		
		orthAllCheckCountTotal[0] = orthAllCheckCountTotal[1] = orthAllCheckCountTotal[2] = 
			orthAllCheckCountTotal[3] = orthAllCheckCountTotal[4] = 0;
		
		orthSelectedCheckCountTotal[0] = orthSelectedCheckCountTotal[1] = orthSelectedCheckCountTotal[2] = 
			orthSelectedCheckCountTotal[3] = orthSelectedCheckCountTotal[4] = 0;
		 */
		
		
		//----------------------------------------------------------------------
		//loading ontology into Pellet reasoner for checking 
		//the orthogonality and satisfiability (fortification training strategy) 
		//----------------------------------------------------------------------
		long ontologyLoadStarttime = System.nanoTime();		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = ((OWLFile)la.getReasoner().getSources().iterator().next()).createOWLOntology(manager);			
		outputWriter("Ontology created, axiom count: " + ontology.getAxiomCount());
		PelletReasoner pelletReasoner = PelletReasonerFactory.getInstance().createReasoner(ontology);
		outputWriter("Pellet creared and binded with the ontology: " + pelletReasoner.getReasonerName());
		long ontologyLoadDuration = System.nanoTime() - ontologyLoadStarttime;
		outputWriter("Total time for creating and binding ontology: " + ontologyLoadDuration/1000000000d + "ms");

		
		for (int kk=0; kk < noOfRuns; kk++) {

			//general statistics
			runtime = new Stat();
			learningTime = new Stat();
			length = new Stat();
			totalNumberOfDescriptions = new Stat();
			

			//pdef
			noOfPdefStat = new Stat();
			noOfUsedPdefStat = new Stat();
			avgPartialDefinitionLengthStat = new Stat();			
			
			//cpdef
			noOfCpdefStat = new Stat();
			noOfCpdefUsedStat = new Stat();
			totalCPDefLengthStat = new Stat();
			avgCpdefLengthStat = new Stat();
			avgCpdefCoverageTrainingStat = new Stat();
						

			//training 
			accuracyTraining = new Stat();
			trainingCorrectnessStat= new Stat();
			trainingCompletenessStat = new Stat();
			fMeasureTraining = new Stat();
			
			//test
			accuracy = new Stat();
			fMeasure = new Stat();
			testingCorrectnessStat = new Stat();
			testingCompletenessStat = new Stat();
					
						
			//blind fortification
			accuracyBlindFortifyStat = new Stat();
			correctnessBlindFortifyStat = new Stat();
			completenessBlindFortifyStat = new Stat();
			fmeasureBlindFortifyStat = new Stat();

			
			//labled fortification
			labelFortifyCpdefTrainingCoverageStat = new Stat();
			noOfLabelFortifySelectedCpdefStat = new Stat();
			avgLabelCpdefLengthStat = new Stat();
			labelFortifiedDefinitionLengthStat = new Stat();
			accuracyLabelFortifyStat = new Stat();
			correctnessLabelFortifyStat= new Stat();
			completenessLabelFortifyStat = new Stat();
			fmeasureLabelFortifyStat = new Stat();
			
			

			//fortification strategies			
			String[] strategyNames = {"TRAINING COVERAGE", "JACCARD SCORE", "FORTIFICATION TRAINING", "SIMILARITY-ALL", "SIMILARITY-NEG_POS"};
			int noOfStrategies = strategyNames.length;
			
			
			//fortification accuracy
			accuracyFortifyStepStat = new Stat[noOfStrategies][6];		//6 elements for six values of 5%, 10%, ..., 50%
			completenessFortifyStepStat = new Stat[noOfStrategies][6];
			correctnessFortifyStepStat = new Stat[noOfStrategies][6];
			fmeasureFortifyStepStat = new Stat[noOfStrategies][6];
			
			
			//initial fortification accuracy
			for (int i=0; i<noOfStrategies; i++) {
				for (int j=0; j<6; j++) {
					accuracyFortifyStepStat[i][j] = new Stat();
					completenessFortifyStepStat[i][j] = new Stat();
					correctnessFortifyStepStat[i][j] = new Stat();				
					fmeasureFortifyStepStat[i][j] = new Stat();
				}
			}
			
			noOfCpdefUsedMultiStepFortStat = new Stat[6];
			for (int i=0; i<6; i++)
				noOfCpdefUsedMultiStepFortStat[i] = new Stat();
				
			int minOfHalfCpdef = Integer.MAX_VALUE;
			
			//run n-fold cross validations
			for(int currFold=0; (currFold<folds); currFold++) {
				
				
				outputWriter("//---------------\n" + "// Fold " + currFold + "/" + folds + "\n//---------------");
				outputWriter("Training: " + trainingSetsPos.get(currFold).size() + "/" + trainingSetsNeg.get(currFold).size()
						+ ", test:" + testSetsPos.get(currFold).size() + "/" + testSetsNeg.get(currFold).size()
						+ ", fort: " + fortificationSetsPos.get(currFold).size() + "/" + fortificationSetsNeg.get(currFold).size()
					);


				if (this.interupted) {
					outputWriter("Cross validation has been interupted");
					return;
				}
				
				//================================================================
				//1. PHASE 1: Learn counter partial definitions
				// 		Reverse the pos/neg and let the learner start
				//================================================================
				
				//reverse the pos/neg examples
				lp.setPositiveExamples(trainingSetsNeg.get(currFold));
				lp.setNegativeExamples(trainingSetsPos.get(currFold));

				//init the learner
				try {			
					lp.init();
					la.init();
				} catch (ComponentInitException e) {
					e.printStackTrace();
				}
				
				outputWriter("** Phase 1 - Learning counter partial definitions");

				//start the learner
				long algorithmStartTime1 = System.nanoTime();
				try {
					la.start();
				}
				catch (OutOfMemoryError e) {
					System.out.println("out of memory at " + (System.currentTimeMillis() - algorithmStartTime1)/1000 + "s");
				}

				long algorithmDuration1 = System.nanoTime() - algorithmStartTime1;
				runtime.addNumber(algorithmDuration1/(double)1000000000);
				
				// learning time, does not include the reduction time
				long learningMili1 = ((ParCELAbstract)la).getLearningTime();
				learningTime.addNumber(learningMili1/(double)1000);

				
				//================================
				// finish learning phase 1
				//===============================
				
				//get the counter parcel definitions - sorted by training coverage by default
				TreeSet<CELOE.PartialDefinition> counterPartialDefinitions = new TreeSet<CELOE.PartialDefinition>(new CoverageComparator());

				//calculate the counter partial definitions' avg. coverage 
				//(note that the positive and negative examples are swapped)
				for (ParCELExtraNode cpdef : ((ParCELAbstract)la).getPartialDefinitions()) {
					
					int trainingCp = cpdef.getCoveredPositiveExamples().size();	//
							
					counterPartialDefinitions.add(new CELOE.PartialDefinition(cpdef.getDescription(), trainingCp));		
					
					avgCpdefCoverageTrainingStat.addNumber(trainingCp/(double)trainingSetsNeg.get(currFold).size());
				}
				
				

				outputWriter("Finish learning, number of partial definitions: " + counterPartialDefinitions.size());	

				
				//===================================================================
				//2. PHASE 2: Learn partial definitions
				// 		Assign the original set of pos/neg and let the learner start  
				//===================================================================
				
				//assign the the pos/neg examples
				lp.setPositiveExamples(trainingSetsPos.get(currFold));
				lp.setNegativeExamples(trainingSetsNeg.get(currFold));
				
				//init the learner
				try {			
					lp.init();
					la.init();
				} catch (ComponentInitException e) {
					e.printStackTrace();
				}
				
				outputWriter("** Phase 2 - Learning partial definitions");

				//start the learner
				long algorithmStartTime2 = System.nanoTime();
				try {
					la.start();
				}
				catch (OutOfMemoryError e) {
					System.out.println("out of memory at " + (System.currentTimeMillis() - algorithmStartTime2)/1000 + "s");
				}

				long algorithmDuration2 = System.nanoTime() - algorithmStartTime2;
				runtime.addNumber(algorithmDuration1/(double)1000000000);
				
				// learning time, does not include the reduction time
				long learningMili2 = ((ParCELAbstract)la).getLearningTime();
				learningTime.addNumber(learningMili2/(double)1000);
				
				
				// get the partial definition			
				// cast the la into ParCELExAbstract for easier accessing
				ParCELAbstract parcel = (ParCELAbstract)la;

				
				//get the target (learned) definition
				Description concept = parcel.getUnionCurrenlyBestDescription(); 
				
				outputWriter("Learning finished. Concept length: " + concept.getLength() + ", number of pdef used: " + parcel.getNoOfReducedPartialDefinition());
								
				
				//-------------------------------
				//training sets
				//-------------------------------
				Set<Individual> curFoldPosTrainingSet = trainingSetsPos.get(currFold);
				Set<Individual> curFoldNegTrainingSet = trainingSetsNeg.get(currFold); 
				
				int trainingPosSize = curFoldPosTrainingSet.size();
				int trainingNegSize = curFoldNegTrainingSet.size();
					
	
				
				//pdef: some stat information
				Set<ParCELExtraNode> partialDefinitions = parcel.getReducedPartialDefinition();
				long noOfPdef = parcel.getNumberOfPartialDefinitions();
				long noOfUsedPdef = parcel.getNoOfReducedPartialDefinition();
				double avgPdefLength = concept.getLength() / (double)noOfUsedPdef;
				noOfPdefStat.addNumber(noOfPdef);
				noOfUsedPdefStat.addNumber(noOfUsedPdef);
				avgPartialDefinitionLengthStat.addNumber(avgPdefLength);
				
				//def
				totalNumberOfDescriptions.addNumber(parcel.getTotalNumberOfDescriptionsGenerated());
				
				
				//print the coverage of the counter partial definitions
				//outputWriter("Number of counter partial definitions: " + noOfCpdef);
				
				/*				 
				//display the cpdef and their coverage 
				outputWriter("(CPDEF length and coverage (length, coverage)");
				int count = 1;
				String sTemp = "";
				
				for (ParCELExtraNode cpdef : counterPartialDefinitions) {
					sTemp += ("(" + cpdef.getDescription().getLength() + ", " + 
							df.format(cpdef.getCoveredNegativeExamples().size()/(double)trainingNegSize) + "); ");
					if (count % 10 == 0) {
						outputWriter(sTemp);
						sTemp = "";
					}
					count++;
				}
				*/
				

				outputWriter("------------------------------");
								
				//-----------------------------
				//training accuracy
				//-----------------------------
		
				//for the training accuracy
				Set<Individual> cpTraining = rs.hasType(concept, trainingSetsPos.get(currFold));		//positive examples covered by the learned concept
				Set<Individual> upTraining = Helper.difference(trainingSetsPos.get(currFold), cpTraining);	//false negative (pos as neg)
				Set<Individual> cnTraining = rs.hasType(concept, trainingSetsNeg.get(currFold));		//false positive (neg as pos)

				//training error set
				outputWriter("training set errors pos (" + upTraining.size() + "): " + upTraining);
				outputWriter("training set errors neg (" + cnTraining.size() + "): " + cnTraining);
				
				
				//calculate training completeness, correctness and accuracy
				int trainingCorrectPosClassified = cpTraining.size();	
				int trainingCorrectNegClassified = trainingNegSize - cnTraining.size();	//getCorrectNegClassified(rs, concept, trainingSetsNeg.get(currFold));
				int trainingCorrectExamples = trainingCorrectPosClassified + trainingCorrectNegClassified;
				
				double trainingAccuracy = 100*((double)trainingCorrectExamples/(trainingPosSize + trainingNegSize));	
				double trainingCompleteness = 100*(double)trainingCorrectPosClassified/trainingPosSize;
				double trainingCorrectness = 100*(double)trainingCorrectNegClassified/trainingNegSize;
				
				accuracyTraining.addNumber(trainingAccuracy);
				trainingCompletenessStat.addNumber(trainingCompleteness);
				trainingCorrectnessStat.addNumber(trainingCorrectness);
				
				//training F-Score
				int negAsPosTraining = cnTraining.size();
				double precisionTraining = (trainingCorrectPosClassified + negAsPosTraining) == 0 ? 
						0 : trainingCorrectPosClassified / (double) (trainingCorrectPosClassified + negAsPosTraining);
				double recallTraining = trainingCorrectPosClassified / (double) trainingPosSize;
				double currFmeasureTraining = 100 * Heuristics.getFScore(recallTraining, precisionTraining);
				fMeasureTraining.addNumber(currFmeasureTraining);


				//----------------------
				//test accuracy
				//----------------------
				
				Set<Individual> curFoldPosTestSet = testSetsPos.get(currFold);
				Set<Individual> curFoldNegTestSet = testSetsNeg.get(currFold);
				
				int testingPosSize = curFoldPosTestSet.size();
				int testingNegSize = curFoldNegTestSet.size();
				
				//calculate the accuracy on the test set
				Set<Individual> cpTest = rs.hasType(concept, curFoldPosTestSet);		//positive examples covered by the learned concept
				Set<Individual> upTest = Helper.difference(curFoldPosTestSet, cpTest);	//false negative (pos as neg)
				Set<Individual> cnTest = rs.hasType(concept, curFoldNegTestSet);		//false positive (neg as pos)

			
				//test accuracies
				int correctTestPosClassified = cpTest.size();	//covered pos. in test set				
				int correctTestNegClassified = testingNegSize - cnTest.size();	//uncovered neg in test set
				int correctTestExamples = correctTestPosClassified + correctTestNegClassified;

				double testingCompleteness = 100*(double)correctTestPosClassified/testingPosSize;
				double testingCorrectness = 100*(double)correctTestNegClassified/testingNegSize;				
				double currAccuracy = 100*((double)correctTestExamples/(testingPosSize + testingNegSize));

				accuracy.addNumber(currAccuracy);
				testingCompletenessStat.addNumber(testingCompleteness);
				testingCorrectnessStat.addNumber(testingCorrectness);				
	
				
				//F-Score test set
				int negAsPos = cnTest.size();
				double testPrecision = correctTestPosClassified + negAsPos == 0 ? 
						0 : correctTestPosClassified / (double) (correctTestPosClassified + negAsPos);
				double testRecall = correctTestPosClassified / (double) testingPosSize;
				double currFmeasureTest = 100 * Heuristics.getFScore(testRecall, testPrecision); 
				
				fMeasure.addNumber(currFmeasureTest);
				
				
				//---------------------------------------
				/// Fortification - TRAINGING COVERAGE				
				//---------------------------------------
				//counter partial definition is sorted by training coverage by default ==> don't need to sort the cpdef set 
				FortificationResult multiStepFortificationCoverage = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, counterPartialDefinitions, curFoldPosTestSet, curFoldNegTestSet, false);
				
				for (int i=0; i<6; i++) {
					accuracyFortifyStepStat[0][i].addNumber(multiStepFortificationCoverage.fortificationAccuracy[i+1]);
					completenessFortifyStepStat[0][i].addNumber(multiStepFortificationCoverage.fortificationCompleteness[i+1]);
					correctnessFortifyStepStat[0][i].addNumber(multiStepFortificationCoverage.fortificationCorrectness[i+1]);
					fmeasureFortifyStepStat[0][i].addNumber(multiStepFortificationCoverage.fortificationFmeasure[i+1]);
				}
				
				
				//check the f-measure, accuracy, completeness, correctness calculations between the methods 
				//	Orthogonality.fortifyAccuracyMultiSteps and inside this class
				
				/*
				outputWriter("********* check the calculation ***************");
				outputWriter("fmeasure: " + currFmeasureTest+ " // " + multiStepFortificationCoverage.fortificationFmeasure[0]);
				outputWriter("accuracy: " + currAccuracy + " // " + multiStepFortificationCoverage.fortificationAccuracy[0]);
				outputWriter("correctness: " + testingCorrectness + " // " + multiStepFortificationCoverage.fortificationCorrectness[0]);
				outputWriter("comleteness: " + testingCompleteness + " // " + multiStepFortificationCoverage.fortificationCompleteness[0]);
				*/
				
				
				//---------------------------------
				// Fortification - ALL CPDEFs
				// (BLIND Fortification)
				//---------------------------------
				//NOTE: 
				//Since this will iterate all cpdef, we will calculate score for all other fortification strategies
				// training coverage (done), jaccard, fortification training, 

				outputWriter("---------------------------------------------------------------");
				outputWriter("BLIND fortification - All counter partial defintions are used");
				outputWriter("---------------------------------------------------------------");
				
				
				//get the set of pos and neg (in the test set) covered by counter partial definition
				Set<Individual> cpdefPositiveCovered = new HashSet<Individual>();
				Set<Individual> cpdefNegativeCovered = new HashSet<Individual>();
				
				long totalCPDefLength = 0;
				
				//some variables for jaccard statistical info
				int c = 1;
				Map<Long, Integer> jaccardValueCount = new TreeMap<Long, Integer>();

				//variables for fortification training
				Set<Individual> fortificationTrainingPos = fortificationSetsPos.get(currFold);
				Set<Individual> fortificationTrainingNeg= fortificationSetsNeg.get(currFold);
				
				Set<Individual> allFortificationExamples = new HashSet<Individual>();
				
				allFortificationExamples.addAll(fortificationTrainingPos);
				allFortificationExamples.addAll(fortificationTrainingNeg);	//duplicate will be remove automatically
				
				ConceptSimilarity similarityCheckerAll = new ConceptSimilarity(rs, allFortificationExamples);
				ConceptSimilarity similarityCheckerPos= new ConceptSimilarity(rs, fortificationTrainingPos);
				ConceptSimilarity similarityCheckerNeg = new ConceptSimilarity(rs, fortificationTrainingNeg);
				
		
				//start the BLIND fortification and calculate the scores
				for (CELOE.PartialDefinition negCpdef : counterPartialDefinitions) {
					
					Description cpdef = negCpdef.getDescription();	//it is not negated

					//System.out.print(c + ". " 
					//		+ cpdef.toKBSyntaxString(baseURI, prefixes) + ": ");
					//--------------------
					//orthogonality check
					//--------------------
					//System.out.print("ortho check...");
					//int orthoCheck = Orthogonality.orthogonalityCheck(pelletReasoner, ontology, concept, cpdef.getDescription());
					int orthoCheck = 0;	//currently, disable this value as the reasoner often gets stuck in checking the satisfiability
					
					//count ortho check values for stat purpose
					orthAllCheckCount[orthoCheck]++;
					
					
					//--------------------
					//blind fortification
					//--------------------
					
					//System.out.print("testing coverage...");
					Set<Individual> cpdefCp = rs.hasType(cpdef, curFoldPosTestSet);
					Set<Individual> cpdefCn = rs.hasType(cpdef, curFoldNegTestSet);
					

					//---------
					//jaccard
					//---------
					//System.out.print("jaccard...");
					double jaccardDistance = Orthogonality.jaccardDistance_old(concept, cpdef);		//calculate jaccard distance between the learned concept and the cpdef	
					
					//count the jaccard values, for stat purpose
					long tmp_key = Math.round(jaccardDistance * 1000);
					if (jaccardValueCount.containsKey(tmp_key))
						jaccardValueCount.put(tmp_key, jaccardValueCount.get(tmp_key)+1);					
					else
						jaccardValueCount.put(tmp_key, 1);
					
					
					//-----------------------
					//fortification training
					//-----------------------
					//System.out.print("fort traning...");
					Set<Individual> fortCp = rs.hasType(cpdef, fortificationTrainingPos);
					Set<Individual> fortCn = rs.hasType(cpdef, fortificationTrainingNeg);
					
					int cp = fortCp.size();
					int cn = fortCn.size();
					
					fortCp.removeAll(rs.hasType(concept, fortificationTrainingPos));
					fortCn.removeAll(rs.hasType(concept, fortificationTrainingNeg));
					
					double fortificationTrainingScore = FortificationUtils.fortificationScore(pelletReasoner, cpdef, concept, 
							cp, cn, fortificationTrainingPos.size(), fortificationTrainingNeg.size(), 
							cp-fortCp.size(), cn-fortCn.size(), ((ParCELExAbstract)la).getMaximumHorizontalExpansion());
					
					
					//--------------
					//similarity
					//--------------
					double similarityScoreAll = similarityCheckerAll.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityScorePos = similarityCheckerPos.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityScoreNeg = similarityCheckerNeg.disjunctiveSimilarityEx(partialDefinitions, cpdef);
					double similarityCombineScore = similarityScoreNeg*1.5 - similarityScorePos*0.5;
					
					//---------------------------
					//assign score for the cpdef
					//---------------------------
					negCpdef.setAdditionValue(0, orthoCheck);
					negCpdef.setAdditionValue(1, cpdefCn.size());	//no of neg. examples in test set covered by the cpdef					
					negCpdef.setAdditionValue(2, jaccardDistance);
					negCpdef.setAdditionValue(3, fortificationTrainingScore);	
					negCpdef.setAdditionValue(4, similarityScoreAll);						
					negCpdef.setAdditionValue(5, similarityScorePos);
					negCpdef.setAdditionValue(6, similarityScoreNeg);
					negCpdef.setAdditionValue(7, similarityCombineScore);
					
					//------------------------
					//BLIND fortification
					//------------------------
					cpdefPositiveCovered.addAll(cpdefCp);
					cpdefNegativeCovered.addAll(cpdefCn);

					
					totalCPDefLength += cpdef.getLength();					
					
					//print the cpdef which covers some pos. examples
					//if (cpdefCp.size() > 0)								
					outputWriter(c++ + ". " + getCpdefString(negCpdef, baseURI, prefixes)
							+ ", cp=" + cpdefCp	+ ", cn=" + cpdefCn);	
				}
								
				outputWriter( " * Blind fortifcation summary: cp=" + cpdefPositiveCovered + " --- cn=" + cpdefNegativeCovered);
				
				
				outputWriter("test set errors pos (" + upTest.size() + "): " + upTest);
				outputWriter("test set errors neg (" + cnTest.size() + "): " + cnTest);
				
				//-----------------------------------------				
				//calculate Blind fortification accuracy
				//-----------------------------------------
				
				//fortify definition length: total length of all cpdef
				totalCPDefLengthStat.addNumber(totalCPDefLength);
				double avgCPDefLength = totalCPDefLength/(double)counterPartialDefinitions.size();
				avgCpdefLengthStat.addNumber(avgCPDefLength);
				
				//accuracy, completeness, correctness
				int oldSizePosFort = cpdefPositiveCovered.size();
				int oldSizeNegFort = cpdefNegativeCovered.size();
				
				cpdefPositiveCovered.removeAll(cpTest);
				cpdefNegativeCovered.removeAll(cnTest);
				
				int commonPos = oldSizePosFort - cpdefPositiveCovered.size();
				int commonNeg = oldSizeNegFort - cpdefNegativeCovered.size();
				
				
				int cpFort = cpTest.size() - commonPos;	//positive examples covered by fortified definition
				int cnFort = cnTest.size() - commonNeg;	//negative examples covered by fortified definition
				
				//correctness = un/negSize
				double blindFortificationCorrectness = 100 *  (curFoldNegTestSet.size() - cnFort)/(double)(curFoldNegTestSet.size());
				
				//completeness = cp/posSize
				double blindFortificationCompleteness = 100 * (cpFort)/(double)curFoldPosTestSet.size();
				
				//accuracy = (cp + un)/(pos + neg)
				double blindFortificationAccuracy = 100 * (cpFort + (curFoldNegTestSet.size() - cnFort))/
						(double)(curFoldPosTestSet.size() + curFoldNegTestSet.size());
				
				//precision = right positive classified / total positive classified
				//          = cp / (cp + negAsPos)
				double blindPrecission = (cpFort + cnFort) == 0 ? 0 : cpFort / (double)(cpFort + cnFort);
				
				//recall = right positive classified / total positive
				double blindRecall = cpFort / (double)curFoldPosTestSet.size();
				
				double blindFmeasure = 100 * Heuristics.getFScore(blindRecall, blindPrecission);
				
				//STAT values for Blind fortification
				correctnessBlindFortifyStat.addNumber(blindFortificationCorrectness);
				completenessBlindFortifyStat.addNumber(blindFortificationCompleteness);
				accuracyBlindFortifyStat.addNumber(blindFortificationAccuracy);
				fmeasureBlindFortifyStat.addNumber(blindFmeasure);
				
				//end of blind fortification
				
				//-----------------------------------------------------------------------------
				// JACCARD fortification
				// use Jaccard score to set the priority for the counter partial definitions
				//-----------------------------------------------------------------------------
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - JACCARD");
				outputWriter("---------------------------------------------");
				
				SortedSet<CELOE.PartialDefinition> jaccardFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(2));
				jaccardFortificationCpdef.addAll(counterPartialDefinitions);
				
				//visit all counter partial definitions
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : jaccardFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));					
				}
				*/
				
				
				outputWriter("*** Jaccard values count:");
				for (Long value : jaccardValueCount.keySet()) 
					outputWriter(df.format(value/1000d) + ": " + jaccardValueCount.get(value));

				
				//calculate jaccard fortification
				FortificationResult multiStepFortificationJaccard = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, jaccardFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, false);				
				for (int i=0; i<6; i++) {
					accuracyFortifyStepStat[1][i].addNumber(multiStepFortificationJaccard.fortificationAccuracy[i+1]);
					completenessFortifyStepStat[1][i].addNumber(multiStepFortificationJaccard.fortificationCompleteness[i+1]);
					correctnessFortifyStepStat[1][i].addNumber(multiStepFortificationJaccard.fortificationCorrectness[i+1]);					
					fmeasureFortifyStepStat[1][i].addNumber(multiStepFortificationJaccard.fortificationFmeasure[i+1]);
				}

				
				//---------------------------------------
				// Fortification - VALIDATION SET
				//---------------------------------------	

				outputWriter("---------------------------------------------");
				outputWriter("Fortification VALIDATION SET");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> trainingFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(3));

				trainingFortificationCpdef.addAll(counterPartialDefinitions);

				//print the counter partial definitions with score
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : trainingFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
				}
				*/
				
				//calculate the multi-step fortification accuracy
				FortificationResult multiStepFortificationTraining = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, trainingFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, false);
				for (int i=0; i<6; i++) {
					accuracyFortifyStepStat[2][i].addNumber(multiStepFortificationTraining.fortificationAccuracy[i+1]);
					completenessFortifyStepStat[2][i].addNumber(multiStepFortificationTraining.fortificationCompleteness[i+1]);
					correctnessFortifyStepStat[2][i].addNumber(multiStepFortificationTraining.fortificationCorrectness[i+1]);						
					fmeasureFortifyStepStat[2][i].addNumber(multiStepFortificationTraining.fortificationFmeasure[i+1]);
				}
				
				
				
				//------------------------------------------
				// Fortification - SIMILARITY_ALL Examples
				//------------------------------------------				
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - SIMILARITY");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> similarityFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(4));
				similarityFortificationCpdef.addAll(counterPartialDefinitions);

				
				//print the counter partial definitions with score
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : similarityFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
				}
				*/
				
				//calculate the multi-step fortification accuracy
				FortificationResult multiStepFortificationSimilarity = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, similarityFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, false);
				for (int i=0; i<6; i++) {
					accuracyFortifyStepStat[3][i].addNumber(multiStepFortificationSimilarity.fortificationAccuracy[i+1]);
					completenessFortifyStepStat[3][i].addNumber(multiStepFortificationSimilarity.fortificationCompleteness[i+1]);
					correctnessFortifyStepStat[3][i].addNumber(multiStepFortificationSimilarity.fortificationCorrectness[i+1]);						
					fmeasureFortifyStepStat[3][i].addNumber(multiStepFortificationSimilarity.fortificationFmeasure[i+1]);
				}
								
				
				//---------------------------------------
				// Fortification - SIMILARITY_POS-NEG
				//---------------------------------------				
				outputWriter("---------------------------------------------");
				outputWriter("Fortification - SIMILARITY_POS-NEG");
				outputWriter("---------------------------------------------");

				
				SortedSet<CELOE.PartialDefinition> similarityCombineFortificationCpdef = new TreeSet<CELOE.PartialDefinition>(new AdditionalValueComparator(7));
				similarityCombineFortificationCpdef.addAll(counterPartialDefinitions);

				
				//print the counter partial definitions with score
				/*
				c = 1;
				for (CELOE.PartialDefinition cpdef : similarityCombineFortificationCpdef) {
					outputWriter(c++ + ". " + this.getCpdefString(cpdef)
							+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
							+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
				}
				*/
				
				//calculate the multi-step fortification accuracy
				FortificationResult multiStepFortificationSimilarityCombine = Orthogonality.fortifyAccuracyMultiSteps(
						rs, concept, similarityCombineFortificationCpdef, curFoldPosTestSet, curFoldNegTestSet, false);
				for (int i=0; i<6; i++) {
					accuracyFortifyStepStat[4][i].addNumber(multiStepFortificationSimilarityCombine.fortificationAccuracy[i+1]);
					completenessFortifyStepStat[4][i].addNumber(multiStepFortificationSimilarityCombine.fortificationCompleteness[i+1]);
					correctnessFortifyStepStat[4][i].addNumber(multiStepFortificationSimilarityCombine.fortificationCorrectness[i+1]);						
					fmeasureFortifyStepStat[4][i].addNumber(multiStepFortificationSimilarityCombine.fortificationFmeasure[i+1]);
				}

				
				//------------------------------
				// Fortification - LABEL DATA				
				// 	LABLED TEST DATA
				//------------------------------
				//if there exists covered negative examples ==> check if there are any counter partial definitions 
				//can be used to remove covered negative examples
				
				int fixedNeg = 0;
				int fixedPos = 0;
				int noOfSelectedCpdef = 0;
				int totalSelectedCpdefLength = 0;
				double avgTrainingCoverageSelectedCpdef = 0;
				
				/**
				 * selected cpdef which are selected based on the test labled data
				 * given a set of wrong classified neg., select a set of cpdef to remove the wrong classified neg examples 
				 * the cpdef are sorted based on the training neg. example coverage
				 */
				TreeSet<CELOE.PartialDefinition> selectedCounterPartialDefinitions = new TreeSet<CELOE.PartialDefinition>(new CoverageComparator());
				
				if (cnTest.size() > 0) {
					
					TreeSet<Individual> tempCoveredNeg = new TreeSet<Individual>(new URIComparator());
					tempCoveredNeg.addAll(cnTest);
					
					TreeSet<Individual> tempUncoveredPos = new TreeSet<Individual>(new URIComparator());
					tempUncoveredPos.addAll(upTest);
					
					//check each counter partial definitions
					for (CELOE.PartialDefinition negCpdef : counterPartialDefinitions) {
						
						Description cpdef = negCpdef.getDescription();//.getChild(0);
						
						//set of neg examples covered by the counter partial definition
						Set<Individual> desCoveredNeg = new HashSet<Individual>(rs.hasType(cpdef, curFoldNegTestSet));
						
						//if the current counter partial definition can help to remove some neg examples
						//int oldNoOfCoveredNeg=tempCoveredNeg.size();
						if (tempCoveredNeg.removeAll(desCoveredNeg)) {
							
							//assign cn on test set to additionalValue
							selectedCounterPartialDefinitions.add(negCpdef);
							
							//check if it may remove some positive examples or not
							Set<Individual> desCoveredPos = new HashSet<Individual>(rs.hasType(cpdef, curFoldPosTestSet));
							tempUncoveredPos.addAll(desCoveredPos);
							
							//count the total number of counter partial definition selected and their total length
							noOfSelectedCpdef++;
							totalSelectedCpdefLength += cpdef.getLength();			
							avgTrainingCoverageSelectedCpdef += negCpdef.getCoverage();
						}
						
						if (tempCoveredNeg.size() == 0)
							break;
					}
					
					fixedNeg = cnTest.size() - tempCoveredNeg.size();
					fixedPos = tempUncoveredPos.size() - upTest.size();	
					avgTrainingCoverageSelectedCpdef /= noOfSelectedCpdef;
				}
				
				
				noOfLabelFortifySelectedCpdefStat.addNumber(noOfSelectedCpdef);				
				labelFortifyCpdefTrainingCoverageStat.addNumber(avgTrainingCoverageSelectedCpdef);			
				
				
				//-------------------------------
				//label fortify stat calculation
				//-------------------------------
				
				//def length
				double labelFortifyDefinitionLength = concept.getLength() + totalSelectedCpdefLength + noOfSelectedCpdef;	//-1 from the selected cpdef and +1 for NOT
				labelFortifiedDefinitionLengthStat.addNumber(labelFortifyDefinitionLength);
				
				double avgLabelFortifyDefinitionLength = 0;
				
				if (noOfSelectedCpdef > 0) {
					avgLabelFortifyDefinitionLength = (double)totalSelectedCpdefLength/noOfSelectedCpdef;				
					avgLabelCpdefLengthStat.addNumber(totalSelectedCpdefLength/(double)noOfSelectedCpdef);
				}
				
				//accuracy = test accuracy + fortification adjustment
				double labelFortifyAccuracy = 100 * ((double)(correctTestExamples + fixedNeg - fixedPos)/
						(curFoldPosTestSet.size() + curFoldNegTestSet.size()));				
				accuracyLabelFortifyStat.addNumber(labelFortifyAccuracy);
				
				//completeness
				double labelFortifyCompleteness = 100 * ((double)(correctTestPosClassified - fixedPos)/curFoldPosTestSet.size());
				completenessLabelFortifyStat.addNumber(labelFortifyCompleteness);
				
				//correctness
				double labelFortifyCorrectness = 100 * ((double)(correctTestNegClassified + fixedNeg)/curFoldNegTestSet.size());				
				correctnessLabelFortifyStat.addNumber(labelFortifyCorrectness);
								
				//precision, recall, f-measure
				double labelFortifyPrecision = 0.0;	//percent of correct pos examples in total pos examples classified (= correct pos classified + neg as pos)
				if (((correctTestPosClassified - fixedPos) + (cnTest.size() - fixedNeg)) > 0)
					labelFortifyPrecision = (double)(correctTestPosClassified - fixedPos)/
							(correctTestPosClassified - fixedPos + cnTest.size() - fixedNeg);	//tmp3: neg as pos <=> false pos
				
				double labelFortifyRecall = (double)(correctTestPosClassified - fixedPos) / curFoldPosTestSet.size();
				
				double labelFortifyFmeasure = 100 * Heuristics.getFScore(labelFortifyRecall, labelFortifyPrecision);
				fmeasureLabelFortifyStat.addNumber(labelFortifyFmeasure);
				
				
				jaccardValueCount.clear();
				outputWriter("\n*** Label fortify counter partial definitions: ");				
				c = 1;
				//output the selected counter partial definition information				
				if (noOfSelectedCpdef > 0) {										
					for (CELOE.PartialDefinition cpdef : selectedCounterPartialDefinitions) {
						
						outputWriter(c++ + ". " + this.getCpdefString(cpdef, baseURI, prefixes)
								+ ", cp=" + rs.hasType(cpdef.getDescription(), curFoldPosTestSet)
								+ ", cn=" + rs.hasType(cpdef.getDescription(), curFoldNegTestSet));
						
						
						long tmp_key = Math.round(cpdef.getAdditionValue(1) * 1000);	//jaccard value is hold by the 2nd element
						
						if (jaccardValueCount.containsKey(tmp_key)) {
							jaccardValueCount.put(tmp_key, jaccardValueCount.get(tmp_key)+1);
						}
						else
							jaccardValueCount.put(tmp_key, 1);
					}
					
					outputWriter("*** Jaccard values count for selected cpdefs:");
					for (Long value : jaccardValueCount.keySet()) 
						outputWriter(df.format(value/1000d) + ": " + jaccardValueCount.get(value));
					
				}				

				
				outputWriter("----------------------");
				
				int[] noOfCpdefMultiStep = Orthogonality.getMultiStepFortificationStep(counterPartialDefinitions.size());
				for (int i=0; i<6; i++) {
					noOfCpdefUsedMultiStepFortStat[i].addNumber(noOfCpdefMultiStep[i]);
					
				}

				//minimal value of 50% of the cpdef used in the fortification
				minOfHalfCpdef = (minOfHalfCpdef > noOfCpdefMultiStep[5]) ? noOfCpdefMultiStep[5] : minOfHalfCpdef;
				
				if (currFold == 0) {
					accuracyFullStep = new double[strategyNames.length][minOfHalfCpdef];	//these arrays contain full fortification information
					fmeasureFullStep = new double[strategyNames.length][minOfHalfCpdef];
				}
				
				
				for (int i=0; i<minOfHalfCpdef; i++) {
					//covarage strategy
					accuracyFullStep[0][i] += multiStepFortificationCoverage.fortificationAccuracyStepByStep[i];
					fmeasureFullStep[0][i] += multiStepFortificationCoverage.fortificationFmeasureStepByStep[i];
					
					//jaccard strategy
					accuracyFullStep[1][i] += multiStepFortificationJaccard.fortificationAccuracyStepByStep[i];
					fmeasureFullStep[1][i] += multiStepFortificationJaccard.fortificationFmeasureStepByStep[i];
					
					//fortification training
					accuracyFullStep[2][i] += multiStepFortificationTraining.fortificationAccuracyStepByStep[i];
					fmeasureFullStep[2][i] += multiStepFortificationTraining.fortificationFmeasureStepByStep[i];

					//similarity all
					accuracyFullStep[3][i] += multiStepFortificationSimilarity.fortificationAccuracyStepByStep[i];
					fmeasureFullStep[3][i] += multiStepFortificationSimilarity.fortificationFmeasureStepByStep[i];

					//similarity (pos-neg)
					accuracyFullStep[4][i] += multiStepFortificationSimilarityCombine.fortificationAccuracyStepByStep[i];
					fmeasureFullStep[4][i] += multiStepFortificationSimilarityCombine.fortificationFmeasureStepByStep[i];

				}
				
				
				//-----------------------------------
				//output stat. information
				//-----------------------------------
				outputWriter("Fold " + currFold + "/" + folds + ":");
				outputWriter("  concept: " + concept);
				
				outputWriter("  training: " + trainingCorrectPosClassified + "/" + trainingPosSize + 
						" positive and " + trainingCorrectNegClassified + "/" + trainingNegSize + " negative examples");				
				outputWriter("  testing: " + correctTestPosClassified + "/" + testingPosSize + " correct positives, " 
						+ correctTestNegClassified + "/" + testingNegSize + " correct negatives");
				
				
				//general learning statistics
				outputWriter("  runtime: " + df.format(algorithmDuration2/(double)1000000000) + "s");
				outputWriter("  learning time: " + df.format(learningMili2/(double)1000) + "s");
				outputWriter("  total number of descriptions: " + la.getTotalNumberOfDescriptionsGenerated());
				outputWriter("  total number pdef: " + noOfPdef + " (used by parcel: " + noOfUsedPdef + ")");
				outputWriter("  total number of cpdef: " + counterPartialDefinitions.size());
				
				
				//pdef + cpdef
				outputWriter("  def. length: " + df.format(concept.getLength()));
				outputWriter("  def. length label fortify: " + df.format(labelFortifyDefinitionLength));
				outputWriter("  avg. def. length label fortify: " + df.format(avgLabelFortifyDefinitionLength));				
				outputWriter("  total cpdef. length: " + df.format(totalCPDefLength));
				outputWriter("  avg. pdef length: " + df.format(avgPdefLength));
				outputWriter("  avg. cpdef. length: " + df.format(avgCPDefLength));				
				outputWriter("  avg. cpdef training coverage: " + statOutput(df, avgCpdefCoverageTrainingStat, "%"));

				outputWriter("  no of cpdef used in the multi-step fortification: " + arrayToString(noOfCpdefMultiStep));
				
				//f-measure
				outputWriter("  f-measure training set: " + df.format(currFmeasureTraining));
				outputWriter("  f-measure test set: " + df.format(currFmeasureTest));
				outputWriter("  f-measure on test set label fortification: " + df.format(labelFortifyFmeasure)); 
				outputWriter("  f-measure on test set blind fortification: " + df.format(blindFmeasure));
				
				
				//accuracy
				outputWriter("  accuracy test: " + df.format(currAccuracy) +  
						"% (corr:"+ df.format(testingCorrectness) + 
						"%, comp:" + df.format(testingCompleteness) + "%) --- " + 
						df.format(trainingAccuracy) + "% (corr:"+ trainingCorrectness + 
						"%, comp:" + df.format(trainingCompleteness) + "%) on training set");
				
				outputWriter("  accuracy label fortification: " + df.format(labelFortifyAccuracy) +
						"%, correctness: " + df.format(labelFortifyCorrectness) +
						"%, completeness: " + df.format(labelFortifyCompleteness) + "%");
				
				outputWriter("  accuracy blind fortification: " + df.format(blindFortificationAccuracy) +
						"%, correctness: " + df.format(blindFortificationCorrectness) +
						"%, completeness: " + df.format(blindFortificationCompleteness) + "%");
				
				
				outputWriter("  number of cpdef use in the label fortification: " + noOfSelectedCpdef);
				outputWriter("  avg. training coverage of the selected cpdef. in the label fortification: " + df.format(avgTrainingCoverageSelectedCpdef));

				
				outputWriter("Aggregate data from fold 0 to fold " + currFold + "/" + folds);
				outputWriter("  runtime: " + statOutput(df, runtime, "s"));
				outputWriter("  learning time parcelex: " + statOutput(df, learningTime, "s"));
				outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
				outputWriter("  avg. def. length: " + statOutput(df, length, ""));
				outputWriter("  avg. label fortified def. length: " + statOutput(df, labelFortifiedDefinitionLengthStat, ""));
				outputWriter("  avg. cpdef used in the label fortification: " + statOutput(df, avgLabelCpdefLengthStat, ""));
				outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
				outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
				outputWriter("  F-Measure on test set fortified: " + statOutput(df, fmeasureLabelFortifyStat, "%"));
				outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
						" -- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
				outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") +
						" -- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, testingCompletenessStat, "%"));				
				
				outputWriter("  label fortification accuracy on test set: " + statOutput(df, accuracyLabelFortifyStat, "%") +
						" -- fortified correctness: " + statOutput(df, correctnessLabelFortifyStat, "%") +
						"-- fortified completeness: " + statOutput(df, completenessLabelFortifyStat, "%"));

				outputWriter("  blind fortification accuracy on test set: " + statOutput(df, accuracyBlindFortifyStat, "%") +
						" -- fortified correctness: " + statOutput(df, correctnessBlindFortifyStat, "%") +
						"-- fortified completeness: " + statOutput(df, completenessBlindFortifyStat, "%"));

				for (int i=0; i< strategyNames.length; i++) {
					
					outputWriter("  multi-step fortified accuracy by " + strategyNames[i] + ":");
					
					outputWriter("\t 5%: " + statOutput(df, accuracyFortifyStepStat[i][0], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][0], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][0], "%")
							);
					outputWriter("\t 10%: " + statOutput(df, accuracyFortifyStepStat[i][1], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][1], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][1], "%")
							);
					outputWriter("\t 20%: " + statOutput(df, accuracyFortifyStepStat[i][2], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][2], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][2], "%")
							);
					outputWriter("\t 30%: " + statOutput(df, accuracyFortifyStepStat[i][3], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][3], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][3], "%")
							);
					outputWriter("\t 40%: " + statOutput(df, accuracyFortifyStepStat[i][4], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][4], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][4], "%")
							);
					outputWriter("\t 50%: " + statOutput(df, accuracyFortifyStepStat[i][5], "%")
							+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][5], "%")
							+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][5], "%")
							);
					
				}

				outputWriter("  total no of counter partial definition: " + statOutput(df, noOfCpdefStat, ""));
				outputWriter("  avg. no of counter partial definition used in label fortification: " + statOutput(df, noOfLabelFortifySelectedCpdefStat,""));
				
				outputWriter("  no of cpdef used in multi-step fortification:");
				outputWriter("\t5%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[0], ""));
				outputWriter("\t10%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[1], ""));
				outputWriter("\t20%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[2], ""));
				outputWriter("\t30%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[3], ""));
				outputWriter("\t40%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[4], ""));
				outputWriter("\t50%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[5], ""));
					

								
				outputWriter("----------------------");
				

				//sleep after each run (fer MBean collecting information purpose)
				try {
					Thread.sleep(5000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}

			}	//for k folds


			//---------------------------------
			//end of k-fold cross validation
			//output result of the k-fold 
			//---------------------------------

			//final cumulative statistical data of a run
			outputWriter("");
			outputWriter("Finished the " + (kk+1) + "/" + noOfRuns + " of " + folds + "-folds cross-validation.");
			outputWriter("  runtime: " + statOutput(df, runtime, "s"));
			outputWriter("  learning time parcelex: " + statOutput(df, learningTime, "s"));
			outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
			outputWriter("  avg. def. length: " + statOutput(df, length, ""));
			outputWriter("  avg. label fortified def. length: " + statOutput(df, labelFortifiedDefinitionLengthStat, ""));
			outputWriter("  avg. cpdef used in the label fortification: " + statOutput(df, avgLabelCpdefLengthStat, ""));
			outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
			outputWriter("  F-Measure on test set: " + statOutput(df, fMeasure, "%"));
			outputWriter("  F-Measure on test set fortified: " + statOutput(df, fmeasureLabelFortifyStat, "%"));
			outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
					"\n\t-- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
					"\n\t-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
			outputWriter("  predictive accuracy on test set: " + statOutput(df, accuracy, "%") +
					"\n\t-- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
					"\n\t-- completeness: " + statOutput(df, testingCompletenessStat, "%"));				
			
			outputWriter("  fortified accuracy on test set: " + statOutput(df, accuracyLabelFortifyStat, "%") +
					"\n\t-- fortified correctness: " + statOutput(df, correctnessLabelFortifyStat, "%") +
					"\n\t-- fortified completeness: " + statOutput(df, completenessLabelFortifyStat, "%"));

			outputWriter("  blind fortified accuracy on test set: " + statOutput(df, accuracyBlindFortifyStat, "%") +
					"\n\t-- fortified correctness: " + statOutput(df, correctnessBlindFortifyStat, "%") +
					"\n\t-- fortified completeness: " + statOutput(df, completenessBlindFortifyStat, "%"));

			for (int i=0; i< strategyNames.length; i++) {
				
				outputWriter("  multi-step fortified accuracy by " + strategyNames[i] + ":");
				
				outputWriter("\t 5%: " + statOutput(df, accuracyFortifyStepStat[i][0], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][0], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][0], "%")
						);
				outputWriter("\t 10%: " + statOutput(df, accuracyFortifyStepStat[i][1], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][1], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][1], "%")
						);
				outputWriter("\t 20%: " + statOutput(df, accuracyFortifyStepStat[i][2], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][2], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][2], "%")
						);
				outputWriter("\t 30%: " + statOutput(df, accuracyFortifyStepStat[i][3], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][3], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][3], "%")
						);
				outputWriter("\t 40%: " + statOutput(df, accuracyFortifyStepStat[i][4], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][4], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][4], "%")
						);
				outputWriter("\t 50%: " + statOutput(df, accuracyFortifyStepStat[i][5], "%")
						+ " -- correctness: " + statOutput(df, correctnessFortifyStepStat[i][5], "%")
						+ " -- completeness: " + statOutput(df, completenessFortifyStepStat[i][5], "%")
						);
				
			}

			outputWriter("  total no of counter partial definition: " + statOutput(df, noOfCpdefStat, ""));
			outputWriter("  avg. no of counter partial definition used in label fortification: " + statOutput(df, noOfLabelFortifySelectedCpdefStat,""));
			
			outputWriter("  no of cpdef used in multi-step fortification:");
			outputWriter("\t5%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[0], ""));
			outputWriter("\t10%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[1], ""));
			outputWriter("\t20%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[2], ""));
			outputWriter("\t30%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[3], ""));
			outputWriter("\t40%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[4], ""));
			outputWriter("\t50%: " + statOutput(df, noOfCpdefUsedMultiStepFortStat[5], ""));
			
			//-----------------------------------------
			//this is for copying to word document
			//-----------------------------------------
			
			outputWriter("\n======= RESULT SUMMARY =======");
			
			//fmeasure			
			outputWriter("\n***f-measure test/blind");
			outputWriter(df.format(fMeasure.getMean()) + "  " + df.format(fMeasure.getStandardDeviation())
				+ "\n" + df.format(fmeasureBlindFortifyStat.getMean()) + "  " + df.format(fmeasureBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, f-measure (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("fmeasure - " + strategyNames[i]);				
				for (int j=0; j<6; j++)
					outputWriter(df.format(fmeasureFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(fmeasureFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			
			//accuracy
			outputWriter("\n***accuracy test/blind");
			outputWriter(df.format(accuracy.getMean()) + "  " + df.format(accuracy.getStandardDeviation())
				+ "\n" + df.format(accuracyBlindFortifyStat.getMean()) + "  " + df.format(accuracyBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("accuracy - " + strategyNames[i]);				
				for (int j=0; j<6; j++)
					outputWriter(df.format(accuracyFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(accuracyFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			//correctness
			outputWriter("\n***correctness test/blind");
			outputWriter(df.format(testingCorrectnessStat.getMean()) + "  " + df.format(testingCorrectnessStat.getStandardDeviation())
				+ "\n" + df.format(correctnessBlindFortifyStat.getMean()) + "  " + df.format(correctnessBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("correctness - " + strategyNames[i]);				
				for (int j=0; j<6; j++)
					outputWriter(df.format(correctnessFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(correctnessFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			//completeness
			outputWriter("\n***completeness test/blind");
			outputWriter(df.format(testingCompletenessStat.getMean()) + "  " + df.format(testingCompletenessStat.getStandardDeviation())
				+ "\n" + df.format(correctnessBlindFortifyStat.getMean()) + "  " + df.format(correctnessBlindFortifyStat.getStandardDeviation())
				);
			
			//for each strategy: strategy name, accuracy (5-50%)
			for (int i=0; i<strategyNames.length; i++) {				
				outputWriter("completeness - " + strategyNames[i]);				
				for (int j=0; j<6; j++)
					outputWriter(df.format(completenessFortifyStepStat[i][j].getMean()) 
							+ "\n" + df.format(completenessFortifyStepStat[i][j].getStandardDeviation()));
			}
			
			
			outputWriter("======= Fmeasure full steps (of 50%) =======");
			for (int i=0; i<strategyNames.length; i++) {	//4 strategies
				outputWriter(strategyNames[i] + ": ");
				for (int j=0; j<minOfHalfCpdef; j++) {
					outputWriterNoNL(fmeasureFullStep[i][j]/10d + "\t");
				}
				outputWriterNoNL("\n");
			}
			
			
			outputWriter("======= Accuracy full steps (of 50%) =======");
			for (int i=0; i<strategyNames.length; i++) {	//4 strategies
				outputWriter(strategyNames[i] + ": ");
				for (int j=0; j<minOfHalfCpdef; j++) {
					outputWriterNoNL(accuracyFullStep[i][j]/10d + "\t");
				}
				outputWriterNoNL("\n");
			}


			
			if (noOfRuns > 1) {		
				//runtime
				runtimeAvg.addNumber(runtime.getMean());
				runtimeMax.addNumber(runtime.getMax());
				runtimeMin.addNumber(runtime.getMin());
				runtimeDev.addNumber(runtime.getStandardDeviation());
				
				//learning time
				learningTimeAvg.addNumber(learningTime.getMean());
				learningTimeDev.addNumber(learningTime.getStandardDeviation());
				learningTimeMax.addNumber(learningTime.getMax());
				learningTimeMin.addNumber(learningTime.getMin());		
	
				//number of partial definitions			
				noOfPartialDefAvg.addNumber(noOfPdefStat.getMean());
				noOfPartialDefMax.addNumber(noOfPdefStat.getMax());
				noOfPartialDefMin.addNumber(noOfPdefStat.getMin());
				noOfPartialDefDev.addNumber(noOfPdefStat.getStandardDeviation());
						
				//avg partial definition length
				avgPartialDefLenAvg.addNumber(avgPartialDefinitionLengthStat.getMean());
				avgPartialDefLenMax.addNumber(avgPartialDefinitionLengthStat.getMax());
				avgPartialDefLenMin.addNumber(avgPartialDefinitionLengthStat.getMin());
				avgPartialDefLenDev.addNumber(avgPartialDefinitionLengthStat.getStandardDeviation());
				
				avgFortifiedPartialDefLenAvg.addNumber(labelFortifiedDefinitionLengthStat.getMean());
				avgFortifiedPartialDefLenMax.addNumber(labelFortifiedDefinitionLengthStat.getMax());
				avgFortifiedPartialDefLenMin.addNumber(labelFortifiedDefinitionLengthStat.getMin());
				avgFortifiedPartialDefLenDev.addNumber(labelFortifiedDefinitionLengthStat.getStandardDeviation());
				
				
				defLenAvg.addNumber(length.getMean());			
				defLenMax.addNumber(length.getMax());
				defLenMin.addNumber(length.getMin());
				defLenDev.addNumber(length.getStandardDeviation());
				
				//counter partial definitions
				noOfCounterPartialDefinitionsAvg.addNumber(noOfCpdefStat.getMean());
				noOfCounterPartialDefinitionsDev.addNumber(noOfCpdefStat.getStandardDeviation());
				noOfCounterPartialDefinitionsMax.addNumber(noOfCpdefStat.getMax());
				noOfCounterPartialDefinitionsMin.addNumber(noOfCpdefStat.getMin());
				
				noOfCounterPartialDefinitionsUsedAvg.addNumber(noOfCpdefUsedStat.getMean());
				noOfCounterPartialDefinitionsUsedDev.addNumber(noOfCpdefUsedStat.getStandardDeviation());
				noOfCounterPartialDefinitionsUsedMax.addNumber(noOfCpdefUsedStat.getMax());
				noOfCounterPartialDefinitionsUsedMin.addNumber(noOfCpdefUsedStat.getMin());			
							
				//training accuracy
				trainingAccAvg.addNumber(accuracyTraining.getMean());			
				trainingAccDev.addNumber(accuracyTraining.getStandardDeviation());
				trainingAccMax.addNumber(accuracyTraining.getMax());
				trainingAccMin.addNumber(accuracyTraining.getMin());
				
				trainingCorAvg.addNumber(trainingCorrectnessStat.getMean());			
				trainingCorDev.addNumber(trainingCorrectnessStat.getStandardDeviation());
				trainingCorMax.addNumber(trainingCorrectnessStat.getMax());
				trainingCorMin.addNumber(trainingCorrectnessStat.getMin());
				
				trainingComAvg.addNumber(trainingCompletenessStat.getMean());
				trainingComDev.addNumber(trainingCompletenessStat.getStandardDeviation());
				trainingComMax.addNumber(trainingCompletenessStat.getMax());
				trainingComMin.addNumber(trainingCompletenessStat.getMin());
				
				testingAccAvg.addNumber(accuracy.getMean());
				testingAccMax.addNumber(accuracy.getMax());
				testingAccMin.addNumber(accuracy.getMin());
				testingAccDev.addNumber(accuracy.getStandardDeviation());
				
				//fortify accuracy
				fortifyAccAvg.addNumber(accuracyLabelFortifyStat.getMean());
				fortifyAccMax.addNumber(accuracyLabelFortifyStat.getMax());
				fortifyAccMin.addNumber(accuracyLabelFortifyStat.getMin());
				fortifyAccDev.addNumber(accuracyLabelFortifyStat.getStandardDeviation());
				
				
				testingCorAvg.addNumber(testingCorrectnessStat.getMean());
				testingCorDev.addNumber(testingCorrectnessStat.getStandardDeviation());
				testingCorMax.addNumber(testingCorrectnessStat.getMax());
				testingCorMin.addNumber(testingCorrectnessStat.getMin());
				
				//fortify correctness
				fortifyCorAvg.addNumber(correctnessLabelFortifyStat.getMean());
				fortifyCorMax.addNumber(correctnessLabelFortifyStat.getMax());
				fortifyCorMin.addNumber(correctnessLabelFortifyStat.getMin());
				fortifyCorDev.addNumber(correctnessLabelFortifyStat.getStandardDeviation());
								
				testingComAvg.addNumber(testingCompletenessStat.getMean());
				testingComDev.addNumber(testingCompletenessStat.getStandardDeviation());
				testingComMax.addNumber(testingCompletenessStat.getMax());
				testingComMin.addNumber(testingCompletenessStat.getMin());
				
				//fortify completeness (level 1 fixing does not change the completeness
				fortifyComAvg.addNumber(completenessLabelFortifyStat.getMean());
				fortifyComMax.addNumber(completenessLabelFortifyStat.getMax());
				fortifyComMin.addNumber(completenessLabelFortifyStat.getMin());
				fortifyComDev.addNumber(completenessLabelFortifyStat.getStandardDeviation());
				
				
				testingFMeasureAvg.addNumber(fMeasure.getMean());
				testingFMeasureDev.addNumber(fMeasure.getStandardDeviation());
				testingFMeasureMax.addNumber(fMeasure.getMax());
				testingFMeasureMin.addNumber(fMeasure.getMin());	
							
				trainingFMeasureAvg.addNumber(fMeasureTraining.getMean());
				trainingFMeasureDev.addNumber(fMeasureTraining.getStandardDeviation());
				trainingFMeasureMax.addNumber(fMeasureTraining.getMax());
				trainingFMeasureMin.addNumber(fMeasureTraining.getMin());
				
				fortifyFmeasureAvg.addNumber(fmeasureLabelFortifyStat.getMean());
				fortifyFmeasureMax.addNumber(fmeasureLabelFortifyStat.getMax());
				fortifyFmeasureMin.addNumber(fmeasureLabelFortifyStat.getMin());
				fortifyFmeasureDev.addNumber(fmeasureLabelFortifyStat.getStandardDeviation());
								
				noOfDescriptionsAgv.addNumber(totalNumberOfDescriptions.getMean());
				noOfDescriptionsMax.addNumber(totalNumberOfDescriptions.getMax());
				noOfDescriptionsMin.addNumber(totalNumberOfDescriptions.getMin());
				noOfDescriptionsDev.addNumber(totalNumberOfDescriptions.getStandardDeviation());
			}
			
		}	//for kk folds
		
		if (noOfRuns > 1) {
	
			outputWriter("");
			outputWriter("Finished " + noOfRuns + " time(s) of the " + folds + "-folds cross-validations");
			
			outputWriter("runtime: " + 
					"\n\t avg.: " + statOutput(df, runtimeAvg, "s") +
					"\n\t dev.: " + statOutput(df, runtimeDev, "s") +
					"\n\t max.: " + statOutput(df, runtimeMax, "s") +
					"\n\t min.: " + statOutput(df, runtimeMin, "s"));
			
			outputWriter("learning time: " + 
					"\n\t avg.: " + statOutput(df, learningTimeAvg, "s") +
					"\n\t dev.: " + statOutput(df, learningTimeDev, "s") +
					"\n\t max.: " + statOutput(df, learningTimeMax, "s") +
					"\n\t min.: " + statOutput(df, learningTimeMin, "s"));
					
			outputWriter("no of descriptions: " + 
					"\n\t avg.: " + statOutput(df, noOfDescriptionsAgv, "") +
					"\n\t dev.: " + statOutput(df, noOfDescriptionsDev, "") +
					"\n\t max.: " + statOutput(df, noOfDescriptionsMax, "") +
					"\n\t min.: " + statOutput(df, noOfDescriptionsMin, ""));
			
			outputWriter("number of partial definitions: " + 
					"\n\t avg.: " + statOutput(df, noOfPartialDefAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfPartialDefDev, "") +
					"\n\t max.: " + statOutput(df, noOfPartialDefMax, "") +
					"\n\t min.: " + statOutput(df, noOfPartialDefMin, ""));
			
			outputWriter("avg. partial definition length: " + 
					"\n\t avg.: " + statOutput(df, avgPartialDefLenAvg, "") + 				
					"\n\t dev.: " + statOutput(df, avgPartialDefLenDev, "") +
					"\n\t max.: " + statOutput(df, avgPartialDefLenMax, "") +
					"\n\t min.: " + statOutput(df, avgPartialDefLenMin, ""));
			
			outputWriter("definition length: " + 
					"\n\t avg.: " + statOutput(df, defLenAvg, "") +
					"\n\t dev.: " + statOutput(df, defLenDev, "") +
					"\n\t max.: " + statOutput(df, defLenMax, "") +
					"\n\t min.: " + statOutput(df, defLenMin, ""));
			
			outputWriter("number of counter partial definitions: " + 
					"\n\t avg.: " + statOutput(df, noOfCounterPartialDefinitionsAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfCounterPartialDefinitionsDev, "") +
					"\n\t max.: " + statOutput(df, noOfCounterPartialDefinitionsMax, "") +
					"\n\t min.: " + statOutput(df, noOfCounterPartialDefinitionsMin, ""));
			
			outputWriter("number of counter partial definitions used: " + 
					"\n\t avg.: " + statOutput(df, noOfCounterPartialDefinitionsUsedAvg, "") +
					"\n\t dev.: " + statOutput(df, noOfCounterPartialDefinitionsUsedDev, "") +
					"\n\t max.: " + statOutput(df, noOfCounterPartialDefinitionsUsedMax, "") +
					"\n\t min.: " + statOutput(df, noOfCounterPartialDefinitionsUsedMin, ""));
			
			outputWriter("accuracy on training set:" + 
					"\n\t avg.: " + statOutput(df, trainingAccAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingAccDev, "%") + 
					"\n\t max.: " + statOutput(df, trainingAccMax, "%") +
					"\n\t min.: " + statOutput(df, trainingAccMin, "%"));
			
			outputWriter("correctness on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingCorAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingCorDev, "%") +
					"\n\t max.: " + statOutput(df, trainingCorMax, "%") +
					"\n\t min.: " + statOutput(df, trainingCorMin, "%"));
			
			outputWriter("completeness on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingComAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingCorDev, "%") +
					"\n\t max.: " + statOutput(df, trainingComMax, "%") +
					"\n\t min.: " + statOutput(df, trainingComMin, "%"));
			
			outputWriter("FMeasure on training set: " + 
					"\n\t avg.: " + statOutput(df, trainingFMeasureAvg, "%") +
					"\n\t dev.: " + statOutput(df, trainingFMeasureDev, "%") +
					"\n\t max.: " + statOutput(df, trainingFMeasureMax, "%") +
					"\n\t min.: " + statOutput(df, trainingFMeasureMin, "%"));
			
			outputWriter("accuracy on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingAccAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingAccDev, "%") +
					"\n\t max.: " + statOutput(df, testingAccMax, "%") +
					"\n\t min.: " + statOutput(df, testingAccMin, "%"));
			
			outputWriter("correctness on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingCorAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingCorDev, "%") +
					"\n\t max.: " + statOutput(df, testingCorMax, "%") +
					"\n\t min.: " + statOutput(df, testingCorMin, "%"));
			
			outputWriter("completeness on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingComAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingComDev, "%") +
					"\n\t max.: " + statOutput(df, testingComMax, "%") +
					"\n\t min.: " + statOutput(df, testingComMin, "%"));
			
			outputWriter("FMeasure on testing set: " + 
					"\n\t avg.: " + statOutput(df, testingFMeasureAvg, "%") +
					"\n\t dev.: " + statOutput(df, testingFMeasureDev, "%") +
					"\n\t max.: " + statOutput(df, testingFMeasureMax, "%") +
					"\n\t min.: " + statOutput(df, testingFMeasureMin, "%"));
			

		}

		
		//reset the set of positive and negative examples for the learning problem for further experiment if any 
		lp.setPositiveExamples(posExamples);
		lp.setNegativeExamples(negExamples);
		

	}	//constructor


	/*
	private String getOrderUnit(int order) {
		switch (order) {
			case 1: return "st";
			case 2: return "nd";
			case 3: return "rd";
			default: return "th";
		}
	}
	*/

	@Override
	protected void outputWriter(String output) {
		logger.info(output);

		if (writeToFile)
			Files.appendToFile(outputFile, output + "\n");
	}
	
	
	class URIComparator implements Comparator<Individual> {

		@Override
		public int compare(Individual o1, Individual o2) {
			return o1.getURI().compareTo(o2.getURI());
		}
		
	}
	
	
	/*	
	class ParCELExtraNodeNegCoverageComparator implements Comparator<ParCELExtraNode> {

		@Override
		public int compare(ParCELExtraNode node1, ParCELExtraNode node2) {
			int coverage1 = node1.getCoveredNegativeExamples().size();
			int coverage2 = node2.getCoveredNegativeExamples().size();
			
			if (coverage1 > coverage2)
				return -1;
			else if (coverage1 < coverage2)
				return 1;
			else
				return new ConceptComparator().compare(node1.getDescription(), node2.getDescription());
				
		}
		
	}
	*/



	class CoverageComparator implements Comparator<CELOE.PartialDefinition> { 
		@Override
		public int compare(CELOE.PartialDefinition p1, CELOE.PartialDefinition p2) {
			if (p1.getCoverage() > p2.getCoverage())
				return -1;
			else if (p1.getCoverage() < p2.getCoverage())
				return 1;
			else
				return new ConceptComparator().compare(p1.getDescription(), p2.getDescription());
				
		}
	}
	
	
	class AdditionalValueComparator implements Comparator<CELOE.PartialDefinition> {		
		int index = 0;
		
		public AdditionalValueComparator(int index) {
			this.index = index;
		}
		
		@Override
		public int compare(PartialDefinition pdef1, PartialDefinition pdef2) {
			if (pdef1.getAdditionValue(index) > pdef2.getAdditionValue(index))
				return -1;
			else if (pdef1.getAdditionValue(index) < pdef2.getAdditionValue(index))
				return 1;
			else
				return new ConceptComparator().compare(pdef1.getDescription(), pdef2.getDescription());
			
		}
		
	}
	
	
	private String getCpdefString(CELOE.PartialDefinition cpdef, String baseURI, Map<String, String> prefixes) {
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
	 * Convert an array of double that contains accuracy/completeness/correctness into a string (from the 1st..6th elements)
	 * 
	 * @param df Decimal formatter 
	 * @param arr Array of double (7 elements)
	 * 
	 * @return A string of double values
	 */
	private String arrayToString(int[] arr) {
		String result = "[" + arr[0];
		
		for (int i=1; i<arr.length; i++)
			result += (";" + arr[i]);
		
		result += "]";
		
		return result;
	}
}
