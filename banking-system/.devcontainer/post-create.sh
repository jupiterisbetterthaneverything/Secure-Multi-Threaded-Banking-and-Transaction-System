#!/usr/bin/env bash
set -e

echo "Installing mysql client..."
sudo apt-get update -qq && sudo apt-get install -y -qq mysql-client

echo "Waiting for MySQL..."
for i in {1..30}; do
  if mysqladmin ping -h db -u bankuser -pbankpass --silent 2>/dev/null; then
    break
  fi
  sleep 2
done

echo "Loading schema..."
mysql -h db -u bankuser -pbankpass bankdb < sql/schema.sql

echo "Downloading dependencies..."
mvn -q -B dependency:go-offline || true

echo "Ready. Run: mvn test"
