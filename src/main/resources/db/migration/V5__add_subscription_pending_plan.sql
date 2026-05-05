ALTER TABLE subscription
    ADD COLUMN pending_plan_id BIGINT REFERENCES membership_plan(id);
