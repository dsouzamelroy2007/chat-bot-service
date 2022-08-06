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
echo "Building docker image"
echo "================================="

docker image build -t chat-bot-service .
if [ "$?" -ne 0 ]; then
	echo "================================="
    echo "docker image not created!"
    echo "================================="
    exit 1
else
	echo "================================="
	echo "Docker image created"
	echo "================================="
fi

docker container stop chatbotmongodb
docker container rm chatbotmongodb
docker-compose up -d