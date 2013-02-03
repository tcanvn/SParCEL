/**
 * Copyright (C) 2007-2008, Jens Lehmann
 *
 * This file is part of DL-Learner.
 * 
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.dllearner.cli;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.learningproblems.Heuristics;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.datastructures.Datastructures;
import org.dllearner.utilities.statistics.Stat;
import org.dllearner.utilities.Files;

/**
 * Performs cross validation for the given problem. Supports k-fold cross-validation and
 * leave-one-out cross-validation.
 * 
 * @author Jens Lehmann
 * 
 */
public class CrossValidation {

	// statistical values
	protected Stat runtime;
	protected Stat accuracy;
	protected Stat length;	
	protected Stat fMeasure;
	
	protected static boolean writeToFile = false;
	protected static File outputFile;

	
	protected Stat fMeasureTraining;
	protected Stat accuracyTraining;
	protected Stat trainingCompletenessStat;
	protected Stat trainingCorrectnessStat;

	protected Stat testingCompletenessStat;
	protected Stat testingCorrectnessStat;
	
	protected Stat totalNumberOfDescriptions;
	
	Logger logger = Logger.getLogger(this.getClass());

	public CrossValidation() {

	}
	
	public CrossValidation(AbstractCELA la, PosNegLP lp, AbstractReasonerComponent rs, int folds,
			boolean leaveOneOut) {

		this(la, lp, rs, folds, leaveOneOut, 1);

	}

