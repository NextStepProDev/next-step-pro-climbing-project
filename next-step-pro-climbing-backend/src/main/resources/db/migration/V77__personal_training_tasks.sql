-- A second kind of entry on the 1:1 plan: a TASK ("stay under 2200 kcal today", "drink 3 litres of
-- water") next to a TRAINING.
--
-- Deliberately a separate ROW rather than a field bolted onto a training. An athlete can nail the
-- session and blow the diet on the same day, and one tick box cannot say that: whatever it reported
-- would be a lie about half the day. Two entries, two ticks, two truths -- and the numbers stay
-- countable on their own.
ALTER TABLE personal_trainings
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    -- Tasks only, and optional there too: "drink 3 litres" carries its number in the title and
    -- needs no column. When a number IS set it is a number rather than words inside the heading --
    -- "max 2200 kcal" typed into a title can never be counted or read next to the weight trend.
    ADD COLUMN target_calories INTEGER;

ALTER TABLE personal_trainings
    ADD CONSTRAINT chk_personal_trainings_kind
        CHECK (kind IN ('TRAINING', 'TASK')),
    -- The bounds catch a slipped digit the same way the weight range does; 500 is below any sane
    -- daily limit and 10000 above any sane one.
    ADD CONSTRAINT chk_personal_trainings_calories
        CHECK (target_calories IS NULL
               OR (kind = 'TASK' AND target_calories BETWEEN 500 AND 10000)),
    -- Perceived effort belongs to a session. "How hard was staying under 2200 kcal, 1-10" is a
    -- question about nothing, and an answer would quietly poison the RPE averages, which read every
    -- rated entry there is.
    ADD CONSTRAINT chk_personal_trainings_rpe_is_training
        CHECK (rpe IS NULL OR kind = 'TRAINING'),
    -- A commitment held across a whole day has no hour: "the calorie ceiling at 17:00" means
    -- nothing. Keeping tasks untimed also keeps them off the week view's hour grid, where they
    -- would sit at a position that claims something the entry does not have. Trainings keep the
    -- V72 rule (both times set or both null) -- that one stays in the service, this is stricter.
    ADD CONSTRAINT chk_personal_trainings_task_untimed
        CHECK (kind <> 'TASK' OR (start_time IS NULL AND end_time IS NULL));

-- Every calendar read filters by athlete + date already; tasks are counted separately in the stats,
-- so the kind travels with that access path rather than getting an index of its own.
COMMENT ON COLUMN personal_trainings.kind IS
    'TRAINING or TASK. Fixed at creation -- no endpoint changes it, because flipping a completed '
    'training into a task would have to throw its RPE away to satisfy the CHECK above.';
