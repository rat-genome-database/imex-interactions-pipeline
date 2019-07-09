#!/usr/bin/env bash
# shell script to run IMEXInteractionsPipeline
. /etc/profile

APPNAME=IMEXInteractionsPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu,jthota@mcw.edu,jdepons@mcw.edu,jrsmith@mcw.edu
fi

cd $APPDIR
java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@" 2>&1 | tee run.log

mailx -s "[$SERVER] IMEX Interaction Pipeline OK" $EMAIL_LIST < run.log
