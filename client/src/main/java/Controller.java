import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Controller {
    private String currentUser;

    @FXML
    public TextField loginField;

    @FXML
    public PasswordField passField;

    @FXML
    public HBox authPanel;

    @FXML
    public Button Auth;

    @FXML
    public ListView localList;

    @FXML
    public Button DeleteLocal;

    @FXML
    public Button RefreshLocal;

    @FXML
    public Button SendToCloud;

    @FXML
    public ListView cloudList;

    @FXML
    public Button DownloadFile;

    @FXML
    public Button DeleteRemote;

    @FXML
    public Button RefreshRemote;

    @FXML
    public ProgressBar operationProgress;

    private boolean authorized;
    private boolean closed;

    public Controller() {
    }

    private String getLocalFullPath(ListView lv){
        return Command.localAbsPath + "\\" + this.currentUser + "\\" + (String) lv.getSelectionModel().getSelectedItem();
    }

    private String getCloudFullPath(ListView lv){
        return Command.cloudAbsPath + "\\" + this.currentUser + "\\" + (String) lv.getSelectionModel().getSelectedItem();
    }

    private void loadFilesToList(Path p, ListView lv) {
        // Если нет каталога пользователя - создадим его

        if (!p.toFile().exists()) {
            if (!p.toFile().mkdir())
                try {
                throw new IOException("Не могу создать каталог пользователя");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Platform.runLater(() -> {
            lv.getItems().clear();
            lv.getItems().addAll(p.toFile().list());
        });
    }


    private void ctrl(boolean set){

        Platform.runLater(
            () -> {
                if (set) {
                    loginField.setText("");
                    passField.setText("");
                    Auth.setText("Выход");
                }
                else {
                    loginField.setText("");
                    passField.setText("");
                    Auth.setText("Авторизоваться");
                }
                loginField.setVisible(!set);
                passField.setVisible(!set);

                // локальные
                DeleteLocal.setDisable(!set);
                RefreshLocal.setDisable(!set);
                SendToCloud.setDisable(!set);
                localList.getItems().clear();
                localList.setDisable(!set);

                // облако
                DownloadFile.setDisable(!set);
                DeleteRemote.setDisable(!set);
                RefreshRemote.setDisable(!set);
                cloudList.getItems().clear();
                cloudList.setDisable(!set);

                // operationProgress.setProgress(0.0d);
        });
    }

    private void openConnection() {

        if (!Client.getInstance().isConnected()) {
            try {
                // Подключение к серверу
                if (!Client.getInstance().isConnected()) {
                    Client.getInstance().connect();
                }
                // Хэндлер сообщений сервера
                serverMessageHandler();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void serverMessageHandler() {
        Thread serverMessageHandler = new Thread(
            () -> {
                while (true) {

                    Object o = null;
                    try {
                        if (!Client.getInstance().isConnected()) {
                            Client.getInstance().connect();
                        }

                        o = Client.getInstance().readData();

                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (!authorized) {
                     // не авторизованы пробуем авторизоваться.
                        authMessage(o);
                    }
                    else {
                     // авторизованы
                        if (closed) {
                            // выходим из потока прослушивания
                            break;
                        }

                     // читаем и обрабатываем поток сообщений от сервера

                     parseMessage(o);

                    }
                }
            });

        serverMessageHandler.setDaemon(true);
        serverMessageHandler.start();
    }

    private void authMessage(Object o) {
        // Авторизация
        if (o instanceof Packet4Command) {
            String[] st = ((Packet4Command) o).getCommand().split(" ");
            authorized = st[0].equalsIgnoreCase(Command.MSG_AUTH_OK.trim());

            // отобразить панель контроллов
            ctrl(authorized);

            // Обновить визуальную часть формы
            if (authorized) {
                this.currentUser = st[1];
                RefreshLocalClick(null);
                RefreshRemoteClick(null);
            }
        }
    }

    private void parseMessage(Object o) {
        // Скачать файл
        if (o instanceof Packet2Data) {

            // 1. записать в каталог
            // 2. Отрисовать пользовательский интерфейс
            // ...

            Packet2Data packet = ((Packet2Data) o);

            String pathname =
                    Command.localAbsPath + "\\" + packet.getUsrOwner() + "\\" + packet.getFile().getName();

            File localFile = new File(pathname);
            // Пишем файл в каталог пользователя
            try {
                if (!localFile.exists()) {
                    localFile.createNewFile();
                }
                Files.write(Paths.get(pathname), ((Packet2Data) o).getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Список каталогов
        if (o instanceof Packet3Listfiledir) {
            // 1. Перестроить каталоги
            // 2. Обновить пользовательский интерфейс
        }
    }

    private void closeConnection() {
        Client.getInstance().close();

        authorized = false;
    }

    public void onAuthActionClick(ActionEvent actionEvent) throws ClassNotFoundException {
        if (Auth.getText().equalsIgnoreCase("Авторизоваться")) {

            // Подключение
            openConnection();

            // Авторизация
            Packet1Login textMessage = new Packet1Login(loginField.getText(), passField.getText());

            try {
                if (!Client.getInstance().isConnected()) {
                    Client.getInstance().connect();
                }

                Client.getInstance().sendData(textMessage);

            } catch (IOException e) {
                e.printStackTrace();
            }

            return;
        }

        if (Auth.getText().equalsIgnoreCase("Выход")) {

            // Отключаемся
            closeConnection();

            // скрываем контроллы
            ctrl(false);
        }
    }

    // ****************************************************
    //* Методы для управления локальным хранилищем *//
    // ****************************************************
    public void SendToCloudClick(ActionEvent actionEvent) {
        // Отправить файл в облако
        try {
            Packet2Data pd = new Packet2Data(this.currentUser, new File(getLocalFullPath(localList)));

            Client.getInstance().sendMsg(pd, operationProgress);

        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
          //  RefreshRemoteClick(actionEvent);
        }
    }

    public void DeleteLocalClick(ActionEvent actionEvent) {
        // Удалить файл локально
        try {
            // Удалить локально
            Files.delete(new File(getLocalFullPath(localList)).toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
           // RefreshLocalClick(actionEvent);
        }
    }

    public void RefreshLocalClick(ActionEvent actionEvent) {
        // Обновить локально
        loadFilesToList(Paths.get(Command.localAbsPath + "\\" + this.currentUser), localList);
    }

    // ****************************************************
    //* Методы для работы с файлами в облаке *//
    // ****************************************************
    public void DownloadFileClick(ActionEvent actionEvent) {
        // Скачать файл из облака на локальный диск
        try {
            Packet4Command getFile = new Packet4Command(this.currentUser, cloudList.getSelectionModel().getSelectedItem().toString());

            String pathname =
                    Command.localAbsPath + "\\" + this.currentUser + "\\" + cloudList.getSelectionModel().getSelectedItem().toString();

            File localFile = new File(pathname);
            localFile.delete();

            Client.getInstance().sendMsg(getFile, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            // RefreshRemoteClick(actionEvent);
        }
    }

    public void DeleteRemoteClick(ActionEvent actionEvent) {
        // Удалить из облака выбранный файл
        try {
            // Удалить локально
            Files.delete(new File(getCloudFullPath(cloudList)).toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            // RefreshRemoteClick(actionEvent);
        }
    }

    public void RefreshRemoteClick(ActionEvent actionEvent) {
        // Обновить список файлов в облаке
        loadFilesToList(Paths.get(Command.cloudAbsPath + "\\" + this.currentUser), cloudList);
    }
}