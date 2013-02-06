@echo off

IF EXIST "parcel-cli.jar" (
	
	echo ------------------------------------
	echo --- Configuration file: %1
	echo ------------------------------------

	java -Xmx1G -jar parcel-cli.jar %1
) ELSE (
	IF EXIST "target\parcel-cli.jar" (
		echo ------------------------------------
		echo --- Configuration file: %1
		echo ------------------------------------
		
		java -Xmx1G -jar target\parcel-cli.jar %1
		
	) ELSE (
	 	echo "parcel-cli" is not found.
	 	echo Recompile this project and try again.
	)
)
