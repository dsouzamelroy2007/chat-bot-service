#!/bin/bash

mvn clean
if [ "$?" -ne 0 ]; then
    echo "Maven Clean Unsuccessful!"
    exit 1
fi

mvn package
if [ "$?" -ne 0 ]; then
    echo "Maven packaging Unsuccessful!"
    exit 1
fi

echo "================================="
echo "Building and starting the full stack (app + Postgres + Redis)"
echo "================================="

docker-compose up -d --build
if [ "$?" -ne 0 ]; then
	echo "================================="
    echo "docker-compose up failed!"
    echo "================================="
    exit 1
fi