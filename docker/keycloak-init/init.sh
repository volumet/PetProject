#!/bin/bash

set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh

# Check for readiness
echo "Waiting for Keycloak to be ready..."
until $KCADM config credentials \
  --server "$KEYCLOAK_BASE_URL" \
  --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
  --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" > /dev/null 2>&1; do
  echo "Waiting for Keycloak startup..."
  sleep 2
done

# Authentication
echo "Logging in with kcadm..."
$KCADM config credentials \
  --server "$KEYCLOAK_BASE_URL" \
  --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
  --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"

# Create realm
if $KCADM get realms/volumet-realm > /dev/null 2>&1; then
  echo "Realm existed!"
else
  echo "Creating realm..."
  $KCADM create realms \
    -s realm=volumet-realm \
    -s enabled=true
fi

# Create roles
REALM=volumet-realm
$KCADM get roles -r $REALM | grep -q '"name"[[:space:]]*:[[:space:]]*"ADMIN"' || \
$KCADM create roles -r $REALM -s name=ADMIN

$KCADM get roles -r $REALM | grep -q '"name"[[:space:]]*:[[:space:]]*"USER"' || \
$KCADM create roles -r $REALM -s name=USER

DEFAULT_ROLE="default-roles-$REALM"
if ! $KCADM get roles/$DEFAULT_ROLE/composites -r $REALM | \
  grep -q "\"name\"[[:space:]]*:[[:space:]]*\"USER\""; then
    echo "Adding USER to $DEFAULT_ROLE"
    $KCADM add-roles -r $REALM  \
      --rname $DEFAULT_ROLE \
      --rolename USER
else
  echo "USER already part of $DEFAULT_ROLE"
fi

# Create client
CLIENT_ID=$($KCADM get clients -r volumet-realm -q clientId=volumet-app --fields id --format csv | tail -n 1 | tr -d '"')
if [ -z "$CLIENT_ID" ]; then
  echo "Creating client..."
  CLIENT_ID=$(/opt/keycloak/bin/kcadm.sh create clients \
      -r volumet-realm \
      -s clientId=volumet-app \
      -s enabled=true \
      -s publicClient=false \
      -s directAccessGrantsEnabled="$DIRECT_ACCESS_ENABLED" \
      -s secret="$CLIENT_SECRET" \
      -i)
else
  echo "Client existed!"
fi

# Register backend as audience
MAPPER_EXISTS=$($KCADM get clients/"$CLIENT_ID"/protocol-mappers/models \
  -r volumet-realm | grep -c volumet-app-audience || true)

if [ "$MAPPER_EXISTS" -eq 0 ]; then
  echo "Creating audience mapper..."
  $KCADM create clients/"$CLIENT_ID"/protocol-mappers/models \
    -r volumet-realm \
    -s name=volumet-app-audience \
    -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper \
    -s 'config."included.client.audience"=volumet-app' \
    -s 'config."id.token.claim"=false' \
    -s 'config."access.token.claim"=true'
else
  echo "Audience mapper already exists."
fi

ADMIN_USERNAME=volumet-admin
if $KCADM get users -r $REALM -q username=$ADMIN_USERNAME | \
  grep -q '"username"'; then
    echo "User '$ADMIN_USERNAME' already existed in realm '$REALM'."
else
  echo "Creating user '$ADMIN_USERNAME' in realm '$REALM'"
  $KCADM create users -r $REALM \
    -s username=$ADMIN_USERNAME \
    -s enabled=true \
    -s email=heroziber@gmail.com \
    -s emailVerified=true \
    -s firstName=Volumet \
    -s lastName=Administrator

    echo "Setting password for '$ADMIN_USERNAME'"
    $KCADM set-password \
      -r $REALM \
      --username $ADMIN_USERNAME \
      --new-password "$KC_BOOTSTRAP_ADMIN_PASSWORD" \
      --temporary=false

    echo "Assigning realm-admin role to '$ADMIN_USERNAME'"
    # Realm admin role
    $KCADM add-roles \
      -r $REALM \
      --uusername $ADMIN_USERNAME \
      --cclientid realm-management \
      --rolename realm-admin

    # App admin role
    $KCADM add-roles \
      -r $REALM \
      --uusername "$ADMIN_USERNAME" \
      --rolename ADMIN
fi