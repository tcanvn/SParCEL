@echo off

echo ------------------------------------
echo --- Configuration file: %1
echo ------------------------------------

java -Xmx2G -jar target\dl-learner.jar %1
