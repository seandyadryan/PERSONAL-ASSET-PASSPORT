BEGIN;
CREATE TABLE IF NOT EXISTS schema_migrations(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE "Assets" (
 "Id" uuid PRIMARY KEY, "OwnerUid" varchar(128) NOT NULL,
 "HouseholdId" uuid NULL, "Name" varchar(160) NOT NULL,
 "Brand" text NOT NULL, "Category" text NOT NULL, "Model" text NOT NULL,
 "Serial" text NOT NULL, "Vendor" text NOT NULL, "Notes" text NOT NULL,
 "Currency" text NOT NULL CHECK ("Currency" IN ('USD','EUR','GBP','AUD','CAD','SGD','IDR','JPY')),
 "Price" numeric(18,2) NOT NULL CHECK ("Price" >= 0),
 "PurchaseDate" date, "WarrantyEnd" date, "ReturnEnd" date,
 "Version" integer NOT NULL DEFAULT 1,
 "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL, "DeletedAt" timestamptz,
 CHECK ("PurchaseDate" IS NULL OR "WarrantyEnd" IS NULL OR "WarrantyEnd" >= "PurchaseDate"),
 CHECK ("PurchaseDate" IS NULL OR "ReturnEnd" IS NULL OR "ReturnEnd" >= "PurchaseDate")
);
CREATE INDEX "IX_Assets_OwnerUid_DeletedAt_CreatedAt" ON "Assets"("OwnerUid", "DeletedAt", "CreatedAt");
CREATE TABLE "Events" (
 "Id" uuid PRIMARY KEY, "AssetId" uuid NOT NULL REFERENCES "Assets"("Id"), "Kind" text NOT NULL,
 "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL, "DeletedAt" timestamptz
);
CREATE INDEX "IX_Events_AssetId" ON "Events"("AssetId");
-- Household ownership is reserved for future sharing. No client can assign HouseholdId today.
CREATE TABLE households(id uuid PRIMARY KEY, name text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz);
CREATE TABLE household_members(id uuid PRIMARY KEY, household_id uuid NOT NULL REFERENCES households(id), firebase_uid varchar(128) NOT NULL, role text NOT NULL CHECK(role IN ('Owner','Editor','Viewer')), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz);
CREATE UNIQUE INDEX active_household_member ON household_members(household_id, firebase_uid) WHERE deleted_at IS NULL;
ALTER TABLE "Assets" ADD CONSTRAINT asset_household_fk FOREIGN KEY ("HouseholdId") REFERENCES households(id);
INSERT INTO schema_migrations(version) VALUES(1);
COMMIT;
