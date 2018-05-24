#!/bin/bash

export LEIN_ROOT=1

lein ddl ${DB_CONTAINER} up
lein dml ${DB_CONTAINER} down
lein dml ${DB_CONTAINER} up

lein ring server-headless
#java -jar target/laundry-3.0.0-SNAPSHOT-standalone.jar

