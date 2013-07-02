package org.dllearner.algorithms.ParCELEx;

/**
 * This class implements a extended version of PDLL which make use of the partial definitions, i.e. 
 * 	descriptions that cover none of the positive examples and some (>0) negative examples.
 * 
 * In this implementation, the counter partial definitions will be used directly by the workers to check if they can be used 
 * to combine with the new description to become a partial definition. 
 * 
 * Therefore, there is no usage of the counter partial definition in the learner side   		
 * 
 *	@author An C. Tran
 */

import java.text.DecimalFormat;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList; 
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELCompletenessComparator;
import org.dllearner.algorithms.ParCEL.ParCELDefaultHeuristic;
import org.dllearner.algorithms.ParCEL.ParCELExtraNode;
import org.dllearner.algorithms.ParCEL.ParCELCorrectnessComparator;
import org.dllearner.algorithms.ParCEL.ParCELHeuristic;
import org.dllearner.algorithms.ParCEL.ParCELImprovedCoverageGreedyReducer;
import org.dllearner.algorithms.ParCEL.ParCELNode;
import org.dllearner.algorithms.ParCEL.ParCELPosNegLP;
import org.dllearner.algorithms.ParCEL.ParCELReducer;
import org.dllearner.algorithms.ParCEL.ParCELRefinementOperatorPool;
import org.dllearner.algorithms.ParCEL.ParCELScore;
import org.dllearner.algorithms.ParCEL.ParCELStringUtilities;
import org.dllearner.algorithms.ParCEL.ParCELWorkerThreadFactory;
import org.dllearner.algorithms.ParCEL.ParCELearnerMBean;
import org.dllearner.algorithms.ParCEL.split.ParCELDoubleSplitterAbstract;
import org.dllearner.algorithms.celoe.OENode;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.owl.ClassHierarchy;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Thing;
import org.dllearner.refinementoperators.RefinementOperator;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.owl.EvaluatedDescriptionComparator;
import org.springframework.beans.factory.annotation.Autowired;


@ComponentAnn(name="ParCELearnerExV2", shortName="parcelLearnerExV2", version=0.1, description="Parallel Class Expression Logic Learning with Exception V2")
public class ParCELearnerExV2 extends ParCELExAbstract implements ParCELearnerMBean {
		
	
	/**
	 * Refinement operator pool which provides refinement operators
	 */	
	private ParCELRefinementOperatorPool refinementOperatorPool;

	private RefinementOperator refinementOperator = null;	//auto-wired will be used for this property

	//splitter used to split the numerical data properties
	private ParCELDoubleSplitterAbstract splitter = null;

	private static Logger logger = Logger.getLogger(ParCELearnerExV2.class);	
	
	private ParCELReducer reducer = null;
	
	private int noOfReducedPartialDefinition = 0;


	/**
	 * Search tree. It hold all evaluated descriptions that are not correct and not weak
	 * ==> candidate for partial definitions
	 * Nodes in the search tree must be ordered using heuristic so that it can help the searching 
	 * 		more efficiently (best search rather than 'blind' breadth first of depth first)
	 * NOTE: node = (description + accuracy/correctness/completeness/... value) 
	 */
	//private ConcurrentSkipListSet<ParCELNode> searchTree;		//thread safe set

	/**
	 * contains tasks submitted to thread pool
	 */
	BlockingQueue<Runnable> taskQueue;
										

	
	/**
	 *	partial definition (they should be shorted by completeness
	 *	so that we can get the best partial definition at any time
	 *	if not, we can use a hashset here 
	 */
	//private SortedSet<PDLLExtraNode> partialDefinitions;		//correct but may be incomplete definitions found
	//private SortedSet<PDLLExtraNode> counterPartialDefinitions;
	
	
	//examples
	private Set<Individual> positiveExamples;
	private Set<Individual> negativeExamples;
	
	
	/**
	 * This may be considered as the noise allowed in learning, 
	 * 	i.e. the maximum number of positive examples can be discard (uncovered)
	 */
	private int uncoveredPositiveExampleAllowed;
	
	
	/**
	 * 	Holds the uncovered positive example, this will be updated when the worker found a partial definition
	 *	since the callback method "definitionFound" is synchronized",
	 * 	there is no need to create a thread-safe for this set
	 */
	private HashSet<Individual> uncoveredPositiveExamples;
	private HashSet<Individual> coveredNegativeExamples;	


	/**
	 * Start description and root node of the search tree
	 */
	private Description startClass;		//description in of the root node
	private ParCELNode startNode;			//root of the search tree	

	
	//---------------------------------------------------------
	//flags to indicate the status of the application
	//---------------------------------------------------------
	/**
	 * The learner is stopped (reasons: done, timeout, out of memory, etc.)
	 */
	private boolean stop = false;
	
	
	/**
	 * The learner found a complete definition by partial definitions or counter partial definitions?
	 */
	private boolean done = false;		
	private boolean counterDone = false;
	
