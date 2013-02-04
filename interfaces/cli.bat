@echo off

IF EXIST "dl-learner.jar" (
	
	echo ------------------------------------
	echo --- Configuration file: %1
	echo ------------------------------------

	java -Xmx1G -jar dl-learner.jar %1
) ELSE (
	IF EXIST "target\dl-learner.jar" (
		echo ------------------------------------
		echo --- Configuration file: %1
		echo ------------------------------------
		
		java -Xmx1G -jar target\dl-learner.jar %1
		
	) ELSE (
	 	echo "dl-learner.jar" is not found.
	 	echo Recompile this project and try again.
	)
)