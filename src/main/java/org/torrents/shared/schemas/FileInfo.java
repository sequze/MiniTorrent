package org.torrents.shared.schemas;

import java.util.List;
import java.util.Map;

public record FileInfo(
        String fileId,
        long size,
        int partsCount,
        List<Integer> parts,
        Map<Integer, String> partChecksums,  // SHA-256 checksums for each part
        String filename
) {
}
