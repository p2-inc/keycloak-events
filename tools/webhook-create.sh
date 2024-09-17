#!/bin/bash

host=http://127.0.0.1:8082
realm=master
user=admin
pass=admin

# Get an access token
DIRECT_GRANT_RESPONSE=$(curl -i --request POST $host/auth/realms/$realm/protocol/openid-connect/token --header "Accept: application/json" --header "Content-Type: application/x-www-form-urlencoded" --data "grant_type=password&username=$user&password=$pass&client_id=admin-cli")
ACCESS_TOKEN=$(echo $DIRECT_GRANT_RESPONSE | grep "access_token" | sed 's/.*\"access_token\":\"\([^\"]*\)\".*/\1/g');

# Construct the json request
json='{ "enabled": "true", "url": "https://pipedream.m.pipedream.net", "secret": "A3jt6D8lz", "eventTypes": [ "access.REMOVE_TOTP", "access.UPDATE_TOTP", "access.LOGIN", "access.LOGOUT", "access.REGISTER", "access.UPDATE_PASSWORD", "access.VERIFY_EMAIL", "access.SEND_VERIFY_EMAIL", "access.RESET_PASSWORD" ] }'

# Create the webhook on the server, and get the id from the location header
response=$(curl --request POST $host/auth/realms/$realm/webhooks --header "Content-Type: application/json" --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN" -d "$json")
headers=$(sed -n '1,/^\r$/p' <<<"$response")
content=$(sed -e '1,/^\r$/d' -e '$d' <<<"$response")
read -r http_code size_header redirect_url < <(tail -n1 <<<"$response")
location=$(grep -oP 'Location: \K.*' <<<"$headers")
id=$(basename $location)

# Do a get on that webhook
curl -vv $host/auth/realms/$realm/webhooks/$id --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN"
