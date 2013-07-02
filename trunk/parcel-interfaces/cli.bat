@echo off

echo ------------------------------------
echo --- Configuration file: '%1'
echo ------------------------------------

if "%1"=="" goto noargs
if not exist %1 goto fnfound

set cli_=
if exist parcel-cli.jar set cli_=true
if exist target\parcel-cli.jar set cli_=true

if defined cli_ (
	if exist target\parcel-cli.jar (
		java -Xmx8G -jar target\parcel-cli.jar %1
	) else (
		java -Xmx8G -jar parcel-cli.jar %1
	)	
	
	ren log\interfaces.log %~nx1_%date:~10,4%%date:~7,2%%date:~4,2%_%time:~0,2%%time:~3,2%.log	
	goto :end
) else (
	echo "'parcel-cli.jar' is not found."
	echo "Please recompile the project and try again."
	goto end
)

:noargs
echo Syntax: cli.bat ^<learning configuration file^>
goto end

:fnfound
echo Learning configuration file is not found

:end
