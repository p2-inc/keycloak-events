# Publishing your own events

Your application can publish its own events to Keycloak. They are delivered to the realm's [webhooks](webhooks.md) alongside Keycloak's user and admin events, so one webhook endpoint can receive both.

## Before you start

- The `ext-event-webhook` listener must be enabled in the realm (see [Enable webhooks](webhooks.md#enable-webhooks)).
- The caller needs the `publish-events` role. This extension adds it as a client role of the realm's `realm-management` client.

## Publish an event

```
POST /auth/realms/:realm/events
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "type": "myapp.INVOICE-PAID",
  "details": {
    "invoiceId": "inv_1234",
    "amount": "49.00"
  }
}
```

| Response | Meaning |
| --- | --- |
| `202` | The event was accepted |
| `400` | The event is malformed, for example it has no `type` |
| `403` | The caller doesn't have the `publish-events` role |
| `409` | The `type` uses a reserved prefix |

The server fills in the event's `time`, if you leave it out, and its `authDetails` (the realm, client, user and IP address of the caller).

The event format is described in the Phase Two [Audit Log API](https://phasetwo.io/docs/audit-logs/api) documentation.

## What it doesn't do

- **Types can't start with `access.`, `admin.` or `system.`** Those prefixes are reserved for events Keycloak produces.
- **Published events aren't saved in Keycloak's event store,** so they don't appear in the admin console's **Events** pages. They go to webhooks only.
- **A `202` doesn't mean a webhook received the event.** Delivery happens afterwards, in the background.
