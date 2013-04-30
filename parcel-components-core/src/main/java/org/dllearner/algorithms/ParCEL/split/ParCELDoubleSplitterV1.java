package org.dllearner.algorithms.ParCEL.split;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.ParCEL.ParCELOntologyUtil;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.KnowledgeSource;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.Individual;
import org.dllearner.kb.OWLFile;
import org.dllearner.utilities.owl.OWLAPIConverter;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * This class implements a splitting strategy for datatype property. In this strategy, the splitted
 * value will be generate in a manner such that there is no range which contains both positive
 * and negative examples to avoid the learning get stuck in its specialisation
 * 
 * <br>
 * 
 * NOTE: values for a data property can be queried from the ontology. However, to find know that
 * 	a value "relates" to positive or negative examples, we need to build a dependence graph because
 * 	a value may not related directly with the value but it may be indirectly related through some 
 * 	object properties. 	
 * 
 * <br>
 * Procedure:
 * <ol>
 * 	<li>filter out a list of numeric datatype properties,</li>
 * 	<li>build a relation graph that connects each value with positive/negative examples related to it,</li>
 * 	<li>apply splitting strategy on the list of values (along with its positive.negative examples)</li>
 * </ol>
 * 
 * @author An C. Tran
 * 
 */

@ComponentAnn(name = "ParCEL double splitter v1", shortName = "parcelSplitterV1", version = 0)
public class ParCELDoubleSplitterV1 implements ParCELDoubleSplitterAbstract {

	private AbstractReasonerComponent reasoner = null;
	private Set<Individual> positiveExamples = null;
	private Set<Individual> negativeExamples = null;

	private KnowledgeSource knowledgeSource = null;
	private OWLOntology ontology = null;

	private Set<OWLDataPropertyExpression> numericDatatypeProperties;
	
	private Logger logger = Logger.getLogger(this.getClass());

	public ParCELDoubleSplitterV1() {

	}

	/**
	 * Create a Splitter given a reasoner, positive and negative examples
	 * 
	 * @param reasoner A reasoner with ontology loaded before
	 * @param positiveExamples Set of positive examples
	 * @param negativeExamples Set of negative examples 
	 */
	public ParCELDoubleSplitterV1(AbstractReasonerComponent reasoner,
			Set<Individual> positiveExamples, Set<Individual> negativeExamples) {
		this.reasoner = reasoner;

		this.positiveExamples = positiveExamples;
		this.negativeExamples = negativeExamples;
	}

	/**
	 * Initialise the Splitter
	 * 
	 * @throws ComponentInitException
	 * @throws OWLOntologyCreationException
	 */
	public void init() throws ComponentInitException {
		if (this.reasoner == null)
			throw new ComponentInitException("There is no reasoner for initialising the Splitter");

		if (this.positiveExamples == null)
			throw new ComponentInitException(
					"There is no positive examples for initialising the Splitter");

		if (this.negativeExamples == null)
			throw new ComponentInitException(
					"There is no negative examples for initialising the Splitter");

		
		// get knowledge source (OWL file to built abox dependency graph
		this.knowledgeSource = reasoner.getSources().iterator().next();

		
		String ontologyPath = ((OWLFile) knowledgeSource).getBaseDir()
				+ ((OWLFile) knowledgeSource).getFileName();

		try {
			ontology = ParCELOntologyUtil.loadOntology(ontologyPath);
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}

		if (!(knowledgeSource instanceof OWLFile))
			throw new RuntimeException("Only OWLFile is supported");

		// get a list of double data type properties for filtering out other properties
		this.numericDatatypeProperties = new HashSet<OWLDataPropertyExpression>();
		for (DatatypeProperty dp : reasoner.getDoubleDatatypeProperties())
			this.numericDatatypeProperties.add(OWLAPIConverter.getOWLAPIDataProperty(dp));
		
		//logger.info("Numeric data properties: " + numericDatatypeProperties);

		//get a list of integer datatype properties
		SortedSet<DatatypeProperty> intDatatypeProperties = reasoner.getIntDatatypeProperties();
		for (DatatypeProperty dp : intDatatypeProperties)
			this.numericDatatypeProperties.add(OWLAPIConverter.getOWLAPIDataProperty(dp));
		
		
		if (logger.isInfoEnabled())
				logger.info("Splitter created: " + this.getClass().getSimpleName() + "...");
		
	}

