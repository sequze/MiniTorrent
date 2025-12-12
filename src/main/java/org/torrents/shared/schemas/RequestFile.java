package org.torrents.shared.schemas;

import java.util.List;

public record RequestFile(String fileId, List<Integer> partsNeeded, String requestId) {}