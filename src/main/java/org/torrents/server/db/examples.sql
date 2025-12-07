-- Выбрать файлы, которые есть у пользователя
SELECT file_id, part_index
FROM file_parts f
    JOIN file_peers fp ON fp.file_part_id =f.id
WHERE fp.peer_id = 'peer1111-1111-1111-1111-111111111111';

-- Выбрать файлы с доступными частями
-- Не возвращает файлы без частей
WITH available_files AS (
    SELECT
        f.id AS file_id,
        GROUP_CONCAT(DISTINCT fp.part_index ORDER BY fp.part_index) AS available_parts
    FROM files f
    JOIN file_parts fp ON fp.file_id = f.id
    JOIN file_peers ON fp.id = file_peers.file_part_id
    GROUP BY f.id
)
SELECT
    f.*,
    af.available_parts
FROM files f
         JOIN available_files af ON f.id = af.file_id
ORDER BY f.id;

