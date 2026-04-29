-- V8: enforce case-insensitive uniqueness on gym.name
-- Uses a functional index on lower(name) so that 'CrossFit' and 'crossfit' are treated as duplicates.
CREATE UNIQUE INDEX gym_name_lower_uq ON gym (lower(name));