	/**
	 * Reducer get the timeout
	 */
	private boolean timeout = false;
	
	
	//configuration for worker pool
    private int minNumberOfWorker = 2;
    private int maxNumberOfWorker = 2;      //max number of workers will be created
    private int maxTaskQueueLength = 1000;
    private long keepAliveTime = 100;       //100ms
    
    
	//some properties for statistical purpose
	private int descriptionTested;	//total number of descriptions tested (generated and calculated accuracy, correctess,...)
	private int maxHorizExp = 0;
	private double maxAccuracy = 0.0d;
	private int bestDescriptionLength = 0;

	
	private int noOfTask = 0;

		

	//just for pretty representation of description
	private String baseURI = null;
	private Map<String, String> prefix;
	
	private DecimalFormat dfPercent = new DecimalFormat("0.00%");
		
	
	/**=========================================================================================================<br>
	 * Constructor for the learning algorithm
	 * 
	 * @param learningProblem Must be a PDLLPosNegLP
	 * @param reasoningService A reasoner
	 */
	public ParCELearnerExV2(ParCELPosNegLP learningProblem, AbstractReasonerComponent reasoningService) {
		super(learningProblem, reasoningService);
		
		//default compactor used by this algorithm
		this.reducer = new ParCELImprovedCoverageGreedyReducer();
		//this.reducer = new ParCELPredScoreReducer();
	}
	

	/**
	 * This constructor can be used by SpringDefinition to create bean object
	 * Properties of new bean may be initialised later using setters
	 */
	public ParCELearnerExV2() {
		super();
		//this.compactor = new PDLLGenerationTimeCompactor();		
		this.reducer = new ParCELImprovedCoverageGreedyReducer();
		//this.compactor = new PDLLDefinitionLengthCompactor();
		//this.reducer = new ParCELPredScoreReducer();
	}
	/**=========================================================================================================<br>
	 * Get the name of this learning algorithm
	 * 
	 * @return Name of this learning algorithm: PLLearning
	 */
	public static String getName() {
		return "ParCELLearnerExV2";
	}
	 

