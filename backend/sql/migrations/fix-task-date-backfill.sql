-- Backfill task_date for existing cook_task records where task_date is NULL
-- For periodic plans (with start_date/end_date): set task_date = start_date
-- For single-day plans: set task_date = plan_date

UPDATE cook_task ct
JOIN recipe_plan rp ON rp.id = ct.plan_id
SET ct.task_date = COALESCE(rp.start_date, rp.plan_date)
WHERE ct.task_date IS NULL AND ct.deleted = 0;