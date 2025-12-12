package org.torrents.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.torrents.client.view.ClientView;

import java.io.File;


public class ClientApp extends Application {
    private static final String DEFAULT_DOWNLOAD_DIR = "downloads";

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Выбор папки загрузки при старте
        String downloadDir = selectDownloadDirectory(primaryStage);
        if (downloadDir == null) {
            // Пользователь отменил выбор, закрываем приложение
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/torrent-client.fxml"));

        Parent root = loader.load();

        // Получаем контроллер после загрузки FXML
        ClientView view = loader.getController();
        view.setDownloadDirectory(downloadDir);

        primaryStage.setTitle("Torrent Client");
        primaryStage.setScene(new Scene(root, 950, 650));
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        // Иконка
        Image icon = new Image(getClass().getResourceAsStream("/app_icon_128_128.png"));
        primaryStage.getIcons().add(icon);
        primaryStage.show();
        // Обработка закрытия окна
        primaryStage.setOnCloseRequest(event -> {
            view.getViewModel().disconnect();
        });
    }

    /**
     * Выбор папки для загрузки файлов
     */
    private String selectDownloadDirectory(Stage owner) {
        // Показываем диалог выбора
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Выбор папки загрузки");
        alert.setHeaderText("Выберите папку для загрузки файлов");
        alert.setContentText("Использовать папку по умолчанию '" + DEFAULT_DOWNLOAD_DIR + "'?");

        ButtonType useDefaultButton = new ButtonType("По умолчанию");
        ButtonType selectButton = new ButtonType("Выбрать папку");
        ButtonType cancelButton = ButtonType.CANCEL;

        alert.getButtonTypes().setAll(useDefaultButton, selectButton, cancelButton);

        ButtonType result = alert.showAndWait().orElse(cancelButton);

        if (result == useDefaultButton) {
            return DEFAULT_DOWNLOAD_DIR;
        } else if (result == selectButton) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Выберите папку для загрузки файлов");


            File userHome = new File(System.getProperty("user.home"));
            if (userHome.exists()) {
                directoryChooser.setInitialDirectory(userHome);
            }

            File selectedDirectory = directoryChooser.showDialog(owner);
            if (selectedDirectory != null) {
                return selectedDirectory.getAbsolutePath();
            }
        }

        // Пользователь отменил выбор
        return null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

