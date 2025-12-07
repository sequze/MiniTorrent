package org.torrents.shared.schemas;

import java.util.List;

public record FilePart(
        String id,
        String fileId,
        int partIndex,
        String checksum,
        List<String> peers
) {
}

