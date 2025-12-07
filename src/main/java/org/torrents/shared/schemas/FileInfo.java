package org.torrents.shared.schemas;

import java.util.List;

public record FileInfo(
        String fileId,
        long size,
        int partsCount,
        List<Integer> parts,
        String fileHash,
        String filename
) {
}
