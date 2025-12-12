package org.torrents.server.db;

import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.FilePart;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RepositoryImpl implements Repository {

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
        createFileStmt.executeUpdate();
    }

    public void createIfNotExistsFile(FileInfo file) {
        String createFileSql = """
                INSERT OR IGNORE INTO files
                (id, name, size, parts_count)
                VALUES (?, ?, ?, ?);""";
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
                // Сохраняем checksum части из FileInfo
                String checksum = file.partChecksums() != null ? file.partChecksums().getOrDefault(partIndex, "") : "";
                createPartStmt.setString(4, checksum);
                createPartStmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create file in database", e);
        }
    }

    /**
     * Добавить новый файл для существующего пира
     */
    public void addFileForPeer(String peerId, FileInfo file) {
        // Создаём файл в БД, если его нет
        createIfNotExistsFile(file);

        // Связываем все части файла с этим пиром
        String insertPeerFilePartSql = """
                INSERT OR IGNORE INTO file_peers (file_part_id, peer_id)
                VALUES (?, ?);""";

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement insertPeerFilePartStmt = c.prepareStatement(insertPeerFilePartSql)
        ) {
            for (Integer partIndex : file.parts()) {
                String filePartId = file.fileId() + "_" + partIndex;

                insertPeerFilePartStmt.setString(1, filePartId);
                insertPeerFilePartStmt.setString(2, peerId);
                insertPeerFilePartStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add file for peer in database", e);
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

        try (Connection c = DatabaseManager.getConnection()) {
            // Загружаем все checksums одним запросом
            java.util.Map<String, java.util.Map<Integer, String>> allChecksums = getAllPartChecksums(c);

            // Выполняем основной запрос для файлов
            try (PreparedStatement stmt = c.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fileId = rs.getString("id");
                    String filename = rs.getString("name");
                    long size = rs.getLong("size");
                    int partsCount = rs.getInt("parts_count");
                    String availablePartsStr = rs.getString("available_parts");
                    List<Integer> availableParts = parseAvailableParts(availablePartsStr);

                    // Получаем checksums из предзагруженной map
                    java.util.Map<Integer, String> partChecksums = allChecksums.getOrDefault(fileId, new java.util.HashMap<>());

                    FileInfo fileInfo = new FileInfo(fileId, size, partsCount, availableParts, partChecksums, filename);
                    files.add(fileInfo);
                }
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
                    String availablePartsStr = rs.getString("available_parts");
                    List<Integer> availableParts = parseAvailableParts(availablePartsStr);

                    // Загружаем checksums частей из БД
                    java.util.Map<Integer, String> partChecksums = getPartChecksums(c, fileId);

                    return new FileInfo(id, size, partsCount, availableParts, partChecksums, filename);
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

    /**
     * Загрузить checksums всех частей файла из БД
     */
    private java.util.Map<Integer, String> getPartChecksums(Connection c, String fileId) throws SQLException {
        String sql = "SELECT part_index, checksum FROM file_parts WHERE file_id = ? ORDER BY part_index";
        java.util.Map<Integer, String> checksums = new java.util.HashMap<>();

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int partIndex = rs.getInt("part_index");
                    String checksum = rs.getString("checksum");
                    if (checksum != null && !checksum.isEmpty()) {
                        checksums.put(partIndex, checksum);
                    }
                }
            }
        }

        return checksums;
    }

    /**
     * Загрузить все checksums для всех файлов одним запросом (оптимизация N+1)
     */
    private java.util.Map<String, java.util.Map<Integer, String>> getAllPartChecksums(Connection c) throws SQLException {
        String sql = "SELECT file_id, part_index, checksum FROM file_parts ORDER BY file_id, part_index";
        java.util.Map<String, java.util.Map<Integer, String>> result = new java.util.HashMap<>();

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String fileId = rs.getString("file_id");
                int partIndex = rs.getInt("part_index");
                String checksum = rs.getString("checksum");

                if (checksum != null && !checksum.isEmpty()) {
                    result.computeIfAbsent(fileId, k -> new java.util.HashMap<>())
                          .put(partIndex, checksum);
                }
            }
        }

        return result;
    }
}
