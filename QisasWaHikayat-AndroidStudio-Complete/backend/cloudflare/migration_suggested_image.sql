-- Run once on the existing Cloudflare D1 database before deploying this Worker.
ALTER TABLE stories ADD COLUMN suggested_image_url TEXT;
