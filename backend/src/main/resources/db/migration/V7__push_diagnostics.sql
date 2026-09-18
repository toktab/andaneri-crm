-- Why a device is not getting reminders: what the push service last answered, and when it was last tried.
-- Without this a failure is invisible - the phone simply stays quiet.
ALTER TABLE push_subscriptions ADD COLUMN last_status INT;
ALTER TABLE push_subscriptions ADD COLUMN last_error VARCHAR(200);
ALTER TABLE push_subscriptions ADD COLUMN last_tried_at DATETIME(6);
