ALTER TABLE image_asset ADD COLUMN model varchar(64);
ALTER TABLE image_asset ADD COLUMN width integer;
ALTER TABLE image_asset ADD COLUMN height integer;
ALTER TABLE image_asset ADD COLUMN seed integer;
ALTER TABLE image_asset ADD COLUMN safe boolean;
ALTER TABLE image_asset ADD COLUMN style_version varchar(64);

UPDATE image_asset
SET model = 'flux',
    width = CASE WHEN aspect_ratio = '1:1' THEN 512 ELSE 768 END,
    height = CASE WHEN aspect_ratio = '1:1' THEN 512 ELSE 432 END,
    seed = 0,
    safe = true,
    style_version = 'uctale-charcoal-v1';

ALTER TABLE image_asset ALTER COLUMN model SET NOT NULL;
ALTER TABLE image_asset ALTER COLUMN width SET NOT NULL;
ALTER TABLE image_asset ALTER COLUMN height SET NOT NULL;
ALTER TABLE image_asset ALTER COLUMN seed SET NOT NULL;
ALTER TABLE image_asset ALTER COLUMN safe SET NOT NULL;
ALTER TABLE image_asset ALTER COLUMN style_version SET NOT NULL;
