FILE1=parcel-cli.jar
FILE2=target/parcel-cli.jar

if [[ ( -f $FILE1 ) || ( -f $FILE2 ) ]]; then
	echo "------------------------------------"
	echo "--- Configuration file: $1"
	echo "------------------------------------"

	if [ -f $FILE1 ]; then
		java -Xmx4G -jar $FILE1 $1
	else
		java -Xmx4G -jar $FILE2 $1
	fi

	mv log/interfaces.log "log/"${1##*/}"_"$(date +"%Y%m%d_%H%M")".log"
else
	echo "'parcel-cli.jar' is not found."
	echo "Please recompile the project and try again."
fi
