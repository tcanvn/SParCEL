FILE1=dl-learner.jar
FILE2=target/dl-learner.jar

if [[ ( -f $FILE1 ) || ( -f $FILE2 ) ]]; then
	echo "------------------------------------"
	echo "--- Configuration file: $1"
	echo "------------------------------------"

	if [ -f $FILE1 ]; then
		java -Xmx1G -jar dl-learner.jar $1
	else
		java -Xmx1G -jar target/dl-learner.jar $1
	fi

	mv log/interfaces.log "log/"${1##*/}"_"$(date +"%Y%m%d_%H%M")".log"
else
	echo "'dl-learner.jar' is not found."
	echo "Please recompile the project and try again."
fi
