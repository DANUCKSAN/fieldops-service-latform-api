# FieldOps operations workflow

This monolithic service implements the first complete FieldOps workflow:

1. An `ADMIN` or `WAREHOUSE_OPR` registers product variations.
2. An `ADMIN` creates the five installer records once their real names are known.
3. An `ADMIN` or `WAREHOUSE_OPR` records warehouse deliveries as stock receipts.
4. The job form loads product and installer options.
5. An `ADMIN` or `WAREHOUSE_OPR` submits the CRM job ID, customer details,
   one panel, one battery, one inverter, and one installer.
6. The job and all material reservations commit in one PostgreSQL transaction.

## Project structure

Application code uses a layer-first package structure under
`org.electrifyingaustralia.fieldops`:

- `controller`: HTTP endpoints;
- `service`: business workflows and transaction boundaries;
- `repository`: Spring Data persistence interfaces;
- `entity`: JPA domain entities;
- `dto/request` and `dto/response`: API contracts;
- `enums`, `exception`, `constant`, and `config`: shared supporting types.

`FieldOpsApplication` remains at the package root so Spring scans every layer.

## Inventory meaning

Inventory has three quantities:

- `onHandQuantity`: physically in the warehouse.
- `reservedQuantity`: allocated to created jobs.
- `availableQuantity`: `onHandQuantity - reservedQuantity`.

Creating a job increases `reservedQuantity`, so availability updates immediately.
It does not reduce physical on-hand stock. A later dispatch workflow can convert a
reservation into a physical stock issue, and a later cancellation workflow can
release a reservation.

## Authentication and roles

All endpoints require a valid FieldOps JWT. The token must include exactly one
FieldOps role in the `roles` claim.

| Operation | ADMIN | WAREHOUSE_OPR |
| --- | --- | --- |
| Create/list products | Yes | Yes |
| Receive/list stock | Yes | Yes |
| Create installer | Yes | No |
| List installers | Yes | Yes |
| Create/list/view jobs | Yes | Yes |

Every operations request resolves the local `app_users` record and rejects a
disabled user. Audit records reference that local user; actor identity is never
accepted from request JSON.

## 1. Register product variations

`POST /api/v1/products`

```json
{
  "sku": "JINKO-JKM440N-54HL4R-V",
  "category": "PANEL",
  "brand": "JINKO",
  "model": "Tiger Neo 440W",
  "capacity": 440
}
```

Allowed combinations:

- `PANEL`: `JINKO`, `AIKO`; capacity is measured in `W`.
- `BATTERY`: `TESLA`, `SIGEN`, `ANKER_SOLIX`, `SUNGROW`, `GOODWE`;
  capacity is measured in `KWH`.
- `INVERTER`: `TESLA`, `SIGEN`, `ANKER_SOLIX`, `SUNGROW`, `GOODWE`;
  capacity is measured in `KW`.

The server infers the unit, creates an empty inventory balance, and enforces a
case-insensitive unique SKU.

Use `GET /api/v1/products?category=PANEL` for product dropdown data.
Omit `category` to return all active products. Each item contains brand, model,
capacity, unit, and current inventory quantities.

## 2. Create installer options

`POST /api/v1/installers` (ADMIN only)

```json
{
  "displayName": "Alex Smith"
}
```

Create the five real installer records this way. No placeholder installers are
seeded. Use `GET /api/v1/installers` to populate the active-installer dropdown.

## 3. Receive stock

`POST /api/v1/inventory/receipts`

```json
{
  "receiptReference": "DELIVERY-2026-0042",
  "receivedAt": "2026-09-01T00:15:00Z",
  "note": "September warehouse delivery",
  "items": [
    {
      "productId": "e759011f-4f54-4dbc-b588-737e58e28b9d",
      "quantity": 100
    },
    {
      "productId": "1c92477c-3e16-4e81-9d7a-46c266a68c21",
      "quantity": 10
    }
  ]
}
```

Receipt references are unique and products cannot be repeated in one receipt.
The API locks all affected balances, increases on-hand stock, and writes one
immutable stock movement per item. Retrying the same receipt reference returns
`409` and never adds the stock twice.

Use `GET /api/v1/inventory` or
`GET /api/v1/inventory?category=BATTERY` for the stock screen.

## 4. Create and allocate a job

`POST /api/v1/jobs`

```json
{
  "jobId": "CRM-2026-001842",
  "customerName": "Priya Sharma",
  "location": "12 George Street, Parramatta NSW 2150",
  "jobDate": "2026-09-18",
  "installerId": "a43ef1fa-f03e-4b27-9097-dd55d2043c33",
  "materials": [
    {
      "productId": "e759011f-4f54-4dbc-b588-737e58e28b9d",
      "quantity": 22
    },
    {
      "productId": "1c92477c-3e16-4e81-9d7a-46c266a68c21",
      "quantity": 1
    },
    {
      "productId": "9f82908c-e62c-41f8-a259-77e54067258b",
      "quantity": 1
    }
  ]
}
```

The request must contain exactly three distinct active products: one `PANEL`,
one `BATTERY`, and one `INVERTER`. Capacity, brand, model, and unit come from the
selected catalog records and cannot be overridden by the job request.

The server then:

1. resolves and checks the authenticated FieldOps user;
2. validates the active installer and material composition;
3. locks the three inventory balances in deterministic order;
4. checks every available quantity before changing anything;
5. creates the job with status `ALLOCATED` and snapshot material details;
6. reserves each quantity and writes immutable reservation movements; and
7. commits every change together.

If any item is short, every job, allocation, movement, and inventory change is
rolled back. A case-insensitive unique CRM job ID prevents a retry from reserving
stock twice. Concurrent requests cannot over-reserve a product.

The response is `201 Created` with a `Location` header. Use:

- `GET /api/v1/jobs/{internalUuid}` for job detail.
- `GET /api/v1/jobs/by-job-id/{jobId}` to recover a result after a client timeout
  or duplicate retry.
- `GET /api/v1/jobs?page=0&size=20` for the job list. Page size is capped at 100.

## Error contract

Errors use RFC Problem Details and include a stable `code` property.

| HTTP status | Typical codes |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST` |
| 401 | Missing or invalid JWT |
| 403 | Wrong role, invalid identity claims, or disabled user |
| 404 | `PRODUCT_NOT_FOUND`, `INSTALLER_NOT_FOUND`, `JOB_NOT_FOUND` |
| 409 | `DUPLICATE_PRODUCT_SKU`, `DUPLICATE_RECEIPT_REFERENCE`, `DUPLICATE_JOB_ID`, `INSUFFICIENT_STOCK`, `INACTIVE_PRODUCT`, `INACTIVE_INSTALLER` |
| 422 | `INVALID_PRODUCT_BRAND`, `INVALID_MATERIAL_COMBINATION` |

## Runtime configuration

Database secrets do not have source-code defaults. Set:

```shell
export FIELDOPS_DB_URL='jdbc:postgresql://localhost:5432/fieldops-db'
export FIELDOPS_DB_USERNAME='postgres'
export FIELDOPS_DB_PASSWORD='your-password'
export FIELDOPS_ALLOWED_ORIGINS='http://localhost:3000,https://fieldops.example.com'
```

For tests, set the corresponding `FIELDOPS_TEST_DB_URL`,
`FIELDOPS_TEST_DB_USERNAME`, and `FIELDOPS_TEST_DB_PASSWORD` variables. Integration
tests require PostgreSQL because they verify real row-locking and database
constraints.
