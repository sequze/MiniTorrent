package org.torrents.server.db;

import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.FilePart;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Repository {

    public void registerPeer(String peerId, List<FileInfo> file) {
        String registerPeerSql = "INSERT INTO peers (id) VALUES (?);";
        String insertPeerFilePartSql = """
                INSERT INTO file_peers (file_part_id, peer_id)
                VALUES (?, ?);""";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement registerPeerStmt = c.prepareStatement(registerPeerSql);
             PreparedStatement insertPeerFilePartStmt = c.prepareStatement(insertPeerFilePartSql)
        ) {

            // создаём пир в бд

            registerPeerStmt.setString(1, peerId);
            registerPeerStmt.executeUpdate();

            // добавляем информацию о частях файлов, которые есть у пира
            for (FileInfo f : file) {
                // создаём файл, если его нет
                FileInfo exists = getFile(f.fileId());
                if (exists == null) {
                    createIfNotExistsFile(f);
                }
                for (Integer partIndex : f.parts()) {
                    String filePartId = f.fileId() + "_" + partIndex;

                    insertPeerFilePartStmt.setString(1, filePartId);
                    insertPeerFilePartStmt.setString(2, peerId);
                    insertPeerFilePartStmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to register peer in database", e);
        }

    }

    private void executeCreateFileStmt(PreparedStatement createFileStmt, FileInfo f) throws SQLException {
        createFileStmt.setString(1, f.fileId());
        createFileStmt.setString(2, f.filename());
        createFileStmt.setLong(3, f.size());
        createFileStmt.setInt(4, f.partsCount());
        createFileStmt.setString(5, f.fileHash());
        createFileStmt.executeUpdate();
    }

    public void createIfNotExistsFile(FileInfo file) {
        String createFileSql = """
                INSERT OR IGNORE INTO files
                (id, name, size, parts_count, file_hash)
                VALUES (?, ?, ?, ?, ?);""";
        String createPartSql = """
                INSERT OR IGNORE INTO file_parts
                (id, file_id, part_index, checksum)
                VALUES (?, ?, ?, ?);""";

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement createFileStmt = c.prepareStatement(createFileSql);
             PreparedStatement createPartStmt = c.prepareStatement(createPartSql)
        ) {
            executeCreateFileStmt(createFileStmt, file);
            for (int partIndex = 0; partIndex < file.partsCount(); partIndex++) {
                String filePartId = file.fileId() + "_" + partIndex;
                createPartStmt.setString(1, filePartId);
                createPartStmt.setString(2, file.fileId());
                createPartStmt.setInt(3, partIndex);
                // TODO: вычислять контрольную сумму части файла
                createPartStmt.setString(4, ""); // Пока пустой контрольный суммой
                createPartStmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create file in database", e);
        }
    }


    public List<FileInfo> getFiles() {
        String sql = """
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
                ORDER BY f.id;""";

        List<FileInfo> files = new ArrayList<>();

        // выполняем запрос
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement stmt = c.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String fileId = rs.getString("id");
                String filename = rs.getString("name");
                long size = rs.getLong("size");
                int partsCount = rs.getInt("parts_count");
                String fileHash = rs.getString("file_hash");
                String availablePartsStr = rs.getString("available_parts");
                List<Integer> availableParts = parseAvailableParts(availablePartsStr);


                FileInfo fileInfo = new FileInfo(fileId, size, partsCount, availableParts, fileHash, filename);

                files.add(fileInfo);
            }

            return files;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get files from database", e);
        }
    }


    // Добавлен метод getFile по аналогии с getFiles(), но для одного fileId
    public FileInfo getFile(String fileId) {
        String sql = """
                SELECT
                    f.*,
                    GROUP_CONCAT(DISTINCT fp.part_index ORDER BY fp.part_index) AS available_parts
                FROM files f
                         LEFT JOIN file_parts fp ON fp.file_id = f.id
                         LEFT JOIN file_peers ON fp.id = file_peers.file_part_id
                WHERE f.id = ?
                GROUP BY f.id;""";

        try (Connection c = DatabaseManager.getConnection(); PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String filename = rs.getString("name");
                    long size = rs.getLong("size");
                    int partsCount = rs.getInt("parts_count");
                    String fileHash = rs.getString("file_hash");
                    String availablePartsStr = rs.getString("available_parts");
                    List<Integer> availableParts = parseAvailableParts(availablePartsStr);

                    return new FileInfo(id, size, partsCount, availableParts, fileHash, filename);
                }
            }

            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get file from database", e);
        }
    }


    // Добавляем метод getFilePartWithPeers
    public FilePart getFilePartWithPeers(String fileId, int partIndex) {
        String filePartId = fileId + "_" + partIndex;

        String sql = """
                SELECT
                    fp.id as id,
                    fp.file_id as file_id,
                    fp.part_index as part_index,
                    fp.checksum as checksum,
                    GROUP_CONCAT(DISTINCT fp2.peer_id) AS peers
                FROM file_parts fp
                         LEFT JOIN file_peers fp2 ON fp.id = fp2.file_part_id
                WHERE fp.id = ?
                GROUP BY fp.id;""";

        try (Connection c = DatabaseManager.getConnection(); PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, filePartId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String fid = rs.getString("file_id");
                    int idx = rs.getInt("part_index");
                    String checksum = rs.getString("checksum");
                    String peersStr = rs.getString("peers");
                    List<String> peers = parsePeers(peersStr);

                    return new FilePart(id, fid, idx, checksum, peers);
                }
            }

            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get file part with peers from database", e);
        }
    }


    private static List<Integer> parseAvailableParts(String availablePartsStr) {
        // Парсим доступные части из строки "0,1,2,3" в List<Integer>
        List<Integer> availableParts = new ArrayList<>();
        if (availablePartsStr != null && !availablePartsStr.isEmpty()) {
            String[] partsArray = availablePartsStr.split(",");
            for (String part : partsArray) {
                availableParts.add(Integer.parseInt(part.trim()));
            }
        }
        return availableParts;
    }

    private static List<String> parsePeers(String peersStr) {
        List<String> peers = new ArrayList<>();
        if (peersStr != null && !peersStr.isEmpty()) {
            String[] parts = peersStr.split(",");
            for (String p : parts) peers.add(p.trim());
        }
        return peers;
    }
}
