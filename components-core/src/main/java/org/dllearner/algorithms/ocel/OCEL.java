/**
 * Copyright (C) 2007-2011, Jens Lehmann
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
 */

package org.dllearner.algorithms.ocel;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.options.BooleanConfigOption;
import org.dllearner.core.options.CommonConfigOptions;
import org.dllearner.core.options.ConfigOption;
import org.dllearner.core.options.DoubleConfigOption;
import org.dllearner.core.options.IntegerConfigOption;
import org.dllearner.core.options.StringConfigOption;
import org.dllearner.core.owl.ClassHierarchy;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.learningproblems.EvaluatedDescriptionPosNeg;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.learningproblems.PosNegLPStandard;
import org.dllearner.learningproblems.PosOnlyLP;
import org.dllearner.learningproblems.ScorePosNeg;
import org.dllearner.reasoning.ReasonerType;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The DL-Learner learning algorithm component for the example
 * based refinement operator approach. It handles all 
 * configuration options, creates the corresponding objects and
 * passes them to the actual refinement operator, heuristic, and
 * learning algorithm implementations.
 * 
 * Note: The options supported by the ROLearner component and this
 * one are not equal. Options that have been dropped for now:
 * - horizontal expansion factor: The goal of the algorithm will
 *     be to (hopefully) be able to learn long and complex concepts
 *     more efficiently.
 *     A horizontal expansion factor has its benefits, but limits
 *     the length of concepts learnable in reasonable time to
 *     about 15 with its default value of 0.6 and a small sized
 *     background knowledge base. We hope to get more fine-grained
 *     control of whether it makes sense to extend a node with
 *     more sophisticated heuristics.
 *     Dropping the horizontal expansion factor means that the
 *     completeness of the algorithm depends on the heuristic.
 * 
 * @author Jens Lehmann
 *
 */
@ComponentAnn(name = "OWL Class Expression Learner", shortName = "ocel", version = 1.2)
public class OCEL extends AbstractCELA implements OCELMBean {
	
//	private OCELConfigurator configurator;
//
//	public OCELConfigurator getConfigurator(){
//		return configurator;
//	}
	
	// actual algorithm
	private ROLearner2 algorithm;
	private static Logger logger = Logger.getLogger(OCEL.class);
	private String logLevel = CommonConfigOptions.logLevelDefault;	
	
	// dependencies
	private RhoDRDown operator;	
	private ExampleBasedHeuristic heuristic;
	
	// configuration options
	private boolean writeSearchTree;
	private File searchTreeFile;
	private boolean replaceSearchTree = false;
	private static String defaultSearchTreeFile = "log/searchTree.txt";
//	private String heuristicStr = "multi";
	Set<NamedClass> allowedConcepts;
	Set<ObjectProperty> allowedRoles;
	Set<NamedClass> ignoredConcepts;
	Set<ObjectProperty> ignoredRoles;
	// these are computed as the result of the previous four settings
	Set<NamedClass> usedConcepts;
	Set<ObjectProperty> usedRoles;	
//	private boolean applyAllFilter = true;
//	private boolean applyExistsFilter = true;	
	private boolean useTooWeakList = true;
	private boolean useOverlyGeneralList = true;
	private boolean useShortConceptConstruction = true;
	private boolean improveSubsumptionHierarchy = true;
//	private boolean useAllConstructor = CommonConfigOptions.useAllConstructorDefault;
//	private boolean useExistsConstructor = CommonConfigOptions.useExistsConstructorDefault;
//	private boolean useHasValueConstructor = CommonConfigOptions.useHasValueConstructorDefault;
//	private int valueFrequencyThreshold = CommonConfigOptions.valueFrequencyThresholdDefault;
//	private boolean useCardinalityRestrictions = CommonConfigOptions.useCardinalityRestrictionsDefault;
//	private boolean useNegation = CommonConfigOptions.useNegationDefault;
//	private boolean useBooleanDatatypes = CommonConfigOptions.useBooleanDatatypesDefault;
//	private boolean useDoubleDatatypes = CommonConfigOptions.useDoubleDatatypesDefault;
	private static double noisePercentageDefault = 0.0;
	private double noisePercentage = noisePercentageDefault;
	private NamedClass startClass = null;
	private boolean useDataHasValueConstructor = false;
	//refactor this
	private static boolean usePropernessChecksDefault = false;
	private boolean usePropernessChecks = usePropernessChecksDefault;
	//	refactor this
	private static int maxPosOnlyExpansionDefault = 4;
	private int maxPosOnlyExpansion = maxPosOnlyExpansionDefault;
	private boolean forceRefinementLengthIncrease = true;
	//extended Options
	//in seconds
	private int maxExecutionTimeInSeconds = CommonConfigOptions.maxExecutionTimeInSecondsDefault;
	private int minExecutionTimeInSeconds = CommonConfigOptions.minExecutionTimeInSecondsDefault;
	private int guaranteeXgoodDescriptions = CommonConfigOptions.guaranteeXgoodDescriptionsDefault;
	private int maxClassDescriptionTests = CommonConfigOptions.maxClassDescriptionTestsDefault;
	
