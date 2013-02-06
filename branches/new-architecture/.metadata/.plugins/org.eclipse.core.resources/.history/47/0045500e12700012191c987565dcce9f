package org.dllearner.algorithms.ParCEL;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadPoolExecutor;

import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Union;

/**
 * Abstract class for all ParCEL algorithms family
 * 
 * @author An C. Tran
 * 
 */
public abstract class ParCELAbstract extends AbstractCELA {

	// ----------------------------------
	// configuration options 
	// ----------------------------------
	@ConfigOption(name = "numberOfWorkers", defaultValue = "4", description = "Number of workers will be created to serve the learner")
	protected int numberOfWorkers = 4; // 

	@ConfigOption(name = "maxExecutionTimeInSeconds", defaultValue = "0", description = "Maximum execution of the algorithm in seconds")
	protected int maxExecutionTimeInSeconds = 0;

	@ConfigOption(name = "noisePercentage", defaultValue = "0.0", description = "The percentage of noise within the examples")
	protected double noisePercentage = 0.0;

	@ConfigOption(name = "maxNoOfSplits", defaultValue = "10", description = "Max number of splits will be applied for data properties with double range. This parameter is not used if a Splitter is provided")
	protected int maxNoOfSplits = 10;

	@ConfigOption(name = "ignoredConcepts", defaultValue = "", description = "Set of concepts that will be ignored in learning the target concept")
	protected Set<NamedClass> ignoredConcepts = null;

	@ConfigOption(name = "minimalCoverage", defaultValue = "0", description = "Minimal coverage that a partial definition must approach so that it can be used")
	private double minimalCoverage = 0;		//0 means no constrain on this condition
	
	@ConfigOption(name = "useHasValue", defaultValue = "false", description = "Use value restriction or not")
	private boolean useHasValue = false;
	
	
	protected int maxHorizExp = 0;
	
	//-------------------------------------------
	//common variables for the learner
	//-------------------------------------------
	
	/**
	 * Hold all generated description to avoid the duplicated descriptions (this may contains only
	 * the weak description but it may take time to check both in this data structure and the search
	 * tree to check for the duplication). Redundancy in this case may help increasing performance.
	 */
	protected ConcurrentSkipListSet<Description> allDescriptions = null;

	
	/**
	 * The search tree holds all evaluated descriptions that are not correct and not weak ==>
	 * candidate for partial definitions. Nodes in the search tree must be sorted so that it can
	 * help the searching more efficiently (best search rather than 'blind' breath first or depth
	 * first)<br>
	 * NOTE: node = (description + accuracy/correctness/completeness/... values)
	 */
	protected ConcurrentSkipListSet<ParCELNode> searchTree = null; 
	

	/**
	 * partial definitions (they should be sorted so that we can get the best
	 * partial definition at any time)
	 */
	protected SortedSet<ParCELExtraNode> partialDefinitions = null;


	
	/**
	 * Heuristic used in the searching expansion (choosing node for expansion)
	 */
	protected ParCELHeuristic heuristic = null;

	
	/**
	 * Reducer which will be used to reduce the partial definitions
	 */
	protected ParCELReducer reducer = null;
	
	
	/**
	 * Pool of workers
	 */
	protected ThreadPoolExecutor workerPool;
	
	
	
	//------------------------------------------
	// Common constructors and methods
	//------------------------------------------
	
	/**
	 * Default constructor
	 */
	public ParCELAbstract() {
		super();
	}

	/**
	 * 
	 * Constructor for the learning algorithm
	 * 
	 * @param learningProblem
	 *            Learning problem, must be a PDLLPosNegLP
	 * @param reasoningService
	 *            Reasoner
	 */
	public ParCELAbstract(ParCELPosNegLP learningProblem, AbstractReasonerComponent reasoningService) {
		super(learningProblem, reasoningService);
	}

	/**
	 * Get the union of all the best (reduced) partial definitions
	 * 
	 * @return An union of all reduced partial definitions
	 */
	public Description getUnionCurrenlyBestDescription() {
		List<Description> compactedDescriptions = new LinkedList<Description>();

		SortedSet<ParCELExtraNode> compactedPartialdefinition = this.getReducedPartialDefinition();

		for (ParCELExtraNode def : compactedPartialdefinition)
			compactedDescriptions.add(def.getDescription());

		return new Union(compactedDescriptions);
	}


	/**
	 * Get the union of all the best (reduced) partial definitions using a given reducer
	 * 
	 * @return An union of all reduced partial definitions
	 */
	public Description getUnionCurrenlyBestDescription(ParCELReducer reducer) {
		List<Description> compactedDescriptions = new LinkedList<Description>();

		SortedSet<ParCELExtraNode> compactedPartialdefinition = this.getReducedPartialDefinition(reducer);

		for (ParCELExtraNode def : compactedPartialdefinition)
			compactedDescriptions.add(def.getDescription());

		return new Union(compactedDescriptions);
	}


