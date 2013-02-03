echo "------------------------------------"
echo "--- Configuration file: $1"
echo "------------------------------------"

java -Xmx2G -jar target/dl-learner.jar $1

mv log/interfaces.log "log/"${1##*/}"_"$(date +"%Y%m%d_%H%M")".log"
