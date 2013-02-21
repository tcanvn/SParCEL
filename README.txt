
This file provides instructions for checking out and running the projects under Eclipse.

1) Introduction: This branch contains 4 Java projects, 1 examples folder and 1 libraries (lib) folder.

   * Projects:
	i) dllearner-components-core: core components of DLLearner (indlucing the a learning framework and some algorithms).
	ii) dllearner-interfaces: interfaces (CLI and GUI) for DLLearner framework.
	iii) parcel-components-core: parcel algorithms family (parcel and parcelex)
	iv) parcel-interfaces: CLI interface for ParCEL (also DLLearner algorithms)

    * Libraries: contains libraries for all 4 projects.

    * Examples: contains learning examples (including datasets and learning configuration files)
	Learning configuration file naming convention: 
	 _cross: cross validation.
	 _learn: learning only.
	 _fort: fortification (currently the cross validation procedure for fortification has not been cleaned up, so the result may be messy).

	For example: 
	- "Aunt_celoe_cross.conf" is a configuration file for running cross validation for CELOE on Aunt dataset.
	- "Uncle_parcel_learn.conf" is a configuration file for learning Uncle datatset using ParCEL.

2) Checking out: Dependency between projects is as follows (A --> B means A depends on B):
	dllearner-components-core --> lib 
	dllearn-interfaces --> dllearner-components-core, lib
	parcel-components-core --> dllearner-components-core, lib
	parcel-interfaces --> parcel-components-core, dllearner-components-core, lib

   These project can be imported directly into Eclipse. They should be placed in the same parent folder.

3) Running:
   - Main class: org.dllearner.cli.ParCEL.CLI (in parcel-interfaces project)
   - Arguments: 
	i) path to learning configuration file. If all projects are placed in the same parent folder, the path should be: "../examples/<...>"
	ii) classpath must includes folder "parcel-interfaces\resources\". This folder contains configuration of Spring framework and log4j
 
   We also provide launch configuration files for some examples in the folder "parcel-interfaces\launch-conf-file\"