	public CrossValidation(AbstractCELA la, PosNegLP lp, AbstractReasonerComponent rs, int folds,
			boolean leaveOneOut, int noOfRuns) {

		DecimalFormat df = new DecimalFormat();

		// the training and test sets used later on
		List<Set<Individual>> trainingSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> trainingSetsNeg = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsPos = new LinkedList<Set<Individual>>();
		List<Set<Individual>> testSetsNeg = new LinkedList<Set<Individual>>();

		// get examples and shuffle them too
		Set<Individual> posExamples = ((PosNegLP) lp).getPositiveExamples();
		List<Individual> posExamplesList = new LinkedList<Individual>(posExamples);
		Collections.shuffle(posExamplesList, new Random(1));
		Set<Individual> negExamples = ((PosNegLP) lp).getNegativeExamples();
		List<Individual> negExamplesList = new LinkedList<Individual>(negExamples);
		Collections.shuffle(negExamplesList, new Random(2));

		// sanity check whether nr. of folds makes sense for this benchmark
		if (!leaveOneOut && (posExamples.size() < folds && negExamples.size() < folds)) {
			System.out.println("The number of folds is higher than the number of "
					+ "positive/negative examples. This can result in empty test sets. Exiting.");
			System.exit(0);
		}

		if (leaveOneOut) {
			// note that leave-one-out is not identical to k-fold with
			// k = nr. of examples in the current implementation, because
			// with n folds and n examples there is no guarantee that a fold
			// is never empty (this is an implementation issue)
			int nrOfExamples = posExamples.size() + negExamples.size();
			for (int i = 0; i < nrOfExamples; i++) {
				// ...
			}
			System.out.println("Leave-one-out not supported yet.");
			System.exit(1);
		} else {
			// calculating where to split the sets, ; note that we split
			// positive and negative examples separately such that the
			// distribution of positive and negative examples remains similar
			// (note that there are better but more complex ways to implement this,
			// which guarantee that the sum of the elements of a fold for pos
			// and neg differs by at most 1 - it can differ by 2 in our implementation,
			// e.g. with 3 folds, 4 pos. examples, 4 neg. examples)
			int[] splitsPos = calculateSplits(posExamples.size(), folds);
			int[] splitsNeg = calculateSplits(negExamples.size(), folds);

			// System.out.println(splitsPos[0]);
			// System.out.println(splitsNeg[0]);

			// calculating training and test sets
			for (int i = 0; i < folds; i++) {
				Set<Individual> testPos = getTestingSet(posExamplesList, splitsPos, i);
				Set<Individual> testNeg = getTestingSet(negExamplesList, splitsNeg, i);
				testSetsPos.add(i, testPos);
				testSetsNeg.add(i, testNeg);
				trainingSetsPos.add(i, getTrainingSet(posExamples, testPos));
				trainingSetsNeg.add(i, getTrainingSet(negExamples, testNeg));
			}

		}

		// ---------------------------------
		// k-fold cross validation
		// ---------------------------------

		Stat runtimeAvg = new Stat();
		Stat runtimeMax = new Stat();
		Stat runtimeMin = new Stat();
		Stat runtimeDev = new Stat();

		Stat defLenAvg = new Stat();
		Stat defLenDev = new Stat();
		Stat defLenMax = new Stat();
		Stat defLenMin = new Stat();

		Stat trainingAccAvg = new Stat();
		Stat trainingAccDev = new Stat();
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

		Stat testingCorAvg = new Stat();
		Stat testingCorDev = new Stat();
		Stat testingCorMax = new Stat();
		Stat testingCorMin = new Stat();

		Stat testingComAvg = new Stat();
		Stat testingComDev = new Stat();
		Stat testingComMax = new Stat();
		Stat testingComMin = new Stat();
		
		Stat testingFMesureAvg = new Stat();
		Stat testingFMesureDev = new Stat();
		Stat testingFMesureMax = new Stat();
		Stat testingFMesureMin = new Stat();
		
		Stat trainingFMesureAvg = new Stat();
		Stat trainingFMesureDev = new Stat();
		Stat trainingFMesureMax = new Stat();
		Stat trainingFMesureMin = new Stat();
		
		Stat noOfDescriptionsAgv = new Stat();
		Stat noOfDescriptionsMax = new Stat();
		Stat noOfDescriptionsMin = new Stat();
		Stat noOfDescriptionsDev = new Stat();

		for (int kk = 0; kk < noOfRuns; kk++) {

			// runtime
			runtime = new Stat();
			length = new Stat();
			accuracyTraining = new Stat();
			trainingCorrectnessStat = new Stat();
			trainingCompletenessStat = new Stat();
			accuracy = new Stat();
			testingCorrectnessStat = new Stat();
			testingCompletenessStat = new Stat();
			
			fMeasureTraining = new Stat();
			fMeasure = new Stat();
			totalNumberOfDescriptions = new Stat();

			// run the algorithm
			for (int currFold = 0; currFold < folds; currFold++) {

				Set<String> pos = Datastructures.individualSetToStringSet(trainingSetsPos
						.get(currFold));
				Set<String> neg = Datastructures.individualSetToStringSet(trainingSetsNeg
						.get(currFold));
				lp.setPositiveExamples(trainingSetsPos.get(currFold));
				lp.setNegativeExamples(trainingSetsNeg.get(currFold));

				try {
					lp.init();
					la.init();
				} catch (ComponentInitException e) {
					e.printStackTrace();
				}

				long algorithmStartTime = System.nanoTime();
				la.start();
				long algorithmDuration = System.nanoTime() - algorithmStartTime;
				runtime.addNumber(algorithmDuration / (double) 1000000000);

				Description concept = la.getCurrentlyBestDescription();

				Set<Individual> tmp = rs.hasType(concept, testSetsPos.get(currFold));
				Set<Individual> tmp2 = Helper.difference(testSetsPos.get(currFold), tmp);
				Set<Individual> tmp3 = rs.hasType(concept, testSetsNeg.get(currFold));

				outputWriter("test set errors pos: " + tmp2);
				outputWriter("test set errors neg: " + tmp3);

				// calculate training accuracies
				int trainingCorrectPosClassified = getCorrectPosClassified(rs, concept,
						trainingSetsPos.get(currFold));
				int trainingCorrectNegClassified = getCorrectNegClassified(rs, concept,
						trainingSetsNeg.get(currFold));
				int trainingCorrectExamples = trainingCorrectPosClassified
						+ trainingCorrectNegClassified;
				double trainingAccuracy = 100 * ((double) trainingCorrectExamples / (trainingSetsPos
						.get(currFold).size() + trainingSetsNeg.get(currFold).size()));
				
				accuracyTraining.addNumber(trainingAccuracy);
				
		
				
				double trainingCompleteness = 100*(double)trainingCorrectPosClassified/trainingSetsPos.get(currFold).size();
				double trainingCorrectness = 100*(double)trainingCorrectNegClassified/trainingSetsNeg.get(currFold).size();
				
				trainingCompletenessStat.addNumber(trainingCompleteness);
				trainingCorrectnessStat.addNumber(trainingCorrectness);

				
				// calculate test accuracies
				int correctPosClassified = getCorrectPosClassified(rs, concept,
						testSetsPos.get(currFold));
				int correctNegClassified = getCorrectNegClassified(rs, concept,
						testSetsNeg.get(currFold));
				int correctExamples = correctPosClassified + correctNegClassified;
				double currAccuracy = 100 * ((double) correctExamples / (testSetsPos.get(currFold)
						.size() + testSetsNeg.get(currFold).size()));
				accuracy.addNumber(currAccuracy);
				
				double testingCompleteness = 100*(double)correctPosClassified/testSetsPos.get(currFold).size();
				double testingCorrectness = 100*(double)correctNegClassified/testSetsNeg.get(currFold).size();
				
				testingCompletenessStat.addNumber(testingCompleteness);
				testingCorrectnessStat.addNumber(testingCorrectness);
				
				// calculate training F-Score
				int negAsPosTraining = rs.hasType(concept, trainingSetsNeg.get(currFold)).size();
				double precisionTraining = trainingCorrectPosClassified + negAsPosTraining == 0 ? 0
						: trainingCorrectPosClassified
								/ (double) (trainingCorrectPosClassified + negAsPosTraining);
				double recallTraining = trainingCorrectPosClassified
						/ (double) trainingSetsPos.get(currFold).size();
				double fMeasureTrainingFold = 100 * Heuristics.getFScore(recallTraining,
						precisionTraining); 
				fMeasureTraining.addNumber(fMeasureTrainingFold);
				
				// calculate test F-Score
				int negAsPos = rs.hasType(concept, testSetsNeg.get(currFold)).size();
				double precision = correctPosClassified + negAsPos == 0 ? 0 : correctPosClassified
						/ (double) (correctPosClassified + negAsPos);
				double recall = correctPosClassified / (double) testSetsPos.get(currFold).size();
				// System.out.println(precision);System.out.println(recall);
				double fMeasureTestingFold = 100 * Heuristics.getFScore(recall, precision); 
				fMeasure.addNumber(fMeasureTestingFold);

				length.addNumber(concept.getLength());
				
				totalNumberOfDescriptions.addNumber(la.getTotalNumberOfDescriptionsGenerated());

				outputWriter("Fold " + currFold + ":");
				outputWriter("  training: " + pos.size() + " positive and " + neg.size()
						+ " negative examples");
				outputWriter("  testing: " + correctPosClassified + "/"
						+ testSetsPos.get(currFold).size() + " correct positives, "
						+ correctNegClassified + "/" + testSetsNeg.get(currFold).size()
						+ " correct negatives");
				outputWriter("  concept: " + concept);
				outputWriter("  accuracy: " + df.format(currAccuracy) + "% ("
						+ "corr:" + df.format(testingCorrectness)
						+ "%, comp:" + df.format(testingCompleteness)
						+ "%)  -- training: " + df.format(trainingAccuracy)
						+ "% (corr:" + df.format(trainingCorrectness)
						+ "%, comp:" + df.format(trainingCompleteness)
						+ "%)");
				outputWriter("  F-Measure on training set: " + df.format(fMeasureTrainingFold));
				outputWriter("  F-Measure on testing set: " + df.format(fMeasureTestingFold));
				outputWriter("  length: " + df.format(concept.getLength()));
				outputWriter("  runtime: " + df.format(algorithmDuration / (double) 1000000000)	+ "s");				
				outputWriter("  total number of descriptions: " + la.getTotalNumberOfDescriptionsGenerated());
				
				outputWriter("----------");
				outputWriter("Aggregate data from fold 0 to fold " + currFold);
				outputWriter("  runtime: " + statOutput(df, runtime, "s"));
				outputWriter("  no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
				outputWriter("  length: " + statOutput(df, length, ""));
				outputWriter("  F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
				outputWriter("  F-Measure: " + statOutput(df, fMeasure, "%"));
				outputWriter("  predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
						" -- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
				outputWriter("  predictive accuracy on testing set: " + statOutput(df, accuracy, "%") +
						" -- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
						"-- completeness: " + statOutput(df, testingCompletenessStat, "%"));
				outputWriter("----------");
				//sleep after each run (fer MBean collecting information purpose)
				try {
					Thread.sleep(5000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}

			}	//k-fold cross validation

			outputWriter("");
			outputWriter("Finished " + (kk+1) + "/" + noOfRuns + " of " + folds + "-folds cross-validation.");
			outputWriter("runtime: " + statOutput(df, runtime, "s"));
			outputWriter("no of descriptions: " + statOutput(df, totalNumberOfDescriptions, ""));
			outputWriter("length: " + statOutput(df, length, ""));
			outputWriter("F-Measure on training set: " + statOutput(df, fMeasureTraining, "%"));
			outputWriter("F-Measure: " + statOutput(df, fMeasure, "%"));
			outputWriter("predictive accuracy on training set: " + statOutput(df, accuracyTraining, "%") + 
					" -- correctness: " + statOutput(df, trainingCorrectnessStat, "%") +
					"-- completeness: " + statOutput(df, trainingCompletenessStat, "%"));
			outputWriter("predictive accuracy on testing set: " + statOutput(df, accuracy, "%") +
					" -- correctness: " + statOutput(df, testingCorrectnessStat, "%") +
					"-- completeness: " + statOutput(df, testingCompletenessStat, "%"));
			// runtime
			runtimeAvg.addNumber(runtime.getMean());
			runtimeMax.addNumber(runtime.getMax());
			runtimeMin.addNumber(runtime.getMin());
			runtimeDev.addNumber(runtime.getStandardDeviation());

			defLenAvg.addNumber(length.getMean());
			defLenMax.addNumber(length.getMax());
			defLenMin.addNumber(length.getMin());
			defLenDev.addNumber(length.getStandardDeviation());

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

			testingCorAvg.addNumber(testingCorrectnessStat.getMean());
			testingCorDev.addNumber(testingCorrectnessStat.getStandardDeviation());
			testingCorMax.addNumber(testingCorrectnessStat.getMax());
			testingCorMin.addNumber(testingCorrectnessStat.getMin());

			testingComAvg.addNumber(testingCompletenessStat.getMean());
			testingComDev.addNumber(testingCompletenessStat.getStandardDeviation());
			testingComMax.addNumber(testingCompletenessStat.getMax());
			testingComMin.addNumber(testingCompletenessStat.getMin());
			
			testingFMesureAvg.addNumber(fMeasure.getMean());
			testingFMesureDev.addNumber(fMeasure.getStandardDeviation());
			testingFMesureMax.addNumber(fMeasure.getMax());
			testingFMesureMin.addNumber(fMeasure.getMin());
						
			trainingFMesureAvg.addNumber(fMeasureTraining.getMean());
			trainingFMesureDev.addNumber(fMeasureTraining.getStandardDeviation());
			trainingFMesureMax.addNumber(fMeasureTraining.getMax());
			trainingFMesureMin.addNumber(fMeasureTraining.getMin());
			
			noOfDescriptionsAgv.addNumber(totalNumberOfDescriptions.getMean());
			noOfDescriptionsMax.addNumber(totalNumberOfDescriptions.getMax());
			noOfDescriptionsMin.addNumber(totalNumberOfDescriptions.getMin());
			noOfDescriptionsDev.addNumber(totalNumberOfDescriptions.getStandardDeviation());
		} // for kk folds

		outputWriter("");
		outputWriter("Finished " + +noOfRuns + " time(s) of the " + folds + "-folds cross-validations");

		outputWriter("runtime: " + 				
				"\n\t avg.: " + statOutput(df, runtimeAvg, "s") +
				"\n\t dev.: " + statOutput(df, runtimeDev, "s") +
				"\n\t max.: " + statOutput(df, runtimeMax, "s") + 
				"\n\t min.: " + statOutput(df, runtimeMin, "s"));
		

		outputWriter("no of descriptions: " + 
				"\n\t avg.: " + statOutput(df, noOfDescriptionsAgv, "") +
				"\n\t dev.: " + statOutput(df, noOfDescriptionsDev, "") +
				"\n\t max.: " + statOutput(df, noOfDescriptionsMax, "") +
				"\n\t min.: " + statOutput(df, noOfDescriptionsMin, ""));

		outputWriter("definition length: " + 
				"\n\t avg.: " + statOutput(df, defLenAvg, "") + 
				"\n\t dev.: " + statOutput(df, defLenDev, "") +
				"\n\t max.: " + statOutput(df, defLenMax, "") + 
				"\n\t min.: " + statOutput(df, defLenMin, ""));

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
				"\n\t dev.: " + statOutput(df, trainingComDev, "%") +
				"\n\t max.: " + statOutput(df, trainingComMax, "%") + 
				"\n\t min.: " + statOutput(df, trainingComMin, "%"));
		
		outputWriter("FMesure on training set: " + 
				"\n\t avg.: " + statOutput(df, trainingFMesureAvg, "%") +
				"\n\t dev.: " + statOutput(df, trainingFMesureDev, "%") +
				"\n\t max.: " + statOutput(df, trainingFMesureMax, "%") +
				"\n\t min.: " + statOutput(df, trainingFMesureMin, "%"));

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
		
		outputWriter("FMesure on testing set: " + 
				"\n\t avg.: " + statOutput(df, testingFMesureAvg, "%") +
				"\n\t dev.: " + statOutput(df, testingFMesureDev, "%") +
				"\n\t max.: " + statOutput(df, testingFMesureMax, "%") +
				"\n\t min.: " + statOutput(df, testingFMesureMin, "%"));

	}

	public static int getCorrectPosClassified(AbstractReasonerComponent rs, Description concept,
			Set<Individual> testSetPos) {
		return rs.hasType(concept, testSetPos).size();
	}

	public static int getCorrectNegClassified(AbstractReasonerComponent rs, Description concept,
			Set<Individual> testSetNeg) {
		return testSetNeg.size() - rs.hasType(concept, testSetNeg).size();
	}

	public static Set<Individual> getTestingSet(List<Individual> examples, int[] splits, int fold) {
		int fromIndex;
		// we either start from 0 or after the last fold ended
		if (fold == 0)
			fromIndex = 0;
		else
			fromIndex = splits[fold - 1];
		// the split corresponds to the ends of the folds
		int toIndex = splits[fold];

		// System.out.println("from " + fromIndex + " to " + toIndex);

		Set<Individual> testingSet = new HashSet<Individual>();
		// +1 because 2nd element is exclusive in subList method
		testingSet.addAll(examples.subList(fromIndex, toIndex));
		return testingSet;
	}

	public static Set<Individual> getTrainingSet(Set<Individual> examples,
			Set<Individual> testingSet) {
		return Helper.difference(examples, testingSet);
	}

	// takes nr. of examples and the nr. of folds for this examples;
	// returns an array which says where each fold ends, i.e.
	// splits[i] is the index of the last element of fold i in the examples
	public static int[] calculateSplits(int nrOfExamples, int folds) {
		int[] splits = new int[folds];
		for (int i = 1; i <= folds; i++) {
			// we always round up to the next integer
			splits[i - 1] = (int) Math.ceil(i * nrOfExamples / (double) folds);
		}
		return splits;
	}

	public static String statOutput(DecimalFormat df, Stat stat, String unit) {
		
		if (stat.getCount() > 0) {		
			String str = "av. " + df.format(stat.getMean()) + unit;
			str += " (deviation " + df.format(stat.getStandardDeviation()) + unit + "; ";
			str += "min " + df.format(stat.getMin()) + unit + "; ";
			str += "max " + df.format(stat.getMax()) + unit + ")";
			return str;
		}
		else 
			return "N/A";
	}

	public Stat getAccuracy() {
		return accuracy;
	}

	public Stat getLength() {
		return length;
	}

	public Stat getRuntime() {
		return runtime;
	}

	
	protected void outputWriter(String output) {
		logger.info(output);

		if (writeToFile)
			Files.appendToFile(outputFile, output + "\n");
	}
	

	protected void outputWriterNoNL(String output) {
		logger.info(output);

		if (writeToFile)
			Files.appendToFile(outputFile, output);
	}


	public Stat getfMeasure() {
		return fMeasure;
	}

	public Stat getfMeasureTraining() {
		return fMeasureTraining;
	}

}
