#!/bin/bash

echo "========================================="
echo "Starting Kafka Infrastructure"
echo "========================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker first."
    exit 1
fi

# Start services
echo "Starting Zookeeper, Kafka, and Kafka UI..."
docker-compose up -d

# Wait for services to be healthy
echo "Waiting for services to be ready..."
sleep 15

# Check service status
echo ""
echo "Service Status:"
docker-compose ps

echo ""
echo "========================================="
echo "Kafka is ready!"
echo "========================================="
echo "Kafka Broker:  localhost:9092"
echo "Kafka UI:      http://localhost:8090"
echo "Zookeeper:     localhost:2181"
echo ""
echo "Topics created:"
docker exec trading-kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null | grep -v "^$"

echo ""
echo "To view logs:"
echo "  docker-compose logs -f kafka"
echo ""
echo "To stop:"
echo "  docker-compose down"
echo ""
echo "To reset all data:"
echo "  docker-compose down -v"
echo "========================================="
