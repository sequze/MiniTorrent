package org.torrents.client.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.torrents.client.viewmodel.FileInfoViewModel;
import org.torrents.client.viewmodel.ClientViewModel;

import java.io.File;

/**
 * View - контроллер для GUI с паттерном MVVM
 * Отвечает только за отображение и взаимодействие с пользователем
 */
public class ClientView {
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Button addFileButton;
    @FXML private Button downloadButton;
    @FXML private Label statusLabel;

    @FXML private TableView<FileInfoViewModel> filesTable;
    @FXML private TableColumn<FileInfoViewModel, String> filenameColumn;
    @FXML private TableColumn<FileInfoViewModel, String> sizeColumn;
    @FXML private TableColumn<FileInfoViewModel, Double> progressColumn;
    @FXML private TableColumn<FileInfoViewModel, String> statusColumn;

    private ClientViewModel viewModel;
    private Stage stage;
    private String downloadDirectory;

    @FXML
    public void initialize() {
        // Настраиваем значения по умолчанию для полей подключения
        serverHostField.setText("localhost");
        serverPortField.setText("6969");
    }

    /**
     * Установить папку загрузки и инициализировать ViewModel
     */
    public void setDownloadDirectory(String downloadDirectory) {
        this.downloadDirectory = downloadDirectory;

        // Создаём ViewModel с выбранной папкой
        viewModel = new ClientViewModel(downloadDirectory);

        // Настраиваем таблицу файлов
        filenameColumn.setCellValueFactory(cellData -> cellData.getValue().filenameProperty());

        sizeColumn.setCellValueFactory(cellData -> {
            return new javafx.beans.property.SimpleStringProperty(
                    cellData.getValue().getSizeFormatted()
            );
        });

        progressColumn.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());

        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

        // Связываем таблицу с данными из ViewModel
        filesTable.setItems(viewModel.getAvailableFiles());

        // Связываем статус подключения с UI
        statusLabel.textProperty().bind(viewModel.connectionStatusProperty());

        // Связываем состояние кнопок с состоянием подключения
        connectButton.disableProperty().bind(viewModel.connectedProperty());
        disconnectButton.disableProperty().bind(viewModel.connectedProperty().not());
        addFileButton.disableProperty().bind(viewModel.connectedProperty().not());
        downloadButton.disableProperty().bind(viewModel.connectedProperty().not());
        serverHostField.disableProperty().bind(viewModel.connectedProperty());
        serverPortField.disableProperty().bind(viewModel.connectedProperty());


        // Настраиваем слушателей для уведомлений
        viewModel.setErrorListener(this::showError);
        viewModel.setInfoListener(this::showInfo);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleConnect() {
        String host = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            showError("Ошибка подключения", "Пожалуйста, введите хост и порт сервера");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showError("Ошибка подключения", "Неверный формат порта");
            return;
        }

        viewModel.connect(host, port);
    }

    @FXML
    private void handleDisconnect() {
        viewModel.disconnect();
    }

    @FXML
    private void handleAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл для раздачи");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            viewModel.addLocalFile(file.toPath());
        }
    }

    @FXML
    private void handleDownload() {
        FileInfoViewModel selectedFile = filesTable.getSelectionModel().getSelectedItem();

        if (selectedFile == null) {
            showError("Ошибка загрузки", "Пожалуйста, выберите файл для загрузки");
            return;
        }

        if (selectedFile.isLocal()) {
            showInfo("Информация", "Файл уже загружен");
            return;
        }

        viewModel.downloadFile(selectedFile);
    }

    /**
     * Показать диалог с ошибкой
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Показать диалог с информацией
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Получить ViewModel (для тестирования)
     */
    public ClientViewModel getViewModel() {
        return viewModel;
    }
}

