-- The coach's template library learns what kind of entry it produces.
--
-- V77 gave the plan a second kind of row (TASK), but the library could only make trainings -- so the
-- one thing that repeats verbatim across every athlete ("stay under 2200 kcal", "3 litres of water")
-- was the one thing that had to be retyped for each of them.
--
-- Unlike personal_trainings.kind, this one IS editable after creation. The reason that column is
-- frozen is that flipping a completed training would have to throw its RPE away; a template holds
-- no completion, no rating and no history, so there is nothing to lose by changing its mind.
ALTER TABLE training_templates
    ADD COLUMN kind            VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    ADD COLUMN target_calories INTEGER;

-- A task has no duration to offer: a commitment held across a whole day has no hour to fill, and
-- the training it creates is untimed by V77's CHECK anyway. Every existing row is a training and
-- keeps the value it already has.
ALTER TABLE training_templates
    ALTER COLUMN default_duration_minutes DROP NOT NULL;

ALTER TABLE training_templates
    ADD CONSTRAINT chk_training_templates_kind
        CHECK (kind IN ('TRAINING', 'TASK')),
    -- Same bounds and the same reason as personal_trainings: optional even for a task ("drink 3
    -- litres" carries its number in the title), and 500..10000 catches a slipped digit. The range
    -- is repeated rather than shared because a template is not a training -- it only makes one.
    ADD CONSTRAINT chk_training_templates_calories
        CHECK (target_calories IS NULL
               OR (kind = 'TASK' AND target_calories BETWEEN 500 AND 10000)),
    -- Exactly one of two shapes, so a task template can never quietly carry a duration that the
    -- form would then have nowhere to show. The 15..720 bound stays on V70's column CHECK, which
    -- passes for NULL.
    ADD CONSTRAINT chk_training_templates_duration
        CHECK ((kind = 'TRAINING' AND default_duration_minutes IS NOT NULL)
            OR (kind = 'TASK' AND default_duration_minutes IS NULL));

COMMENT ON COLUMN training_templates.kind IS
    'TRAINING or TASK -- what applying this template creates. Editable, unlike '
    'personal_trainings.kind: a template has no completion or rating to lose.';
