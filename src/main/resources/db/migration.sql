CREATE TABLE IF NOT EXISTS files (
                                     id TEXT PRIMARY KEY,
                                     name TEXT NOT NULL,
                                     size INTEGER NOT NULL,
                                     parts_count INTEGER NOT NULL,
                                     file_hash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS file_parts (
                                          id TEXT PRIMARY KEY,
                                          file_id TEXT NOT NULL,
                                          part_index INTEGER NOT NULL,
                                          checksum TEXT NOT NULL,
                                          FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS peers (
                                     id TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS file_peers (
                                          file_part_id TEXT NOT NULL,
                                          peer_id TEXT NOT NULL,
                                          PRIMARY KEY (file_part_id, peer_id),
    FOREIGN KEY (file_part_id) REFERENCES file_parts(id) ON DELETE CASCADE,
    FOREIGN KEY (peer_id) REFERENCES peers(id) ON DELETE CASCADE
    );
