ALTER TABLE image_asset ADD COLUMN generation_owner varchar(36);
ALTER TABLE image_asset ADD COLUMN generation_lease_expires_at timestamp;
