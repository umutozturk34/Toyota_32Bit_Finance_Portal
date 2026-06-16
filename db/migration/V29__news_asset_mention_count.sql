-- =====================================================================================================
-- NEWS ↔ ASSET MENTION COUNT
-- =====================================================================================================
-- How many times an article references each linked asset (ticker + name occurrences) — a prominence hint
-- shown in the UI as an "×N" chip badge. Added as a forward migration (not folded into V28) because V28 is
-- already applied in live databases; editing it would break Flyway's checksum. Additive + idempotent, so it
-- runs cleanly on both an existing DB (column appended, existing rows default to 1) and a fresh clone.
-- =====================================================================================================

ALTER TABLE news_article_assets ADD COLUMN IF NOT EXISTS mention_count INT NOT NULL DEFAULT 1;
