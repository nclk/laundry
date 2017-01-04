#!/bin/bash

lein ddl sutdb up
lein dml sutdb up

lein ring server-headless
#sudo java -jar target/laundry-0.1.0-SNAPSHOT-standalone.jar