	/**=========================================================================================================<br>
	 * Initial the learning algorithm
	 * 	- Create distance data
	 * 	- Prepare positive and negative examples (get from the learning problem (PLLearningPosNegLP)
	 * 	- Create a class hierarchy for refinement operator (for fast class herarchy checking)
	 * 	- Create expansion heuristic, which will be used to choose the expansion node in the search tree
	 * 	- Create refinement operator (RhoDRDown)  
	 */
	@Override
	public void init() throws ComponentInitException {
				
		//get the negative and positive examples
		if (!(learningProblem instanceof ParCELPosNegLP)) 
			throw new ComponentInitException(learningProblem.getClass() + " is not supported by '" + getName() + "' learning algorithm");
		
		
		//get the positive and negative examples from the learning problem
		positiveExamples = ((ParCELPosNegLP)learningProblem).getPositiveExamples();
		negativeExamples = ((ParCELPosNegLP)learningProblem).getNegativeExamples();
		
		
		((ParCELPosNegLP)this.learningProblem).setUncoveredPositiveExamples(this.positiveExamples);
		
		//initial heuristic which will be used by reducer to sort the search tree (expansion strategy)
		//the heuristic need to get some constant from the configurator for scoring the description
		if (heuristic == null)
			heuristic = new ParCELDefaultHeuristic();		
		
		startClass = Thing.instance;	//this will be revise later using least common super class of all observations
		

		this.uncoveredPositiveExampleAllowed = (int)Math.ceil(getNoisePercentage()*positiveExamples.size());
		
		//initialise the existing uncovered positive examples
		((ParCELPosNegLP)this.learningProblem).setUncoveredPositiveExamples(uncoveredPositiveExamples);
		
		//----------------------------------
		//create refinement operator pool
		//----------------------------------
		if (refinementOperator == null) {
			//-----------------------------------------
			//prepare for refinement operator creation
			//-----------------------------------------
			Set<NamedClass> usedConcepts = new TreeSet<NamedClass>(reasoner.getNamedClasses());
			
			
			//remove the ignored concepts out of the list of concepts will be used by refinement operator
			if (this.ignoredConcepts != null) { 
				try {
					usedConcepts.removeAll(ignoredConcepts);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			ClassHierarchy classHiearachy = reasoner.getClassHierarchy().cloneAndRestrict(usedConcepts);
			Map<DatatypeProperty, List<Double>> splits = null;
			
			//create a splitter and refinement operator pool
			//there are two options: i) using object pool, ii) using set of objects (removed from this revision) 
			if (this.splitter != null) {
				splitter.setReasoner(reasoner);
				splitter.setPositiveExamples(positiveExamples);
				splitter.setNegativeExamples(negativeExamples);
				splitter.init();

				splits = splitter.computeSplits();
				
				//i) option 1: create an object pool
				refinementOperatorPool = new ParCELRefinementOperatorPool(reasoner, classHiearachy, startClass, splits, numberOfWorkers + 1);

			}			
			else {	//no splitter provided
				//i) option 1: create an object pool
				refinementOperatorPool = new ParCELRefinementOperatorPool(reasoner, classHiearachy, startClass, numberOfWorkers + 1, maxNoOfSplits);
			}
						
			refinementOperatorPool.getFactory().setUseDisjunction(false);
			refinementOperatorPool.getFactory().setUseNegation(false);
			refinementOperatorPool.getFactory().setUseHasValue(this.getUseHasValue());

		}		

		baseURI = reasoner.getBaseURI();
		prefix = reasoner.getPrefixes();
		//new DecimalFormat();
		
		
		//logging the information (will use slf4j)
		if (logger.isInfoEnabled()) {
			logger.info("[pllearning] - Heuristic used: " + heuristic.getClass());
			logger.info("[pllearning] - Positive examples: " + positiveExamples.size() + ", negative examples: " + negativeExamples.size());
		}
		
		minNumberOfWorker = maxNumberOfWorker = numberOfWorkers;

	}	//init()
	

	
	/**=========================================================================================================<br>
	 * Start reducer
	 * 	1. Reset the status of reducer (stop, isRunning, done, timeout)<br> 
	 *	2. Reset the data structure (using reset() method)<br>
	 *	3. Create a set of workers and add them into the worker pool<br> 
	 *		NOTE: Each worker will have it own refinement operator<br>
	 *	4. Prepare some data: pos/neg examples, uncovered positive examples, etc.<br>
	 *	5. Start the learning progress: <br>
	 *		i) refine nodes in the (tree set) <br>
	 *	   ii) evaluate nodes in unevaluated nodes (hash set) <br> 
	 *
	 */
	@Override
	public void start() {
		
		// register a MBean for debugging purpose
		/*
		try {
			ObjectName parCELExV2Bean = new ObjectName(
					"org.dllearner.algorithms.ParCELEx:type=ParCELearnerExV2");
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			if (!mbs.isRegistered(parCELExV2Bean))
				mbs.registerMBean(this, parCELExV2Bean);
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
		
		stop = false;
		done = false;
		timeout = false;
		counterDone = false;
		
		this.noOfReducedPartialDefinition = -1;
			
		reset();
		

		//create a start node in the search tree
		allDescriptions.add(startClass);	//currently, start class is always Thing
		
		//add the first node into the search tree
		startNode = new ParCELNode((ParCELNode)null, startClass, 
				this.positiveExamples.size()/(double)(this.positiveExamples.size() + this.negativeExamples.size()), 0, 1);
		
		startNode.setCoveredPositiveExamples(positiveExamples);
		startNode.setCoveredNegativeExamples(negativeExamples);
			
		searchTree.add(startNode);	//add the root node into the search tree
		
		
		//---------------------------------------------
		// create worker pool
		//---------------------------------------------
		//taskQueue = new ArrayBlockingQueue<Runnable>(maxTaskQueueLength);		
		taskQueue = new LinkedBlockingQueue<Runnable>(maxTaskQueueLength);
			
		workerPool = new ThreadPoolExecutor(minNumberOfWorker, maxNumberOfWorker, keepAliveTime, 
				TimeUnit.MILLISECONDS, taskQueue, new ParCELWorkerThreadFactory());
		

		
		
		if (logger.isInfoEnabled())
			logger.info("Worker pool created, core pool size: " + workerPool.getCorePoolSize() + 
					", max pool size: " + workerPool.getMaximumPoolSize());
		
		
		//start time of reducer, statistical purpose only
		miliStarttime = System.currentTimeMillis();
		
		//----------------------------------------------------------
		// perform the learning process until the conditions for 
		//	termination meets
		//----------------------------------------------------------
		while (!isTerminateCriteriaSatisfied()) {

			//-------------------
			//check for timeout
            //-------------------
			timeout = (this.maxExecutionTimeInSeconds > 0 && (System.currentTimeMillis() - miliStarttime) > this.maxExecutionTimeInSeconds*1000);
				
			if (timeout)
				break;
			
			
			ParCELNode nodeToProcess;
			
			nodeToProcess = searchTree.pollLast();
					
			//TODO: why this? why "blocking" concept does not help in this case?
			//remove this checking will exploit the heap memory and no definition found
			//NOTE: i) instead of using sleep, can we use semaphore here?
			//		ii) if using semaphore or blocking, timeout checking may not be performed on time?
			 while ((workerPool.getQueue().size() >= maxTaskQueueLength)) {                
                 try {
                         Thread.sleep(20);
                 } catch (InterruptedException e) {
                         e.printStackTrace();
                 }
			 }
			
			if ((nodeToProcess != null) && !workerPool.isShutdown() && !workerPool.isTerminating()) {
				try {
					this.createNewTask(nodeToProcess);
				}
				catch (RejectedExecutionException re) {
					logger.error(re);
					this.searchTree.add(nodeToProcess);
				}
			}
		}	//while the algorithm is not finish
		
				
		this.miliLearningTime = System.currentTimeMillis() - miliStarttime;
		
		stop();
				

		//-----------------------------------------------------------------------
		//try to combine descriptions in the search tree with the counter partial 
		//definition to find more partial definition
		//-----------------------------------------------------------------------
		/*
		synchronized (this.searchTree) {

			if (logger.isInfoEnabled())
				logger.info("Processing counter partial definition: " + this.counterPartialDefinitions.size() + ", " + this.searchTree.size());

			List<ParCELNode> newSearchTree = new ArrayList<ParCELNode>(this.searchTree);
			Collections.sort(newSearchTree, new ParCELCompletenessComparator());

			if (logger.isInfoEnabled())
				logger.info("Finding new partial definition from COUNTER partial definition: ");

			int newPartialDefCount = 0;

			for (ParCELNode des : newSearchTree) {
				synchronized (this.counterPartialDefinitions) {

					Set<ParCELExtraNode> combinableCounterPartialDefinitions = 
						ParCELExCombineCounterPartialDefinition.getCombinable(des, this.counterPartialDefinitions);

					if (combinableCounterPartialDefinitions != null) {		
						
						//List<Description> counterList = new LinkedList<Description>();
						//counterList.add(des.getDescription());
						
						//for (ParCELExtraNode def : combinableCounterPartialDefinitions)
							//counterList.add(def.getDescription());
							//des.setDescription(new Intersection(des.getDescription(), def.getDescription()));
						//des.setDescription(new Intersection(counterList));
						des.setDescription(ParCELExUtilities.createIntersection(des.getDescription(), 
								combinableCounterPartialDefinitions, true));

						ParCELExtraNode newPD = new ParCELExtraNode(des);
						newPD.setGenerationTime(System.currentTimeMillis() - miliStarttime);
						newPD.setType(ParCELExNodeTypes.PARTIAL_DEFINITION_LAZY_COMBINATION);	//type 2
						//newPD.setCompositeList(combinableCounterPartialDefinitions);
						partialDefinitions.add(newPD);											
					}	//new partial definition found
				}	//synch counter partial definition
			}	//for each description in the search tree
			
			if (logger.isInfoEnabled()) 								
				logger.info(newPartialDefCount + " new partial definition found");
			
		}	//synch search tree
		*/

		
		//-------------------------------
		//post-learning processing
		//-------------------------------
		
		if (logger.isInfoEnabled()) {			
			synchronized (partialDefinitions) {
				if (this.getCurrentlyOveralMaxCompleteness() == 1)
					logger.info("Learning finishes in: " + this.miliLearningTime + "ms, with: " + partialDefinitions.size() + " definitions");
				else if (this.isTimeout()) {
					logger.info("Learning timeout in " + this.maxExecutionTimeInSeconds + "ms. Overall completeness (%): " + this.getCurrentlyOveralMaxCompleteness());
					logger.info("Uncovered positive examples left " + this.uncoveredPositiveExamples.size() + " - " + ParCELStringUtilities.replaceString(this.uncoveredPositiveExamples.toString(), this.baseURI, this.prefix));
				}				
				else {
					logger.info("Learning is manually terminated at " + this.miliLearningTime + "ms. Overall completeness (%): " + this.getCurrentlyOveralMaxCompleteness());
					logger.info("Uncovered positive examples left " + this.uncoveredPositiveExamples.size() + " - " + ParCELStringUtilities.replaceString(this.uncoveredPositiveExamples.toString(), this.baseURI, this.prefix));					
				}
						
				logger.info("**Reduced partial definitions:");
				TreeSet<ParCELExtraNode> compactedDefinitions = (TreeSet<ParCELExtraNode>) this.getReducedPartialDefinition();
				this.noOfReducedPartialDefinition = compactedDefinitions.size();
				int count = 1;
				for (ParCELExtraNode def : compactedDefinitions) {
					logger.info(count++ + ". " + ParCELExUtilities.groupDefinition(def.getDescription()).toManchesterSyntaxString(baseURI, prefix).replace("and (not", "\nand (not") + //def.getDescription().toManchesterSyntaxString(baseURI, prefix).replace("and (not", "\nand (not") + " " +
							"(length:" + def.getDescription().getLength() + 
							", accuracy: " + dfPercent.format(def.getAccuracy()) + 
							", type: " + def.getType() + ")");
					
					//test the new representation of the partial definition 
					//in which the counter partials will be group for readability
					//logger.info("Definition after grouped: " + ParCELEx2DefinitionGrouping.groupDefinition(def.getDescription()).toManchesterSyntaxString(baseURI, prefix).replace("and (not", "\nand (not"));
					
					//print out the learning tree
					if (logger.isTraceEnabled()) {
						ParCELNode parent = (ParCELNode)def.getParent();			
						while (parent != null) {
							logger.trace("  <-- " + parent.getDescription().toManchesterSyntaxString(this.baseURI, this.prefix)); 
									//" [acc:" +  df.format(parent.getAccuracy()) +
									//", correctness:" + df.format(parent.getCorrectness()) + ", completeness:" + df.format(parent.getCompleteness()) +
									//", score:" + df.format(this.heuristic.getScore(parent)) + "]");
							
							//print out the children nodes
							List<OENode> children = parent.getChildren();
							for (OENode child : children) {
								ParCELNode tmp = (ParCELNode)child;
								logger.trace("    --> " + tmp.getDescription().toManchesterSyntaxString(this.baseURI, this.prefix)); 
										//" [acc:" +  df.format(tmp.getAccuracy()) +
										//", correctness:" + df.format(tmp.getCorrectness()) + ", completeness:" + df.format(tmp.getCompleteness()) + 
										//", score:" + df.format(this.heuristic.getScore(tmp)) + "]");
							}
							parent = (ParCELNode)parent.getParent();				
						}
					} //end of printing the learning tree
				}
			}					
		}		
				
		
		super.aggregateCounterPartialDefinitionInf();
		
	}	//start()

	
	private void createNewTask(ParCELNode nodeToProcess) {
		workerPool.execute(new ParCELWorkerExV2(this, this.refinementOperatorPool,
				(ParCELPosNegLP)learningProblem, nodeToProcess, "ParCELEx2Task-" + (noOfTask++)));
	}
	

	
	/**=========================================================================================================<br>
	 * Callback method for worker when a partial definition found 
	 * 		(callback for an evaluation request from reducer)<br>
	 * If a definition (partial) found, do the following tasks:<br>
	 * 	1. Add the definition into the partial definition set<br>
	 * 	2. Update: uncovered positive examples, max accuracy, best description length
	 * 	2. Check for the completeness of the learning. If yes, stop the learning<br>
	 * 
	 * @param partialDefinition New partial definition
	 */
	public void newPartialDefinitionsFound(Set<ParCELExtraNode> definitions) {

		for (ParCELExtraNode def : definitions) {

			//NOTE: in the previous version, this node will be added back into the search tree
			//		it is not necessary since in DLLearn, a definition may be revised to get a better one but
			//			in this approach, we do not refine partial definition.
		
			//remove uncovered positive examples by the positive examples covered by the new partial definition
			int uncoveredPositiveExamplesRemoved;
			int uncoveredPositiveExamplesSize;		//for avoiding synchronized uncoveredPositiveExamples later on
			
			synchronized (uncoveredPositiveExamples) {
				uncoveredPositiveExamplesRemoved = this.uncoveredPositiveExamples.size();
				this.uncoveredPositiveExamples.removeAll(def.getCoveredPositiveExamples());
				uncoveredPositiveExamplesSize = this.uncoveredPositiveExamples.size();
			
			 
			uncoveredPositiveExamplesRemoved -= uncoveredPositiveExamplesSize;
			
			if (uncoveredPositiveExamplesRemoved > 0) {
							
				//set the generation time for the new partial definition
				//def.setGenerationTime(System.currentTimeMillis() - miliStarttime);		
				synchronized (partialDefinitions) {
					partialDefinitions.add(def);
				}
				
				//update the uncovered positive examples for the learning problem
				((ParCELPosNegLP)this.learningProblem).setUncoveredPositiveExamples(uncoveredPositiveExamples);
				
				if (logger.isTraceEnabled()) {
					logger.trace("(+) PARTIAL definition found: " + def.getDescription().toManchesterSyntaxString(baseURI, prefix) +
							"\n\t - covered positive examples (" + def.getCoveredPositiveExamples().size() + "): " +def.getCoveredPositiveExamples() +
							"\n\t - uncovered positive examples left: " + uncoveredPositiveExamplesSize + "/" + positiveExamples.size() +
							", des:" + def.getGenerationTime()
							);					
				}
				else if (logger.isDebugEnabled())
					logger.debug("(+) PARTIAL definition found: " + def.getDescription().toManchesterSyntaxString(baseURI, prefix) +
							"\n\t - cp (" + def.getCoveredPositiveExamples().size() + "): " + def.getCoveredPositiveExamples() +
							"\n\t - up: " + uncoveredPositiveExamplesSize + "/" + positiveExamples.size()
							);
				else if (logger.isInfoEnabled()) {
					logger.info("(+) PARTIAL definition found. Uncovered positive examples left: " + uncoveredPositiveExamplesSize + "/" + positiveExamples.size());
				}

				//update the max accuracy and max description length
				if (def.getAccuracy() > this.maxAccuracy) {
					this.maxAccuracy = def.getAccuracy();
					this.bestDescriptionLength = def.getDescription().getLength();
				}
				
				//check if the complete definition found
				if (uncoveredPositiveExamplesSize <= uncoveredPositiveExampleAllowed) {
					this.done = true;
					//stop();
				}
			}
			}
			

		}	//for each partial definition
		
	}	//definitionFound()
	
	
	/**=========================================================================================================<br>
	 * This function is used to process the counter partial definitions returned from the workers
	 */
	public void newCounterPartialDefinitionsFound(Set<ParCELExtraNode> counterPartialDefinitions) {
		
		for (ParCELExtraNode def : counterPartialDefinitions) {
		
			//calculate the "actual" number of negative examples covered by the new definition
			int numberOfNewCoveredNegativeExamples;
			int numberOfCoveredNegativeExamples;	///for avoiding synchronized coveredNegativeExamples later on
			
			synchronized (this.coveredNegativeExamples) {
				numberOfNewCoveredNegativeExamples = this.coveredNegativeExamples.size();
				this.coveredNegativeExamples.addAll(def.getCoveredNegativeExamples());
				numberOfNewCoveredNegativeExamples = this.coveredNegativeExamples.size() - numberOfNewCoveredNegativeExamples;
				numberOfCoveredNegativeExamples = this.coveredNegativeExamples.size();
			
			
			//process the counter partial definition if it covers at least 1 new negative example
			if (numberOfNewCoveredNegativeExamples > 0) {
							
				//add the new counter partial definition into the set of counter partial definitions
				synchronized (this.counterPartialDefinitions) {
					this.counterPartialDefinitions.add(def);					
				}
				
				//NOTE: when a partial definition found, we update the set of uncovered positive examples for the Learning Problem
				//			but there is no need to do it for the counter partial definition, i.e. no update for covered negative examples
				if (logger.isTraceEnabled()) {
					logger.trace("(-) COUNTER PARTIAL definition found: " + def.getDescription().getChild(0).toManchesterSyntaxString(baseURI, prefix) +
							"\n\t - covered negative examples (" + def.getCoveredNegativeExamples().size() + "): " + def.getCoveredNegativeExamples() +
							"\n\t - total covered negative examples: " + numberOfCoveredNegativeExamples + "/" + this.negativeExamples.size() 
							);					
				}
				else if (logger.isDebugEnabled())
					logger.debug("(-) COUNTER PARTIAL definition found: " + def.getDescription().getChild(0).toManchesterSyntaxString(baseURI, prefix) +
							"\n\t - cn (" + def.getCoveredNegativeExamples().size() + "): " + def.getCoveredNegativeExamples() +
							"\n\t - total cn: " + numberOfCoveredNegativeExamples + "/" + this.negativeExamples.size() 
							);
				else if (logger.isInfoEnabled()) {
					logger.info("(-) COUNTER PARTIAL definition found. Total covered negative examples: " + numberOfCoveredNegativeExamples + "/" + this.negativeExamples.size());
				}
				
				//complete counter definition found
				if (this.coveredNegativeExamples.size() >= this.negativeExamples.size()) {
					this.counterDone = true;
					//this.stop();
				}
			}
			}
			
		}
		
	}
	
	/**=========================================================================================================<br>
	 * 	Callback method for worker to call when it gets an evaluated node which is neither a partial definition 
	 * 		nor a weak description<br>
	 *	
	 *	NOTE: there is not need for using synchronisation for this method since the thread safe 
	 *		data structure is currently using  
	 * 
	 * @param newNode New node to add to the search tree
	 */
	public void newRefinementDescriptions(Set<ParCELNode> newNodes) {		
		searchTree.addAll(newNodes);
	}
	
	
	
	/**=========================================================================================================<br>
	 * Update the max horizontal expansion used
	 * @param newHozExp
	 */
	public synchronized void updateMaxHorizontalExpansion(int newHozExp) {				
		if (maxHorizExp < newHozExp) {
			maxHorizExp = newHozExp;
		}
	}

	
	/**=========================================================================================================<br>
	 * Reset all necessary properties for a new learning
	 * 	1. Create new search tree
	 * 	2. Create an empty description set, which hold all generated description (to avoid redundancy)
	 * 	3. Create an empty 
	 */
	private void reset() {
		searchTree = new ConcurrentSkipListSet<ParCELNode>(heuristic);
		
		//allDescriptions = new TreeSet<Description>(new ConceptComparator());
		allDescriptions = new ConcurrentSkipListSet<Description>(new ConceptComparator());
		
		partialDefinitions = new TreeSet<ParCELExtraNode>(new ParCELCompletenessComparator());
		counterPartialDefinitions = new TreeSet<ParCELExtraNode>(new ParCELCorrectnessComparator());
		
		//clone the positive examples for this set
		this.uncoveredPositiveExamples = new HashSet<Individual>();
		this.uncoveredPositiveExamples.addAll(this.positiveExamples);	//create a copy of positive examples used to check the completeness

		this.coveredNegativeExamples = new HashSet<Individual>();		
		
		descriptionTested = 0;
		maxAccuracy = 0;
	}
	
	
	/**=========================================================================================================<br>
	 * Check if the learner can be terminated
	 * 
	 * @return True if termination condition is true (asked to stop, complete definition found, or timeout),
	 * 			false otherwise 
	 */
	private boolean isTerminateCriteriaSatisfied() {		
		return 	stop || 
				done ||
				counterDone ||
				timeout;
	}
	
	
	/**=========================================================================================================<br>
	 * Set heuristic will be used 
	 * 
	 * @param newHeuristic
	 */
	public void setHeuristic(ParCELHeuristic newHeuristic) {
		this.heuristic = newHeuristic;
		
		if (logger.isInfoEnabled())
			logger.info("Changing heuristic to " + newHeuristic.getClass().getName());
	}
	
	
	/**=========================================================================================================<br>
	 * Stop the learning algorithm: Stop the workers and set the "stop" flag to true
	 */
	@Override
	public void stop() {
		
		if (!stop) {			
			stop = true;
			
			List<Runnable> waitingTasks = workerPool.shutdownNow();
			
			
			
			try {
				workerPool.awaitTermination(0, TimeUnit.SECONDS);
			}
			catch (InterruptedException ie) {
				logger.error(ie);
			}
			
			//put the unexecuted tasks back to the search tree
			logger.debug("Put incompleted task back to the search tree: " + waitingTasks.size());
			for (Runnable node : waitingTasks)
				searchTree.add(((ParCELWorkerExV2)node).getProcessingNode());
			
			//don't stop immediately, wait until all workers finish their jobs
			/*
			while (workerPool.getQueue().size() > 0) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			*/
		}
	}
	
	
	/**=========================================================================================================<br>
	 * Get the currently best description in the set of partial definition
	 */
	@Override
	public Description getCurrentlyBestDescription() {
		if (partialDefinitions.size() > 0) {
			return partialDefinitions.iterator().next().getDescription();
		}
		else
			return null;
	}


	/**=========================================================================================================<br>
	 * Get all partial definition without any associated information such as accuracy, correctness, etc. 
	 */
	@Override
	public List<Description> getCurrentlyBestDescriptions() {
		return PLOENodesToDescriptions(partialDefinitions);
	}
	
	
	/**=========================================================================================================<br>
	 * Convert a set of PLOENode into a list of descriptions
	 * 
	 * @param nodes Set of PLOENode need to be converted
	 * 
	 * @return Set of descriptions corresponding to the given set of PLOENode
	 */
	private List<Description> PLOENodesToDescriptions(Set<ParCELExtraNode> nodes) {
		List<Description> result = new LinkedList<Description>();
		for (ParCELExtraNode node : nodes)
			result.add(node.getDescription());
		return result;
	}
	
	
	/**=========================================================================================================<br>
	 * The same as getCurrentBestDescription.  An evaluated description is a description with 
	 * its evaluated properties including accuracy and correctness  
	 */
	@Override
	public EvaluatedDescription getCurrentlyBestEvaluatedDescription() {
		if (partialDefinitions.size() > 0) {
			ParCELNode firstNode = partialDefinitions.iterator().next();
			return new EvaluatedDescription(firstNode.getDescription(), new ParCELScore(firstNode));
		}
		else
			return null;
	}

	
	/**=========================================================================================================<br>
	 * Get all partial definitions found so far 
	 */
	@Override
	public TreeSet<? extends EvaluatedDescription> getCurrentlyBestEvaluatedDescriptions() {
		return extraPLOENodesToEvaluatedDescriptions(partialDefinitions);
	}

	
	/**=========================================================================================================<br>
	 * Method for PLOENode - EvaluatedDescription conversion
	 * 
	 * @param partialDefs Set of ExtraPLOENode nodes which will be converted into EvaluatedDescription 
	 * 
	 * @return Set of corresponding EvaluatedDescription  
	 */
	private TreeSet<? extends EvaluatedDescription> extraPLOENodesToEvaluatedDescriptions(Set<ParCELExtraNode> partialDefs) {
		TreeSet<EvaluatedDescription> result = new TreeSet<EvaluatedDescription>(new EvaluatedDescriptionComparator());
		for (ParCELExtraNode node : partialDefs) {
			result.add(new EvaluatedDescription(node.getDescription(), new ParCELScore(node)));
		}
		return result;
	}
	
	
	/**=========================================================================================================<br>
	 * Get the overall completeness of all partial definition found
	 * 
	 * @return Overall completeness so far
	 */
	public double getCurrentlyOveralMaxCompleteness() {
		return 1 - (uncoveredPositiveExamples.size()/(double)positiveExamples.size());
	}
	
	
	/**=========================================================================================================<br>
	 * Get the list of learning problem supported by this learning algorithm
	 * 
	 * @return List of supported learning problem 
	 */
	public static Collection<Class<? extends AbstractLearningProblem>> supportedLearningProblems() {
		Collection<Class<? extends AbstractLearningProblem>> problems = new LinkedList<Class<? extends AbstractLearningProblem>>();
		problems.add(ParCELPosNegLP.class);
		return problems;
	}


	//methods related to the compactness: get compact definition, set compactor
	public SortedSet<ParCELExtraNode> getReducedPartialDefinition(ParCELReducer reducer) {
		return reducer.reduce(partialDefinitions, positiveExamples, uncoveredPositiveExamples.size());
	}
	
	
	public SortedSet<ParCELExtraNode> getReducedPartialDefinition() {
		return this.getReducedPartialDefinition(this.reducer);
	}
	
	
	public void setCompactor(ParCELReducer newReducer) {
		this.reducer = newReducer;
	}


	//------------------------------------------
	// getters for learning results
	//------------------------------------------
	
	public double getMaxAccuracy() {
		return maxAccuracy;
	}
	
	@Override
	public int getCurrentlyBestDescriptionLength() {
		return bestDescriptionLength;
	}
	
	
	@Override
	public boolean isRunning() {
		return !stop && !done && !timeout;
	}
	
	
	public int getClassExpressionTests() {
		return descriptionTested;
	}
	
	
	@Override
	public int getSearchTreeSize() {		
		return (searchTree!= null? searchTree.size() : -1);
	}

	
	public int getMaximumHorizontalExpansion() {
		return maxHorizExp;
	}

	
	public Set<ParCELExtraNode> getPartialDefinitions() {
		return partialDefinitions;
	}
	
	
	/*
	public Set<PDLLNode> getSearchTree() {
		return searchTree;
	}
	*/
	
	public Collection<ParCELNode> getSearchTree() {
		return searchTree;
	}
		
	
	public ParCELHeuristic getHeuristic() {
		return heuristic;
	}
	

	public boolean isTimeout() {
		return timeout;
	}
	
	
	public boolean isDone() {
		return done; 
	}
	
	
	public long getLearningTime() {
		return miliLearningTime;
	}
	
	//------------------------------------------------
	// setters and getters for configuration options
	//------------------------------------------------
	
	@Autowired(required=false)
	public void setRefinementOperator(RefinementOperator refinementOp) {
		this.refinementOperator = refinementOp;
	}
	
	public RefinementOperator getRefinementOperator() {
		return this.refinementOperator;
	}
	
	@Autowired(required=false)
	public void setSplitter(ParCELDoubleSplitterAbstract splitter) {
		this.splitter = splitter;
	}
	
	public int getNoOfReducedPartialDefinition() {
		return this.noOfReducedPartialDefinition;
	}
	
	public SortedSet<ParCELExtraNode> getCurrentCounterPartialDefinitions() {
		return this.counterPartialDefinitions;
	}

	
	@Override
	public boolean terminatedByCounterDefinitions() {
		return this.counterDone;
	}
	
	@Override
	public boolean terminatedByPartialDefinitions() {
		return this.done;
	}


	@Override
	public long getTotalDescriptions() {		
		return allDescriptions.size();
	}

	@Override
	public double getCurrentlyBestAccuracy() {		
		return 	((positiveExamples.size() - uncoveredPositiveExamples.size()) + negativeExamples.size()) /
				(double)(positiveExamples.size() + negativeExamples.size());
	}
	
	@Override
	public int getWorkerPoolSize() {
		return this.workerPool.getQueue().size();
	}
	
	/*
	public long getTotalDescriptions();
	public int getCurrentlyBestDescriptionLength();
	public double getCurrentlyBestAccuracy();
	public int getWorkerPoolSize();
	public int getSearchTreeSize();
	*/
	
	@Override
	public int getCurrentlyMaxExpansion() {
		// TODO Auto-generated method stub
		return this.maxHorizExp;
	}

	
}