	private double negativeWeight = 1.0;
	private double startNodeBonus = 0.1; // 1.0;
	private double expansionPenaltyFactor = 0.02;
	private int negationPenalty = 0;
	private boolean terminateOnNoiseReached = true;
	
	private LongWrapper totalRefinement = new LongWrapper(0);
	
	// Variablen zur Einstellung der Protokollierung
	// boolean quiet = false;
	boolean showBenchmarkInformation = false;
	// boolean createTreeString = false;
	// String searchTree = new String();
//	private int cardinalityLimit = 5;
//	private boolean useStringDatatypes = false;
//	private boolean instanceBasedDisjoints = true;

	// Konfiguration des Algorithmus
	// Faktor für horizontale Erweiterung (notwendig für completeness)
	// double horizontalExpansionFactor = 0.6;	


    public OCEL(){

    }
	// soll später einen Operator und eine Heuristik entgegennehmen
	// public ROLearner(LearningProblem learningProblem, LearningProblem learningProblem2) {
	public OCEL(PosNegLP learningProblem, AbstractReasonerComponent reasoningService) {
		super(learningProblem, reasoningService);
//		this.configurator = new OCELConfigurator(this);
	}
	
	public OCEL(PosOnlyLP learningProblem, AbstractReasonerComponent reasoningService) {
		super(learningProblem, reasoningService);
//		this.configurator = new OCELConfigurator(this);
	}
	
	public static Collection<Class<? extends AbstractLearningProblem>> supportedLearningProblems() {
		Collection<Class<? extends AbstractLearningProblem>> problems = new LinkedList<Class<? extends AbstractLearningProblem>>();
		problems.add(PosNegLP.class);
		problems.add(PosOnlyLP.class);
		return problems;
	}
	
