-- Transactional outbox: lease columns + domain scoped publishing.
--
-- The relay claims a row before publishing it (claimed_at + claim_token) so two
-- replicas of the same service never publish the same event twice; when the
-- publisher dies the lease expires and the row becomes claimable again.
--
-- next_attempt_at carries the exponential backoff between retries and
-- event_key carries the Kafka record key (the recipient for the email flows).
ALTER TABLE "outbox" ADD COLUMN IF NOT EXISTS "event_key" VARCHAR(200) DEFAULT NULL;
ALTER TABLE "outbox" ADD COLUMN IF NOT EXISTS "next_attempt_at" TIMESTAMP DEFAULT NULL;
ALTER TABLE "outbox" ADD COLUMN IF NOT EXISTS "claimed_at" TIMESTAMP DEFAULT NULL;
ALTER TABLE "outbox" ADD COLUMN IF NOT EXISTS "claim_token" VARCHAR(100) DEFAULT NULL;

-- Drives the "which rows are due" query: pending rows ordered by age.
CREATE INDEX IF NOT EXISTS "idx_outbox_due" ON "outbox" ("status", "next_attempt_at", "created_at");

-- Publishers only claim the domains their service owns (aggregate_type).
CREATE INDEX IF NOT EXISTS "idx_outbox_aggregate" ON "outbox" ("aggregate_type", "status");
