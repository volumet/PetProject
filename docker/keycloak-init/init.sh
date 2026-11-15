#!/bin/bash

set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh

# Must match the backend's JWT validation (application.yml, spring.security.oauth2.resourceserver.jwt):
#   iss = $KEYCLOAK_PUBLIC_URL/realms/$REALM   (issuer-uri)
#   aud contains $APP_CLIENT                   (audiences)
REALM="${KEYCLOAK_REALM:-volumet-realm}"
APP_CLIENT="${KEYCLOAK_CLIENT_ID:-volumet-app}"
AUDIENCE_MAPPER="${APP_CLIENT}-audience"

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
if $KCADM get "realms/$REALM" > /dev/null 2>&1; then
  echo "Realm existed!"
else
  echo "Creating realm..."
  $KCADM create realms \
    -s realm="$REALM" \
    -s enabled=true
fi

# Create roles
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
CLIENT_ID=$($KCADM get clients -r "$REALM" -q clientId="$APP_CLIENT" --fields id --format csv | tail -n 1 | tr -d '"')
if [ -z "$CLIENT_ID" ]; then
  echo "Creating client '$APP_CLIENT'..."
  CLIENT_ID=$($KCADM create clients \
      -r "$REALM" \
      -s clientId="$APP_CLIENT" \
      -s enabled=true \
      -s publicClient=false \
      -s directAccessGrantsEnabled="$DIRECT_ACCESS_ENABLED" \
      -s secret="$CLIENT_SECRET" \
      -i)
else
  echo "Client existed!"
fi

# Register backend as audience: access tokens issued to this client carry aud=$APP_CLIENT,
# which the backend requires (spring.security.oauth2.resourceserver.jwt.audiences)
MAPPER_EXISTS=$($KCADM get clients/"$CLIENT_ID"/protocol-mappers/models \
  -r "$REALM" | grep -c "\"$AUDIENCE_MAPPER\"" || true)

if [ "$MAPPER_EXISTS" -eq 0 ]; then
  echo "Creating audience mapper '$AUDIENCE_MAPPER'..."
  $KCADM create clients/"$CLIENT_ID"/protocol-mappers/models \
    -r "$REALM" \
    -s name="$AUDIENCE_MAPPER" \
    -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper \
    -s "config.\"included.client.audience\"=$APP_CLIENT" \
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

# The backend must be configured with exactly these values, or every request is rejected with 401
echo "----------------------------------------------------------------"
echo "Backend JWT validation must use:"
echo "  issuer-uri : ${KEYCLOAK_PUBLIC_URL:-<KEYCLOAK_PUBLIC_URL not set>}/realms/$REALM   (token 'iss')"
echo "  audiences  : $APP_CLIENT   (token 'aud')"
echo "----------------------------------------------------------------"
