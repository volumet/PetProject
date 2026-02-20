#!/bin/bash

/opt/mssql/bin/sqlservr &

echo "Waiting for SQL Server..."
sleep 20

echo "Login with database user $DB_USERNAME"
/opt/mssql-tools/bin/sqlcmd \
   -U "$DB_USERNAME" \
   -P "$DB_PASSWORD" \
   -Q "IF DB_ID('RoomManagement') IS NULL CREATE DATABASE RoomManagement"

wait