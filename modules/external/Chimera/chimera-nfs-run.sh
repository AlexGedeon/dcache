#!/bin/sh

# chkconfig: 345 25 75
# description: chimera nfs3 startup script

PORTMAP_PORT=111

if [  -z ${ourHomeDir} ]
then
    ourHomeDir=/opt/d-cache
fi

log=/var/log/chimera-nfsv3.log
pfile=/var/run/chimera-nfsv3.pid

. ${ourHomeDir}/classes/extern.classpath
. ${ourHomeDir}/config/dCacheSetup


#  A handy wrapper to allow us to delay continuing until some event has
#  happened.  A function is called to check whether the event has happened yet.
#  If that method returns 0, we assume the event has happened, other
#  return-codes will result in the code looping until a timeout has occurred.
#  Should the function time-out whilst waiting for an event, an error message
#  is emitted.
#
#  Parameters:
#    $1  the function called when checking if the event has happened,
#    $2  the message to display if waiting is required,
#    $3  the error message to display if timeout
#
#  Returns 0 on success, 1 on timeout.
#
function waitForEvent()
{
    for try in 0 1 2 3 4 5 6 7 8 9 10 too-much; do
        $1 
        if [ $? -eq 0 ]; then
            [ "x$try" != "x0" ] && echo
            break
        fi

        if [ "x$try" = "x0" ]; then
            echo -n "$2: "
        else
            echo -n "."
        fi

        if [ "x$try"  = "xtoo-much" ]; then
            echo
            echo "$3"
            return 1
        else
            sleep 1
        fi
    done

    return 0
}


portmapPortPresent()
{
    netstat -na | grep [^0-9]$PORTMAP_PORT >/dev/null
}

portmapAnswersQueries()
{
    rpcinfo -p >/dev/null 2>&1
}

nfsV3ServiceRegistered()
{
    rpcinfo -t localhost 100003 3 >/dev/null 2>&1
}


dCacheChimeraStart()
{
  if [ -f ${pfile} ]
  then
    pid=`cat ${pfile}`
    kill -0 ${pid} > /dev/null 2>&1
    if [ $? -eq 0 ]
    then
      echo "Old NFS process still running"
      exit 1
    fi
  fi

  echo "Starting Chimera-NFSv3 interface"
  ${java} ${java_options} \
     -classpath ${ourHomeDir}/classes/cells.jar:${externalLibsClassPath} \
     org.dcache.chimera.nfs.v3.Main2 \
     ${ourHomeDir}/config/chimera-config.xml > ${log} 2>&1 &
  echo $! > ${pfile}

  waitForEvent portmapPortPresent "Waiting for portmap port" \
          "Chimera portmap port is taking too long to appear; carrying on"

  waitForEvent portmapAnswersQueries "Waiting for portmap service" \
          "Chimera portmap is taking too long to appear; carrying on"

  waitForEvent nfsV3ServiceRegistered "Waiting for NFS server to register itself" \
          "Chimera is taking too long; giving up."
}


dCacheChimeraStop()
{
  if [ -f ${pfile} ]
  then
    pid=`cat ${pfile}`
    kill -0 ${pid} > /dev/null 2>&1
    if [ $? -eq 0 ]
    then
      echo "Shutting down Chimera-NFSv3 interface"
      kill `cat ${pfile}`
      rm -f ${pfile}
    else
      echo "NFS process not running"
    fi
  else
    echo "Pid file missing. NFS process not running?"
  fi
}
case $1 in

    start)
        dCacheChimeraStart
        ;;

    stop)
        dCacheChimeraStop
        ;;

    restart)
        dCacheChimeraStop
        dCacheChimeraStart
        ;;
    *)
        echo "Usage: `basename $0` [start | stop | restart]"
        exit 1;

esac
exit 0
