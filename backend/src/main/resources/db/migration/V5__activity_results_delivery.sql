-- What happened on a call or visit can now be several things at once ("we left samples" and "they want
-- others too"), with room for a note in the team's own words; deliveries name the flavors to bring; and
-- tasks guessed from the old spreadsheet are marked, so they can be cleared out of the call list at once.

ALTER TABLE activities ADD COLUMN result_note VARCHAR(200);

-- The first result stays in activities.result (reports count on it); the rest live here.
CREATE TABLE activity_results (
    activity_id BIGINT      NOT NULL,
    result      VARCHAR(30) NOT NULL,
    PRIMARY KEY (activity_id, result),
    CONSTRAINT fk_activity_result FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE
);

-- Which flavors to take to a delivery or a tasting.
CREATE TABLE task_flavors (
    task_id   BIGINT NOT NULL,
    flavor_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, flavor_id),
    CONSTRAINT fk_task_flavor_task   FOREIGN KEY (task_id)   REFERENCES tasks (id)   ON DELETE CASCADE,
    CONSTRAINT fk_task_flavor_flavor FOREIGN KEY (flavor_id) REFERENCES flavors (id) ON DELETE CASCADE
);

ALTER TABLE tasks ADD COLUMN imported BOOLEAN NOT NULL DEFAULT FALSE;

-- Tasks made on the same day as an imported call for the same business came out of the spreadsheet's
-- "next step" column: they are guesses, not something the team planned.
UPDATE tasks t SET imported = TRUE
 WHERE EXISTS (SELECT 1 FROM activities a
                WHERE a.business_id = t.business_id AND a.imported = TRUE
                  AND CAST(a.created_at AS DATE) = CAST(t.created_at AS DATE));
