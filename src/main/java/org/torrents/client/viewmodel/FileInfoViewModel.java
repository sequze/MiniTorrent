package org.torrents.client.viewmodel;

import javafx.beans.property.*;
import lombok.Getter;
import org.torrents.shared.schemas.FileInfo;

/**
 * ViewModel для отдельного файла
 */
public class FileInfoViewModel {
    @Getter
    private final FileInfo fileInfo;

    private final StringProperty filename = new SimpleStringProperty();
    private final LongProperty size = new SimpleLongProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty status = new SimpleStringProperty("Доступен");
    private final BooleanProperty isLocal = new SimpleBooleanProperty(false);

    public FileInfoViewModel(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
        this.filename.set(fileInfo.filename());
        this.size.set(fileInfo.size());
    }

    // Getters для свойств
    public StringProperty filenameProperty() {
        return filename;
    }

    public String getFilename() {
        return filename.get();
    }

    public LongProperty sizeProperty() {
        return size;
    }

    public long getSize() {
        return size.get();
    }

    public String getSizeFormatted() {
        long bytes = size.get();
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public double getProgress() {
        return progress.get();
    }

    public void setProgress(double progress) {
        this.progress.set(progress);
    }

    public StringProperty statusProperty() {
        return status;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public BooleanProperty isLocalProperty() {
        return isLocal;
    }

    public boolean isLocal() {
        return isLocal.get();
    }

    public void setIsLocal(boolean isLocal) {
        this.isLocal.set(isLocal);
        if (isLocal) {
            this.status.set("Локальный");
        }
    }

}

