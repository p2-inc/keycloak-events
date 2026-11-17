# Microsoft Sentinel (`azure-sentinel`)

The `azure-sentinel` payload format sends Keycloak events to a Log Analytics workspace through the [Azure Monitor Logs Ingestion API](https://learn.microsoft.com/en-us/azure/azure-monitor/logs/logs-ingestion-api-overview), where Microsoft Sentinel can query them. Each event becomes a row in a custom table, `KeycloakEvents_CL` in this guide, with one column per event field.

The older HTTP Data Collector API, retired on 14 September 2026, is not supported.

See [Webhook payload formats](../webhook-payload-formats.md) for how formats and the `oauth2-client-credentials` auth type work in general.

## How it fits together

```
Keycloak ──POST rows──▶ Logs Ingestion endpoint ──▶ Data collection rule ──▶ KeycloakEvents_CL ──▶ Sentinel
    │                    (DCE, or the DCR's own)    stream Custom-KeycloakEvents
    └──client credentials──▶ Microsoft Entra ID (token for https://monitor.azure.com)
```

You need:

1. A **custom table** in the workspace with the columns below.
2. A **data collection rule** (DCR) that declares a `Custom-KeycloakEvents` stream with the same columns and routes it to the table.
3. A **Microsoft Entra app registration** with a client secret and the **Monitoring Metrics Publisher** role on the DCR.
4. A **webhook** in Keycloak with `payloadFormat: azure-sentinel` that posts to the DCR's stream.

## Set up Azure

The steps use the Azure CLI, and follow Microsoft's [Logs Ingestion API](https://learn.microsoft.com/en-us/azure/azure-monitor/logs/logs-ingestion-api-overview) guide. Replace the `<...>` values. The Log Analytics workspace must already exist, with Sentinel enabled on it, and the table, the DCR and the workspace must be in the same region.

### 1. Create the custom table

```sh
az monitor log-analytics workspace table create \
  --resource-group <resource-group> \
  --workspace-name <workspace> \
  --name KeycloakEvents_CL \
  --columns TimeGenerated=datetime EventId=string EventUid=string EventCategory=string \
    EventType=string EventTypeFull=string EventResult=string RealmId=string RealmName=string \
    ClientId=string UserId=string Username=string SessionId=string IpAddress=string Error=string \
    AuthRealm=string OperationType=string ResourceType=string ResourcePath=string \
    TargetUserId=string TargetUsername=string Representation=string Details=dynamic
```

### 2. Create the data collection rule

Save this as `keycloak-dcr.json`, filling in the region and the workspace's resource id (`az monitor log-analytics workspace show ... --query id`). The stream columns must match the table and the rows Keycloak sends. The `azure-sentinel` entry of `GET /auth/realms/:realm/webhooks/formats` also lists them.

```json
{
  "location": "<region>",
  "kind": "Direct",
  "properties": {
    "streamDeclarations": {
      "Custom-KeycloakEvents": {
        "columns": [
          { "name": "TimeGenerated", "type": "datetime" },
          { "name": "EventId", "type": "string" },
          { "name": "EventUid", "type": "string" },
          { "name": "EventCategory", "type": "string" },
          { "name": "EventType", "type": "string" },
          { "name": "EventTypeFull", "type": "string" },
          { "name": "EventResult", "type": "string" },
          { "name": "RealmId", "type": "string" },
          { "name": "RealmName", "type": "string" },
          { "name": "ClientId", "type": "string" },
          { "name": "UserId", "type": "string" },
          { "name": "Username", "type": "string" },
          { "name": "SessionId", "type": "string" },
          { "name": "IpAddress", "type": "string" },
          { "name": "Error", "type": "string" },
          { "name": "AuthRealm", "type": "string" },
          { "name": "OperationType", "type": "string" },
          { "name": "ResourceType", "type": "string" },
          { "name": "ResourcePath", "type": "string" },
          { "name": "TargetUserId", "type": "string" },
          { "name": "TargetUsername", "type": "string" },
          { "name": "Representation", "type": "string" },
          { "name": "Details", "type": "dynamic" }
        ]
      }
    },
    "destinations": {
      "logAnalytics": [
        { "name": "workspace", "workspaceResourceId": "<workspace-resource-id>" }
      ]
    },
    "dataFlows": [
      {
        "streams": ["Custom-KeycloakEvents"],
        "destinations": ["workspace"],
        "transformKql": "source",
        "outputStream": "Custom-KeycloakEvents_CL"
      }
    ]
  }
}
```

Create it, then note its immutable id and ingestion endpoint:

```sh
az rest --method put \
  --url "https://management.azure.com/subscriptions/<subscription-id>/resourceGroups/<resource-group>/providers/Microsoft.Insights/dataCollectionRules/keycloak-events?api-version=2023-03-11" \
  --body @keycloak-dcr.json \
  --query "{id: id, immutableId: properties.immutableId, endpoint: properties.endpoints.logsIngestion}"
```

A `Direct` DCR has its own ingestion endpoint. If you use a data collection endpoint (DCE) instead, leave out `"kind"`, add `"dataCollectionEndpointId": "<dce-resource-id>"` under `properties`, and use the DCE's logs ingestion URL as the endpoint.

`transformKql` can filter or reshape rows on the way in (for example `source | where EventType != "CODE_TO_TOKEN"`). It can't add columns the table doesn't have.

### 3. Create the app registration and grant it access

```sh
az ad sp create-for-rbac --name keycloak-events-sentinel
# note appId (client id), password (client secret) and tenant

az role assignment create \
  --assignee <appId> \
  --role "Monitoring Metrics Publisher" \
  --scope <dcr-resource-id>
```

Role assignments can take up to 30 minutes to apply. Until then the ingestion API answers `403`, which is not retried (see [Retries](#retries)), so wait before turning the webhook on.

## Create the webhook

```
POST /auth/realms/:realm/webhooks
```

```json
{
  "enabled": true,
  "url": "<endpoint>/dataCollectionRules/<immutableId>/streams/Custom-KeycloakEvents?api-version=2023-01-01",
  "eventTypes": ["access.*", "admin.*"],
  "payloadFormat": "azure-sentinel",
  "authType": "oauth2-client-credentials",
  "config": {
    "oauth2.tokenUrl": "https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token",
    "oauth2.clientId": "<appId>",
    "oauth2.clientSecret": "<password>",
    "oauth2.scope": "https://monitor.azure.com//.default"
  }
}
```

The scope really does have two slashes: `https://monitor.azure.com//.default`.

To send every realm's events from one server-wide webhook instead, use the catch-all webhook with `WEBHOOK_FORMAT=azure-sentinel` (see [Using a format without a managed webhook](../webhook-payload-formats.md#using-a-format-without-a-managed-webhook)).

Rows usually appear a few minutes after the first events:

```kusto
KeycloakEvents_CL
| sort by TimeGenerated desc
| take 20
```

## The rows

The body is a JSON array of rows. Columns without a value are left out.

| Column | Type | Contents |
| --- | --- | --- |
| `TimeGenerated` | datetime | When the event happened |
| `EventId` | string | The Keycloak event id |
| `EventUid` | string | The id of this webhook send |
| `EventCategory` | string | `user`, `admin`, or the prefix of a custom event type (e.g. `system`) |
| `EventType` | string | The type as Keycloak names it: the user event type (`LOGIN`, `LOGIN_ERROR`, ...) or the admin operation (`CREATE`, `UPDATE`, `DELETE`, `ACTION`) |
| `EventTypeFull` | string | The keycloak-events type, e.g. `access.LOGIN` or `admin.USER-CREATE` |
| `EventResult` | string | `Success`, or `Failure` when the event has an error |
| `RealmId`, `RealmName` | string | The realm the event happened in |
| `ClientId` | string | The client. For admin events, Keycloak records the client's internal id rather than its client id. |
| `UserId`, `Username` | string | The user the event is about. For admin events, the admin who made the change. |
| `SessionId` | string | The user session |
| `IpAddress` | string | The client IP address |
| `Error` | string | The error, e.g. `invalid_user_credentials` |
| `AuthRealm` | string | Admin events: the realm the admin signed in to |
| `OperationType` | string | Admin events: the operation |
| `ResourceType`, `ResourcePath` | string | Admin events: what was changed, e.g. `USER` and `users/<id>` |
| `TargetUserId`, `TargetUsername` | string | Admin events on users: the user that was changed |
| `Representation` | string | Admin events: the JSON representation of the change, when the realm includes representations in admin events. Cut to 32,768 characters. |
| `Details` | dynamic | The event details, e.g. `grant_type`, `auth_method`, `redirect_uri` |

A login:

```json
[
  {
    "TimeGenerated": "2026-11-16T13:48:37.967Z",
    "EventId": "fa74c067-4bd5-4373-8cd2-c9dbc9b4ac6b",
    "EventUid": "14b70e50-f61d-4268-9f7f-1a47fe4fcdb2",
    "EventCategory": "user",
    "EventType": "LOGIN",
    "EventTypeFull": "access.LOGIN",
    "EventResult": "Success",
    "RealmId": "e99e807c-7ca5-4be2-8a6e-6149252f7de8",
    "RealmName": "master",
    "ClientId": "admin-cli",
    "UserId": "44c76053-3b3d-4d40-b510-4446f8ff11bb",
    "Username": "admin",
    "SessionId": "Xve8GHntmvCHyAhCZxXZKbNi",
    "IpAddress": "192.168.65.1",
    "Details": {
      "auth_method": "openid-connect",
      "grant_type": "password",
      "scope": "email profile",
      "client_auth_method": "client-secret",
      "username": "admin"
    }
  }
]
```

An admin creating a user:

```json
[
  {
    "TimeGenerated": "2026-11-16T13:48:13.013Z",
    "EventId": "acf3c0fd-ab21-468c-9de2-1670903fdbfe",
    "EventUid": "64f525bb-5068-4457-a076-b71617bf34a1",
    "EventCategory": "admin",
    "EventType": "CREATE",
    "EventTypeFull": "admin.USER-CREATE",
    "EventResult": "Success",
    "RealmId": "9c3b4868-2563-4b21-8dbf-28ce791d8088",
    "RealmName": "master",
    "ClientId": "fcb84419-1306-4bdf-9dcd-34260dd15bf5",
    "UserId": "25a50ca0-f01e-4041-b928-37cce09e7dbc",
    "Username": "admin",
    "IpAddress": "192.168.65.1",
    "AuthRealm": "master",
    "OperationType": "CREATE",
    "ResourceType": "USER",
    "ResourcePath": "users/e566d0dc-2b62-4002-b769-83f6d2ff036e",
    "Representation": "{\"username\":\"sentinel-user\",\"enabled\":true}",
    "TargetUserId": "e566d0dc-2b62-4002-b769-83f6d2ff036e",
    "TargetUsername": "sentinel-user",
    "Details": {
      "userId": "e566d0dc-2b62-4002-b769-83f6d2ff036e",
      "username": "sentinel-user"
    }
  }
]
```

## Example queries

Failed logins by IP address in 5-minute windows, a starting point for a brute-force analytics rule:

```kusto
KeycloakEvents_CL
| where EventType == "LOGIN_ERROR"
| summarize Failures = count(), Users = dcount(Username) by IpAddress, RealmName, bin(TimeGenerated, 5m)
| where Failures > 10
```

Role and group changes made by admins:

```kusto
KeycloakEvents_CL
| where EventCategory == "admin"
| where ResourceType in ("REALM_ROLE_MAPPING", "CLIENT_ROLE_MAPPING", "GROUP_MEMBERSHIP")
| project TimeGenerated, RealmName, Admin = Username, OperationType, ResourceType, ResourcePath, TargetUsername
```

Logins for one user, with the client and grant type:

```kusto
KeycloakEvents_CL
| where EventType in ("LOGIN", "LOGIN_ERROR") and Username == "jdoe"
| project TimeGenerated, EventResult, ClientId, IpAddress, GrantType = tostring(Details.grant_type), Error
```

The rows aren't mapped to the [ASIM](https://learn.microsoft.com/en-us/azure/sentinel/normalization) schemas, so ASIM-based content doesn't pick them up.

## Retries

| Response | What happens |
| --- | --- |
| `204` | Sent |
| `401` | The cached token is dropped and the send is retried with a new token |
| `400`, `403`, `404`, `413` | Not retried. Usually a stream or column mismatch, a missing role assignment, a wrong DCR id or stream name, or an oversized request. |
| `429`, `5xx`, network errors | Retried with backoff |

If the token endpoint can't be reached, the send is retried with backoff.

## Limits

- Each request carries one event.
- The Logs Ingestion API accepts up to 1 MB per request. A row that would be bigger is sent without its `Details`.
- `Representation` is cut to 32,768 characters, well under Log Analytics' 64 KB limit for a string column.

## Troubleshooting

Enable [logging of webhook send attempts](../webhooks.md#logging-webhook-send-attempts) to see each response status.

| Symptom | Check |
| --- | --- |
| `403` | The app has **Monitoring Metrics Publisher** on the DCR itself, and the assignment has had time to apply. |
| `404` | The URL has the DCR's *immutable* id (`dcr-...`), not its name, and the stream name is `Custom-KeycloakEvents`. |
| `400` | The DCR stream columns match the table and the column list above. The response body names the problem. |
| Token errors in the Keycloak log | `oauth2.tokenUrl` names the right tenant, and the client secret hasn't expired. |
| `204` but no rows | Ingestion takes a few minutes. Also check the DCR's `transformKql` and `outputStream`. |
