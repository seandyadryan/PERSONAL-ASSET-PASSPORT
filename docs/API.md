# REST API v1

Base URL: your configured HTTPS origin. JSON requests. Send `Authorization: Bearer <Firebase ID token>` for `/api/*`. `/health` is public liveness only; it does not verify database or Firebase readiness. Development OpenAPI UI: `/swagger`.

| Method | Route | Behavior |
|---|---|---|
| GET | `/api/me` | Verified UID/email, FREE entitlement, cloudSyncEnabled false |
| GET | `/api/assets?page=1&pageSize=25&search=Camera` | Owner-filtered page (max 100), count and items |
| GET | `/api/assets/{uuid}` | One active owned asset; otherwise 404 |
| POST | `/api/assets` | Validate/create owned asset, returns 201 + Location |
| PUT | `/api/assets/{uuid}` | Full replacement of editable fields with required current version |
| DELETE | `/api/assets/{uuid}?version=1` | Soft delete; stale version returns 409 |
| GET | `/api/dashboard` | Counts and purchase totals per currency |

Create body (optional string fields default to empty):

```json
{
  "name": "Camera", "category": "Photography", "currency": "USD", "price": 1299.00,
  "brand": "Example", "model": "Model A", "serial": "SN123", "vendor": "Local shop",
  "notes": "", "purchaseDate": "2026-09-17", "warrantyEnd": "2027-09-17",
  "returnEnd": "2026-10-01", "version": 1
}
```

No `ownerUid`, entitlement, permission or price-plan field is accepted as authority. IDs, timestamps and owner are set by the server. Asset purchase price is user-entered inventory information, not subscription pricing. Currency is one of USD/EUR/GBP/AUD/CAD/SGD/IDR/JPY. Name 1–160, product identifiers max 200, notes max 4000, price non-negative with at most two decimals. Deadlines cannot precede purchase.

Responses omit owner identifiers from asset DTOs. Authentication failures return 401. Other users' IDs return 404. Invalid fields return validation problem details (400), stale version/free limit 409, throttling 429. Unexpected errors use problem details without exposing server exception internals in production. PostgreSQL serialization conflicts during competing creates should be retried after refreshing; the MVP does not supply idempotent replay or synchronization.

API data is separate from the mobile local vault until a later opt-in sync implementation. Upload, OCR, AI, billing and sync routes deliberately do not exist yet.
