INSERT INTO user_settings (user_id, sync_enabled, notify_comments, notify_recommendations, notify_new_publications)
SELECT u.id, NULL, TRUE, TRUE, FALSE
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.id)
