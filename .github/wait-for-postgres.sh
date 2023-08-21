#!/bin/sh

set -e

until PGPASSWORD="test" psql -h "localhost" -U "postgres" finagle_postgres_test -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 1
done

>&2 echo "Postgres is up - executing command"