	/**
	 * Get all unreduced partial definitions
	 * 
	 * @return unreduced partial definitions
	 */
	public abstract Set<ParCELExtraNode> getPartialDefinitions();

	
	/**
	 * Get the max overall completeness so far 
	 * 
	 * @return max overall completeness
	 */
	public abstract double getCurrentlyOveralMaxCompleteness();

	
	/**
	 * Get the set of reduced partial definitions using default reducer
	 * 
	 * @return set of reduced partial definitions
	 */
	public abstract SortedSet<ParCELExtraNode> getReducedPartialDefinition();
	
	
	/**
	 * Get the number of reduced partial definitions
	 * 
	 * @return number of reduced partial definitions
	 */
	public abstract int getNoOfReducedPartialDefinition();

		
	/**
	 * Get the reduced partial definitions using the given reducer
	 * 
	 * @param reducer Reducer which will be used to reduce the partial definitions
	 * 
	 * @return reduced partial definitions
	 */
	public abstract SortedSet<ParCELExtraNode> getReducedPartialDefinition(ParCELReducer reducer);

	
	//===========================================
	// call-back methods for workers
	//===========================================
	/**
	 * Update the max horizontal expansion 
	 * 
	 * @param newHozExp New horizontal expansion
	 */
	public synchronized void updateMaxHorizontalExpansion(int newHozExp) {			
		if (maxHorizExp < newHozExp) {
			maxHorizExp = newHozExp;
		}
	}
	
	
	
	public int getMaximumHorizontalExpansion() {
		return maxHorizExp;
	}
	
	
	/**
	 * This will be called by the workers to return the new partial definitions found  
	 * 
	 * @param definitions New partial definitions
	 */
	public abstract void newPartialDefinitionsFound(Set<ParCELExtraNode> definitions);
	
	
	/**
	 * This will be called by the workers to pass the new refinements descriptions 
	 * 
	 * @param newNodes New refinement descriptions
	 */
	public abstract void newRefinementDescriptions(Set<ParCELNode> newNodes);
	
	
	/*
	 * 
	 * Get the learning time in milisecond. Learning time does not include the reduction time
	 */
	public abstract long getLearningTime();	
	
	/**
	 * Get total number of partial definitions found so far
	 * 
	 * @return Number of partial definitions
	 */
	public long getNumberOfPartialDefinitions() {
		return this.partialDefinitions.size();
	}
	
	/**
	 * Add a description into search tree. No synchronization is needed since safe-thread is using
	 * 
	 * @param des
	 *            Description to be added
	 * 
	 * @return True is the description can be added (has not been in the search tree/all
	 *         descriptions set
	 */
	public boolean addDescription(Description des) {
		return this.allDescriptions.add(des);
	}
	
	// -------------------------------------------------------
	// setters and getters for learner configuration options
	// -------------------------------------------------------

	//number of workers
	public void setNumberOfWorkers(int numberOfWorkers) {
		this.numberOfWorkers = numberOfWorkers;
	}

	public int getNumberOfWorkers() {
		return numberOfWorkers;
	}

	//time out (max execution time)
	public void setMaxExecutionTimeInSeconds(int maxExecutionTime) {
		this.maxExecutionTimeInSeconds = maxExecutionTime;
	}

	public int getMaxExecutionTimeInSeconds() {
		return maxExecutionTimeInSeconds;
	}

	//noise allowed
	public void setNoisePercentage(double noise) {
		this.noisePercentage = noise;
	}

	public double getNoisePercentage() {
		return this.noisePercentage;
	}
	
	//max no of splits
	public int getMaxNoOfSplits() {
		return maxNoOfSplits;
	}

	public void setMaxNoOfSplits(int maxNoOfSplits) {
		this.maxNoOfSplits = maxNoOfSplits;
	}

	//ignored concepts
	public Set<NamedClass> getIgnoredConcepts() {
		return ignoredConcepts;
	}

	public void setIgnoredConcepts(Set<NamedClass> ignoredConcepts) {
		this.ignoredConcepts = ignoredConcepts;
	}
	
	//minimal covered of the partial definitions
	public double getMinimalCoverage() {
		return minimalCoverage;
	}

	public void setMinimalCoverage(double minimalCoverage) {
		this.minimalCoverage = minimalCoverage;
	}
	
	public ParCELReducer getReducer() {
		return this.reducer;
	}

	public String getBaseURI() {
		return reasoner.getBaseURI();
	}
	
	public Map<String, String> getPrefix() {
		return reasoner.getPrefixes();
	}
	
	public long getTotalNumberOfDescriptionsGenerated() {
		return this.allDescriptions.size();
	}
	
	public boolean getUseHasValue() {
		return this.useHasValue;
	}
	
	public void setUseHasValue(boolean useHasValue) {
		this.useHasValue = useHasValue;
	}
}
