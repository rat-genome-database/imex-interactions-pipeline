#!/usr/bin/env bash
# shell script to run IMEXInteractionsPipeline
. /etc/profile

APPNAME=IMEXInteractionsPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu,jdepons@mcw.edu
fi

cd $APPDIR
pwd
DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configuration=file://$APPDIR/properties/log4j.properties"
export IMEX_INTERACTIONS_PIPELINE_OPTS="$DB_OPTS $LOG4J_OPTS"

bin/$APPNAME "$@" 2>&1 | tee run.log

mailx -s "[$SERVER] IMEX Interaction Pipeline OK" $EMAIL_LIST < run.log