	public static Collection<ConfigOption<?>> createConfigOptions() {
		Collection<ConfigOption<?>> options = new LinkedList<ConfigOption<?>>();
		options.add(new BooleanConfigOption("writeSearchTree", "specifies whether to write a search tree", false));
		options.add(new StringConfigOption("searchTreeFile","file to use for the search tree", defaultSearchTreeFile));
		options.add(new BooleanConfigOption("replaceSearchTree","specifies whether to replace the search tree in the log file after each run or append the new search tree", false));
		StringConfigOption heuristicOption = new StringConfigOption("heuristic", "specifiy the heuristic to use", "lexicographic");
		heuristicOption.setAllowedValues(new String[] {"lexicographic", "flexible"});
		options.add(heuristicOption);
		options.add(new BooleanConfigOption("applyAllFilter", "usage of equivalence ALL R.C AND ALL R.D = ALL R.(C AND D)", true));
		options.add(new BooleanConfigOption("applyExistsFilter", "usage of equivalence EXISTS R.C OR EXISTS R.D = EXISTS R.(C OR D)", true));
		options.add(new BooleanConfigOption("useTooWeakList", "try to filter out too weak concepts without sending them to the reasoner", true));
		options.add(new BooleanConfigOption("useOverlyGeneralList", "try to find overly general concept without sending them to the reasoner", true));
		options.add(new BooleanConfigOption("useShortConceptConstruction", "shorten concept to see whether they already exist", true));
		DoubleConfigOption horizExp = new DoubleConfigOption("horizontalExpansionFactor", "horizontal expansion factor (see publication for description)", 0.6);
		horizExp.setLowerLimit(0.0);
		horizExp.setUpperLimit(1.0);
		options.add(horizExp);
		options.add(new BooleanConfigOption("improveSubsumptionHierarchy", "simplify subsumption hierarchy to reduce search space (see publication for description)", true));
		options.add(CommonConfigOptions.allowedConcepts());
		options.add(CommonConfigOptions.ignoredConcepts());
		// allowed/ignored roles are an unstable/untested feature
		options.add(CommonConfigOptions.allowedRoles());
		options.add(CommonConfigOptions.ignoredRoles());
		options.add(CommonConfigOptions.useAllConstructor());
		options.add(CommonConfigOptions.useExistsConstructor());
		options.add(CommonConfigOptions.useHasValueConstructor());
		options.add(CommonConfigOptions.useDataHasValueConstructor());
		options.add(CommonConfigOptions.valueFreqencyThreshold());
		options.add(CommonConfigOptions.useCardinalityRestrictions());
		options.add(CommonConfigOptions.cardinalityLimit());
		options.add(CommonConfigOptions.useNegation());
		options.add(CommonConfigOptions.useBooleanDatatypes());
		options.add(CommonConfigOptions.useDoubleDatatypes());
		options.add(CommonConfigOptions.useStringDatatypes());
		options.add(CommonConfigOptions.maxExecutionTimeInSeconds());
		options.add(CommonConfigOptions.minExecutionTimeInSeconds());
		options.add(CommonConfigOptions.guaranteeXgoodDescriptions());
		options.add(CommonConfigOptions.maxClassDescriptionTests());
		options.add(CommonConfigOptions.getLogLevel());
		options.add(new BooleanConfigOption("usePropernessChecks", "specifies whether to check for equivalence (i.e. discard equivalent refinements)",usePropernessChecksDefault));
		options.add(CommonConfigOptions.getNoisePercentage());
		options.add(CommonConfigOptions.getTerminateOnNoiseReached());
		options.add(new StringConfigOption("startClass", "the named class which should be used to start the algorithm (GUI: needs a widget for selecting a class)"));
		options.add(new BooleanConfigOption("forceRefinementLengthIncrease", "specifies whether nodes should be expanded until only longer refinements are reached"));
		options.add(new DoubleConfigOption("negativeWeight", "Used to penalise errors on negative examples different from those of positive examples (lower = less importance for negatives).",1.0));
		options.add(new DoubleConfigOption("startNodeBonus", "You can use this to give a heuristic bonus on the start node (= initially broader exploration of search space).",0.0));
		options.add(new IntegerConfigOption("negationPenalty", "Penalty on negations (TODO: better explanation).", 0));
		options.add(CommonConfigOptions.getExpansionPenaltyFactor(0.02));
		options.add(CommonConfigOptions.getInstanceBasedDisjoints());
		return options;
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.Component#init()
	 */
	@Override
	public void init() throws ComponentInitException {
		
		// exit with a ComponentInitException if the reasoner is unsupported for this learning algorithm
		if(getReasoner().getReasonerType() == ReasonerType.DIG) {
			throw new ComponentInitException("DIG does not support the inferences needed in the selected learning algorithm component: " + getName());
		}
		
		// set log level if the option has been set
		if(!logLevel.equals(CommonConfigOptions.logLevelDefault))
			logger.setLevel(Level.toLevel(logLevel,Level.toLevel(CommonConfigOptions.logLevelDefault)));
		
		if(searchTreeFile == null)
			searchTreeFile = new File(defaultSearchTreeFile);

		if(writeSearchTree)
			Files.clearFile(searchTreeFile);

		// adjust heuristic

		if(heuristic == null) {
			if(getLearningProblem() instanceof PosOnlyLP) {
				throw new RuntimeException("does not work with positive examples only yet");
//				heuristic = new MultiHeuristic(((PosOnlyLP) getLearningProblem()).getPositiveExamples().size(),0, negativeWeight, startNodeBonus, expansionPenaltyFactor, negationPenalty);
			} else {
				heuristic = new MultiHeuristic(((PosNegLP) getLearningProblem()).getPositiveExamples().size(),((PosNegLP) getLearningProblem()).getNegativeExamples().size(), negativeWeight, startNodeBonus, expansionPenaltyFactor, negationPenalty);
			}			
			
			// OLD CODE below: in the new framework we assume that the 
			// heuristic is always injected as object (not as string)
//			if(heuristicStr == "lexicographic")
//				heuristic = new LexicographicHeuristic();
//			else if(heuristicStr == "flexible") {
//				if(learningProblem instanceof PosOnlyLP) {
//					throw new RuntimeException("does not work with positive examples only yet");
//				}
//				heuristic = new FlexibleHeuristic(((PosNegLP) getLearningProblem()).getNegativeExamples().size(), ((PosNegLP) getLearningProblem()).getPercentPerLengthUnit());
//			} else {
//				if(getLearningProblem() instanceof PosOnlyLP) {
//					throw new RuntimeException("does not work with positive examples only yet");
//	//				heuristic = new MultiHeuristic(((PosOnlyLP) getLearningProblem()).getPositiveExamples().size(),0, negativeWeight, startNodeBonus, expansionPenaltyFactor, negationPenalty);
//				} else {
//					heuristic = new MultiHeuristic(((PosNegLP) getLearningProblem()).getPositiveExamples().size(),((PosNegLP) getLearningProblem()).getNegativeExamples().size(), negativeWeight, startNodeBonus, expansionPenaltyFactor, negationPenalty);
//				}
//			}
		} else {
			// we need to set some variables to make the heuristic work
			if(heuristic instanceof MultiHeuristic) {
				MultiHeuristic mh = ((MultiHeuristic)heuristic);
				if(mh.getNrOfNegativeExamples() == 0) {
					mh.setNrOfNegativeExamples(((PosNegLP) getLearningProblem()).getNegativeExamples().size());
				}
				int nrPosEx = ((PosNegLP) getLearningProblem()).getPositiveExamples().size();
				int nrNegEx = ((PosNegLP) getLearningProblem()).getNegativeExamples().size();
				if(mh.getNrOfExamples() == 0) {
					mh.setNrOfExamples(nrPosEx + nrNegEx);
				}				
				if(mh.getNrOfNegativeExamples() == 0) {
					mh.setNrOfNegativeExamples(nrNegEx);
				}					
			}
		}
		
		// warn the user if he/she sets any non-standard heuristic, because it will just be ignored
		if(learningProblem instanceof PosNegLPStandard) {
			if(((PosNegLPStandard)learningProblem).isUseApproximations()) {
				System.err.println("You actived approximations for the considered learning problem, but OCEL does not support it. Option will be ignored. (Recommendation: Use CELOE instead.)");
			}
			if(!((PosNegLPStandard)learningProblem).getAccuracyMethod().equals("predacc")) {
				System.err.println("You have chosen a non-standard (predictive accuracy) heuristic in your learning problem, but OCEL does not support it. Option will be ignored. (Recommendation: Use CELOE instead.)");
			}
		}
		
		// compute used concepts/roles from allowed/ignored
		// concepts/roles
		if(allowedConcepts != null) {
			// sanity check to control if no non-existing concepts are in the list
			Helper.checkConcepts(reasoner, allowedConcepts);
			usedConcepts = allowedConcepts;
		} else if(ignoredConcepts != null) {
			usedConcepts = Helper.computeConceptsUsingIgnoreList(reasoner, ignoredConcepts);
		} else {
			usedConcepts = Helper.computeConcepts(reasoner);
		}
		
		if(allowedRoles != null) {
			Helper.checkRoles(reasoner, allowedRoles);
			usedRoles = allowedRoles;
		} else if(ignoredRoles != null) {
			Helper.checkRoles(reasoner, ignoredRoles);
			usedRoles = Helper.difference(reasoner.getObjectProperties(), ignoredRoles);
		} else {
			usedRoles = reasoner.getObjectProperties();
		}
		
		// prepare subsumption and role hierarchies, because they are needed
		// during the run of the algorithm;
		// in contrast to before, the learning algorithms have to maintain their
		// own view on the class hierarchy
		ClassHierarchy classHierarchy = reasoner.getClassHierarchy().cloneAndRestrict(usedConcepts);
		if(improveSubsumptionHierarchy)
			classHierarchy.thinOutSubsumptionHierarchy();
		
//		System.out.println(classHierarchy);
//		System.exit(0);
		
//		reasoner.prepareRoleHierarchy(usedRoles);
		// prepare datatype hierarchy only if necessary
//		if(reasoner.hasDatatypeSupport())
//			reasoner.prepareDatatypePropertyHierarchy();
		
		// create a refinement operator and pass all configuration
		// variables to it
		if(operator == null) {
			// we use a default operator and inject the class hierarchy for now
			operator = new RhoDRDown();
			((RhoDRDown)operator).setReasoner(reasoner);
			((RhoDRDown)operator).init();
		}
		// TODO: find a better solution as this is quite difficult to debug
		((RhoDRDown)operator).setSubHierarchy(classHierarchy);
		((RhoDRDown)operator).setObjectPropertyHierarchy(reasoner.getObjectPropertyHierarchy());
		((RhoDRDown)operator).setDataPropertyHierarchy(reasoner.getDatatypePropertyHierarchy());
				
		
		// create an algorithm object and pass all configuration
		// options to it
		algorithm = new ROLearner2(
//				configurator,
				learningProblem,
				reasoner,
				operator,
				heuristic,
				startClass,
				// usedConcepts,
				// usedRoles,
				noisePercentage/(double)100,
				writeSearchTree,
				replaceSearchTree,
				searchTreeFile,
				useTooWeakList,
				useOverlyGeneralList,
				useShortConceptConstruction,
				usePropernessChecks,
				maxPosOnlyExpansion,
				maxExecutionTimeInSeconds,
				minExecutionTimeInSeconds,
				guaranteeXgoodDescriptions,
				maxClassDescriptionTests,
				forceRefinementLengthIncrease,
				terminateOnNoiseReached,
				negativeWeight, 
				startNodeBonus, 
				expansionPenaltyFactor, 
				negationPenalty,
				totalRefinement
		);
		// note: used concepts and roles do not need to be passed
		// as argument, because it is sufficient to prepare the
		// concept and role hierarchy accordingly
	}
	
	public static String getName() {
//		return "refinement operator based learning algorithm II";
		return "OCEL";
	}
	
	public static String getUsage() {
		return "algorithm = refexamples;";
	}
	
	@Override
	public void start() {
		
		try {
			ObjectName workerPoolName = new ObjectName(
					"org.dllearner.algorithms.ocel.OCELMBean:type=OCELMBean");
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			if (!mbs.isRegistered(workerPoolName))
				mbs.registerMBean(this, workerPoolName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		algorithm.start();
	}
	
//	@Override
	public ScorePosNeg getSolutionScore() {
		return algorithm.getSolutionScore();
	}
	
	@Override
	public Description getCurrentlyBestDescription() {
		return algorithm.getBestSolution();
	}
	
	@Override
	public synchronized List<Description> getCurrentlyBestDescriptions() {
		return algorithm.getCurrentlyBestDescriptions();
	}	
	
	@Override
	public EvaluatedDescriptionPosNeg getCurrentlyBestEvaluatedDescription() {
		return new EvaluatedDescriptionPosNeg(algorithm.getBestSolution(),algorithm.getSolutionScore());
	}
	
	@Override
	public synchronized TreeSet<EvaluatedDescriptionPosNeg> getCurrentlyBestEvaluatedDescriptions() {
		return algorithm.getCurrentlyBestEvaluatedDescriptions();
	}

	/** {@inheritDoc} */
	@Override
	public void stop() {
		algorithm.stop();
	}

	public ExampleBasedNode getStartNode() {
		return algorithm.getStartNode();
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.LearningAlgorithm#isRunning()
	 */
	/** {@inheritDoc} */
	@Override
	public boolean isRunning() {
		return algorithm.isRunning();
	}
	
	public RhoDRDown getRefinementOperator() {
		return operator;
	}

	public RhoDRDown getOperator() {
		return operator;
	}

    @Autowired(required=false)
	public void setOperator(RhoDRDown operator) {
		this.operator = operator;
	}

	public boolean isWriteSearchTree() {
		return writeSearchTree;
	}

	public void setWriteSearchTree(boolean writeSearchTree) {
		this.writeSearchTree = writeSearchTree;
	}

	public File getSearchTreeFile() {
		return searchTreeFile;
	}

	public void setSearchTreeFile(File searchTreeFile) {
		this.searchTreeFile = searchTreeFile;
	}

	public boolean isReplaceSearchTree() {
		return replaceSearchTree;
	}

	public void setReplaceSearchTree(boolean replaceSearchTree) {
		this.replaceSearchTree = replaceSearchTree;
	}

	public Set<NamedClass> getAllowedConcepts() {
		return allowedConcepts;
	}

	public void setAllowedConcepts(Set<NamedClass> allowedConcepts) {
		this.allowedConcepts = allowedConcepts;
	}

	public Set<ObjectProperty> getAllowedRoles() {
		return allowedRoles;
	}

	public void setAllowedRoles(Set<ObjectProperty> allowedRoles) {
		this.allowedRoles = allowedRoles;
	}

	public Set<NamedClass> getIgnoredConcepts() {
		return ignoredConcepts;
	}

	public void setIgnoredConcepts(Set<NamedClass> ignoredConcepts) {
		this.ignoredConcepts = ignoredConcepts;
	}

	public Set<ObjectProperty> getIgnoredRoles() {
		return ignoredRoles;
	}

	public void setIgnoredRoles(Set<ObjectProperty> ignoredRoles) {
		this.ignoredRoles = ignoredRoles;
	}

	public Set<NamedClass> getUsedConcepts() {
		return usedConcepts;
	}

	public void setUsedConcepts(Set<NamedClass> usedConcepts) {
		this.usedConcepts = usedConcepts;
	}

	public Set<ObjectProperty> getUsedRoles() {
		return usedRoles;
	}

	public void setUsedRoles(Set<ObjectProperty> usedRoles) {
		this.usedRoles = usedRoles;
	}

	public boolean isUseTooWeakList() {
		return useTooWeakList;
	}

	public void setUseTooWeakList(boolean useTooWeakList) {
		this.useTooWeakList = useTooWeakList;
	}

	public boolean isUseOverlyGeneralList() {
		return useOverlyGeneralList;
	}

	public void setUseOverlyGeneralList(boolean useOverlyGeneralList) {
		this.useOverlyGeneralList = useOverlyGeneralList;
	}

	public boolean isUseShortConceptConstruction() {
		return useShortConceptConstruction;
	}

	public void setUseShortConceptConstruction(boolean useShortConceptConstruction) {
		this.useShortConceptConstruction = useShortConceptConstruction;
	}

	public boolean isImproveSubsumptionHierarchy() {
		return improveSubsumptionHierarchy;
	}

	public void setImproveSubsumptionHierarchy(boolean improveSubsumptionHierarchy) {
		this.improveSubsumptionHierarchy = improveSubsumptionHierarchy;
	}

	public double getNoisePercentage() {
		return noisePercentage;
	}

	public void setNoisePercentage(double noisePercentage) {
		this.noisePercentage = noisePercentage;
	}

	public NamedClass getStartClass() {
		return startClass;
	}

	public void setStartClass(NamedClass startClass) {
		this.startClass = startClass;
	}

	public boolean isUsePropernessChecks() {
		return usePropernessChecks;
	}

	public void setUsePropernessChecks(boolean usePropernessChecks) {
		this.usePropernessChecks = usePropernessChecks;
	}

	public int getMaxPosOnlyExpansion() {
		return maxPosOnlyExpansion;
	}

	public void setMaxPosOnlyExpansion(int maxPosOnlyExpansion) {
		this.maxPosOnlyExpansion = maxPosOnlyExpansion;
	}

	public boolean isForceRefinementLengthIncrease() {
		return forceRefinementLengthIncrease;
	}

	public void setForceRefinementLengthIncrease(boolean forceRefinementLengthIncrease) {
		this.forceRefinementLengthIncrease = forceRefinementLengthIncrease;
	}

	public int getMaxExecutionTimeInSeconds() {
		return maxExecutionTimeInSeconds;
	}

	public void setMaxExecutionTimeInSeconds(int maxExecutionTimeInSeconds) {
		this.maxExecutionTimeInSeconds = maxExecutionTimeInSeconds;
	}

	public int getMinExecutionTimeInSeconds() {
		return minExecutionTimeInSeconds;
	}

	public void setMinExecutionTimeInSeconds(int minExecutionTimeInSeconds) {
		this.minExecutionTimeInSeconds = minExecutionTimeInSeconds;
	}

	public int getGuaranteeXgoodDescriptions() {
		return guaranteeXgoodDescriptions;
	}

	public void setGuaranteeXgoodDescriptions(int guaranteeXgoodDescriptions) {
		this.guaranteeXgoodDescriptions = guaranteeXgoodDescriptions;
	}

	public int getMaxClassDescriptionTests() {
		return maxClassDescriptionTests;
	}

	public void setMaxClassDescriptionTests(int maxClassDescriptionTests) {
		this.maxClassDescriptionTests = maxClassDescriptionTests;
	}

	public boolean isShowBenchmarkInformation() {
		return showBenchmarkInformation;
	}

	public void setShowBenchmarkInformation(boolean showBenchmarkInformation) {
		this.showBenchmarkInformation = showBenchmarkInformation;
	}

	public double getNegativeWeight() {
		return negativeWeight;
	}

	public void setNegativeWeight(double negativeWeight) {
		this.negativeWeight = negativeWeight;
	}

	public double getStartNodeBonus() {
		return startNodeBonus;
	}

	public void setStartNodeBonus(double startNodeBonus) {
		this.startNodeBonus = startNodeBonus;
	}

	public double getExpansionPenaltyFactor() {
		return expansionPenaltyFactor;
	}

	public void setExpansionPenaltyFactor(double expansionPenaltyFactor) {
		this.expansionPenaltyFactor = expansionPenaltyFactor;
	}

	public double getNegationPenalty() {
		return negationPenalty;
	}

	public void setNegationPenalty(int negationPenalty) {
		this.negationPenalty = negationPenalty;
	}

	public boolean isUseDataHasValueConstructor() {
		return useDataHasValueConstructor;
	}

	public void setUseDataHasValueConstructor(boolean useDataHasValueConstructor) {
		this.useDataHasValueConstructor = useDataHasValueConstructor;
	}

	public boolean isTerminateOnNoiseReached() {
		return terminateOnNoiseReached;
	}

	public void setTerminateOnNoiseReached(boolean terminateOnNoiseReached) {
		this.terminateOnNoiseReached = terminateOnNoiseReached;
	}

    @Autowired(required=false)
	public void setHeuristic(ExampleBasedHeuristic heuristic) {
		this.heuristic = heuristic;
	}

    public ExampleBasedHeuristic getHeuristic() {
        return heuristic;
    }
    
	@Override
	public long getTotalNumberOfDescriptionsGenerated() {
		return totalRefinement.getValue();
	}
	
	/*
	 * bean: 
	 * 	public long getTotalDescriptions();
		public int getCurrentlyBestDescriptionLength();
		public double getCurrentlyBestAccuracy();
		public int getCurrentlyMaxExpansion();
	 */
	
	@Override
	public long getTotalDescriptions() {
		return algorithm.conceptTestsReasoner + 
			algorithm.conceptTestsOverlyGeneralList + 
			algorithm.conceptTestsTooWeakList;
	}
	
	
	@Override
	public int getCurrentlyBestDescriptionLength() {
		return algorithm.getBestSolution().getLength();
	}
	
	
	@Override
	public double getCurrentlyBestAccuracy() {
		return algorithm.getCurrentlyBestAccuracy();
	}
	
	
	@Override
	public int getCurrentlyMaxExpansion() {
		return algorithm.getcurrentlyMaxExpansion();
	}
}
