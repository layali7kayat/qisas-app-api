-- Run once against the Cloudflare D1 database used by Dramio.
CREATE TABLE IF NOT EXISTS story_notification_subscriptions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  visitor_id TEXT NOT NULL,
  story_id INTEGER NOT NULL,
  fcm_token TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(visitor_id, story_id, fcm_token)
);
CREATE INDEX IF NOT EXISTS idx_story_notification_subscriptions_story_enabled
ON story_notification_subscriptions(story_id, enabled);
