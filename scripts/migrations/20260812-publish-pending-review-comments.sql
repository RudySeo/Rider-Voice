START TRANSACTION;

UPDATE reviews
SET comment_moderation_status = CASE
    WHEN comment IS NULL THEN 'NONE'
    ELSE 'PUBLISHED'
END
WHERE comment_moderation_status = 'PENDING';

COMMIT;

SELECT COUNT(*) AS remaining_pending_comments
FROM reviews
WHERE comment_moderation_status = 'PENDING';
