ALTER TABLE image_asset ADD COLUMN generation_owner varchar(36);
ALTER TABLE image_asset ADD COLUMN generation_lease_expires_at timestamp;

CREATE INDEX idx_image_asset_generation_claim
    ON image_asset(id, generation_lease_expires_at);
