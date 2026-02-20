#!/bin/bash

set -euo pipefail

echo "Waiting for Keycloak to be ready..."
until /opt/keycloak/bin/kcadm.sh config credentials \
  --server "$KEYCLOAK_BASE_URL" \
  --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
  --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" > /dev/null 2>&1; do
  echo "Waiting for Keycloak startup..."
  sleep 2
done

echo "Logging in with kcadm..."
/opt/keycloak/bin/kcadm.sh config credentials \
  --server "$KEYCLOAK_BASE_URL" \
  --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
  --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"

echo "Creating realm..."
if /opt/keycloak/bin/kcadm.sh get realms/volumet-realm > /dev/null 2>&1; then
  echo "Realm existed!"
else
  /opt/keycloak/bin/kcadm.sh create realms \
    -s realm=volumet-realm \
    -s enabled=true
fi

CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh get clients -r volumet-realm -q clientId=volumet-app --fields id --format csv | tail -n 1)

if [ -z "$CLIENT_ID" ]; then
  echo "Creating client..."
  /opt/keycloak/bin/kcadm.sh create clients \
      -r volumet-realm \
      -s clientId=volumet-app \
      -s enabled=true \
      -s publicClient=false \
      -s secret="$CLIENT_SECRET"
else
  echo "Client existed!"
fi