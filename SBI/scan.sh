#!/bin/sh

LOGFILE="scan_report.log"

function start_db() {
    docker-compose -f $DIR/scanner.yml up -d || :
}

function setup() {
    local IMAGE=$1
    [ -z "$IMAGE" ] && { echo "Please provide image to scan" ; exit 1 ; }
    start_db
    [ "$(docker-compose -f $DIR/scanner.yml ps|sed '1,2d'|cut -d' ' -f1|wc -l)" -ne 2 ] && sleep 15 || :
}

function start_scan_local() {
    local IMAGE=$1
    IP=$(docker network inspect bridge --format "{{range .IPAM.Config}}{{.Gateway}}{{end}}")
    setup $1
    CMD="--ip $IP -t Critical -l $LOGFILE $IMAGE"
    echo -e "Scanning is being done for image for Critical Vulnerabilities: $IMAGE"
    $DIR/build/scanner/clair-scanner $CMD
}