	/**
	 * Compute splits for all double data properties in the ontology
	 * 
	 * @return A map of datatype properties and their splitting values
	 */
	public Map<DatatypeProperty, List<Double>> computeSplits() {
		// -------------------------------------------------
		// generate relations for positive examples
		// -------------------------------------------------

		Map<DatatypeProperty, ValueCountSet> relations = new HashMap<DatatypeProperty, ValueCountSet>();

		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();

		for (Individual ind : positiveExamples) {
			Map<DatatypeProperty, ValueCountSet> individualRelations = getInstanceValueRelation(
					factory.getOWLNamedIndividual(IRI.create(ind.getURI())), true, null);

			for (DatatypeProperty pro : individualRelations.keySet()) {
				if (relations.keySet().contains(pro))
					relations.get(pro).addAll(individualRelations.get(pro));
				else
					relations.put(pro, individualRelations.get(pro));
			}
		}

		// generate relation for negative examples
		for (Individual ind : negativeExamples) {
			Map<DatatypeProperty, ValueCountSet> individualRelations = getInstanceValueRelation(
					factory.getOWLNamedIndividual(IRI.create(ind.getURI())), false, null);

			for (DatatypeProperty pro : individualRelations.keySet()) {
				if (relations.keySet().contains(pro))
					relations.get(pro).addAll(individualRelations.get(pro));
				else
					relations.put(pro, individualRelations.get(pro));
			}
		}

		// -------------------------------------------------
		// calculate the splits for each data property
		// -------------------------------------------------

		Map<DatatypeProperty, List<Double>> splits = new TreeMap<DatatypeProperty, List<Double>>();

		// - - - . + + + + + . = . = . = . + . = . = . - . = . - - -
		for (DatatypeProperty dp : relations.keySet()) {

			if (relations.get(dp).size() > 0) {
				List<Double> values = new ArrayList<Double>();
				ValueCountSet propertyValues = relations.get(dp);

				int priorType = propertyValues.first().getType();
				double priorValue = propertyValues.first().getValue();

				Iterator<ValueCount> iterator = propertyValues.iterator();
				while (iterator.hasNext()) {
					ValueCount currentValueCount = iterator.next();
					int currentType = currentValueCount.getType();
					double currentValue = currentValueCount.getValue();

					// check if a new value should be generated: when the type changes or the
					// current value belongs to both pos. and neg.
					if ((currentType == 3) || (currentType != priorType)) {
						//calculate the middle/avg. value
						//TODO: how to identify the splitting strategy here? For examples: time,... 
						values.add((priorValue + currentValue) / 2.0);

						//Double newValue = new Double(new TimeSplitter().calculateSplit((int)priorValue, (int)currentValue));
						//if (!values.contains(newValue))
						//	values.add(newValue);
						
						//values.add((priorValue + currentValue) / 2.0);
					}

					// update the prior type and value after process the current element
					priorType = currentValueCount.getType();
					priorValue = currentValueCount.getValue();

				}

				// add processed property into the result set (splits)
				splits.put(dp, values);
				
				if (logger.isInfoEnabled())
					logger.info("Splitting: " + dp + ", no of values: " + relations.get(dp).size()
							+ ", splits: " + values.size());
				
			}
		}
		
		if (logger.isInfoEnabled())
			logger.info("Splitting result: " + splits);

		return splits;
	}

	/**
	 * Find the related values of an individual
	 * 
	 * @param individual
	 *            The individual need to be seek for the related values
	 * @param positiveExample
	 *            True if the given individual is a positive example and false otherwise
	 * @param visitedIndividuals
	 *            Set of individuals that had been visited when finding the related values for the
	 *            given individual
	 * 
	 * @return A map from data property to its related values that had been discovered from the
	 *         given individual
	 */
	private Map<DatatypeProperty, ValueCountSet> getInstanceValueRelation(OWLIndividual individual,
			boolean positiveExample, Set<OWLIndividual> visitedIndividuals) {

		if (visitedIndividuals == null)
			visitedIndividuals = new HashSet<OWLIndividual>();

		// if the individual visited
		if (visitedIndividuals.contains(individual))
			return null;
		else
			visitedIndividuals.add(individual);

		Map<DatatypeProperty, ValueCountSet> relations = new HashMap<DatatypeProperty, ValueCountSet>();

		// get all data property values of the given individual
		Map<OWLDataPropertyExpression, Set<OWLLiteral>> dataPropertyValues = individual
				.getDataPropertyValues(this.ontology);

		// get all object properties value of the given individual
		Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objectPropertyValues = individual
				.getObjectPropertyValues(this.ontology);

		// ---------------------------------------
		// process data properties
		// NOTE: filter the double data property
		// ---------------------------------------
		for (OWLDataPropertyExpression dp : dataPropertyValues.keySet()) {

			if (this.numericDatatypeProperties.contains(dp)) {

				// process values of each data property: create a ValueCount object and add it into
				// the result
				ValueCountSet values = new ValueCountSet();
				for (OWLLiteral lit : dataPropertyValues.get(dp)) {
					ValueCount newValue = new ValueCount(Double.parseDouble(lit.getLiteral()),
							positiveExample); // (value, pos)
					values.add(newValue);
				}

				// if the data property exist, update its values
				if (relations.keySet().contains(dp))
					relations.get(new DatatypeProperty(dp.asOWLDataProperty().getIRI().toString()))
							.addAll(values);
				// otherwise, create a new map <data property - values and add it into the return
				// value
				else
					relations.put(new DatatypeProperty(dp.asOWLDataProperty().getIRI().toString()),
							values);
			}
		}

		// process each object property: call this method recursively
		for (OWLObjectPropertyExpression op : objectPropertyValues.keySet()) {
			for (OWLIndividual ind : objectPropertyValues.get(op)) {
				Map<DatatypeProperty, ValueCountSet> subRelations = getInstanceValueRelation(ind,
						positiveExample, visitedIndividuals);

				// sub-relation == null if the ind had been visited
				if (subRelations != null) {
					for (DatatypeProperty dp : subRelations.keySet()) {
						// if the data property exist, update its values
						if (relations.keySet().contains(dp))
							relations.get(dp).addAll(subRelations.get(dp));
						// otherwise, create a new map <data property - values and add it into the
						// return value
						else
							relations.put(dp, subRelations.get(dp));
					}
				}

			}
		}

		return relations;
	}

	
	//-----------------------------
	// getters and setters
	//-----------------------------
	public AbstractReasonerComponent getReasoner() {
		return reasoner;
	}

	public void setReasoner(AbstractReasonerComponent reasoner) {
		this.reasoner = reasoner;
	}

	public Set<Individual> getPositiveExamples() {
		return positiveExamples;
	}

	public void setPositiveExamples(Set<Individual> positiveExamples) {
		this.positiveExamples = positiveExamples;
	}

	public Set<Individual> getNegativeExamples() {
		return negativeExamples;
	}

	public void setNegativeExamples(Set<Individual> negativeExamples) {
		this.negativeExamples = negativeExamples;
	}

}
