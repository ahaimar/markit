-- Deduplicate legacy payloads that reused business ids (order/user id) in event_id.
-- Keep the most recently created row per event_id; portable across PostgreSQL and H2.
DELETE FROM notifications
WHERE event_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM notifications n2
    WHERE n2.event_id = notifications.event_id
      AND (n2.created_at > notifications.created_at
          OR (n2.created_at = notifications.created_at AND n2.id > notifications.id))
  );

CREATE UNIQUE INDEX idx_notifications_event_unique ON notifications (event_id);