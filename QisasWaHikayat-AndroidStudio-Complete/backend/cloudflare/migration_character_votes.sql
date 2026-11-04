-- Cloudflare D1 character voting. Worker also creates this table on first use.
CREATE TABLE IF NOT EXISTS character_votes (
  story_id INTEGER NOT NULL,
  character_key TEXT NOT NULL CHECK(character_key IN ('character_one','character_two','character_three')),
  visitor_token TEXT NOT NULL,
  vote INTEGER NOT NULL CHECK(vote IN (-1,1)),
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(story_id,character_key,visitor_token),
  FOREIGN KEY(story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_character_votes_story_character ON character_votes(story_id,character_key);
