<div align="center">
  <img src="src/main/resources/app_icon.png" width="400" height="400" />
</div>

# Mini-Torrent

Сетевое JavaFX-приложение, имитирующее базовый функционал торрент-трекера.

## Типы сообщений:

1. **REGISTER** — клиент сообщает серверу свои начальные файлы/части.

- payload:
    ```json
    {
      "type": "REGISTER",
      "payload": {
        "files": [
          {
            "fileId": "f1",
            "size": 12345,
            "parts": [0, 1, 2],
            "partsCount": 3
          }
        ]
      }
    }
    ```

2. **FILE_LIST** - сервер → клиент: список доступных файлов

- payload:
    ```json
    {
      "type": "FILE_LIST",
      "payload": {
        "files": [
          {
            "fileId": "f1",
            "size": 12345,
            "partsCount": 3,
            "parts": [0,1,2]
          }
        ]
      }
    }
    ```

3. **REQUEST_FILE** - клиент → сервер | сервер -> клиент: запрос файла fileId

- payload:
    ```json
    {
    "type": "REQUEST_FILE",
    "payload": {
      "fileId": "f1",
      "partsNeeded": [0, 1, 2],
      "requestId": "req123"  
      }
    }
    ```
    - если не указан partsNeeded, запрашивается весь файл
    - requestId нужен для идентификации запроса при получении частей файла
4. **ADD_FILE** - клиент -> сервер: добавление нового файла для раздачи
- payload:
    ```json
    {
      "type": "ADD_FILE",
      "payload": {
        "fileId": "f2",
        "size": 54321,
        "partsCount": 3,
        "parts": {
          "0": "checksum0",
          "1": "checksum1",
          "2": "checksum2"
         }
      }
    }
    ```
4. **SEND_CHUNK** - сервер -> клиент: сервер передаёт клиенту часть файла (так же используется клиентом для отправки части файла серверу)
- payload:
    ```json
    {
      "type": "SEND_CHUNK",
      "payload": {
        "fileId": "f1",
        "partIndex": 2,
        "length": 4096
      }
    }
    ```
  > - [4 байта] - длина JSON заголовка (int)
  > - [N байт] - JSON заголовок
  > - [4096 байт] - данные chunk

5. **ERROR** - сообщение об ошибке
- payload:
    ```json
    {
      "type": "ERROR",
      "payload": {
        "code": 404,
        "message": "File not found"
      }
    }
    ```

## Поток данных

1. **Регистрация клиента**

```
Client                           Server
  │                                │
  ├─── connect() ─────────────────►│
  │                                │
  ├─── REGISTER ──────────────────►│ Отправляет список файлов, 
  │    {files: [...]}              │ которые ранее раздавал или загружал
  │                                │
  │◄───── FILE_LIST ───────────────┤ Отправляет список доступных файлов
  │    {files: [...]}              │
  │                                │
```

2. **Загрузка файла**

```
Client A                  Server                  Client B
  │                         │                       │
  ├── REQUEST_FILE ────────►│                       ├─ Запрос файла
  │   {fileId, parts}       │                       │
  │                         │                       │
  │                         ├── REQUEST_FILE ──────►├─ Запрос файла/части у 
  │                         │   {fileId, parts}     │  другого клиента
  │                         │                       │
  │                         │◄──── SEND_CHUNK ──────├─ Клиент отдаёт файл/чанк
  │                         │    + binary data      │
  │                         │                       │
  │◄──── SEND_CHUNK ────────┤                       ├─ Клиент получает файл/чанк 
  │    + binary data        │                       │
  │                         │                       │
  │                         │                       │
```

3. **Добавление файла**

```
Client                           Server
  │                                │
  ├────────  ADD_FILE  ───────────►│
  │  • read file                   │
  │  • calculate hash              │
  │  • split into chunks           │
  │                                │
  │                                │
  │◄───── FILE_LIST ───────────────├─ отдаёт обновлённый FILE_LIST
  │    {files: [..., new file]}    │  всем клиентам
```

## Формат сообщения

```
┌──────────────────────────────────┐
│  4 bytes: message length (int)   │
├──────────────────────────────────┤
│  N bytes: JSON message           │
│  {                               │
│    "type": "REGISTER",           │
│    "payload": {...}              │
│  }                               │
└──────────────────────────────────┘
```

**Формат SEND_CHUNK**

```
┌──────────────────────────────────┐
│  4 bytes: JSON length (int)      │
├──────────────────────────────────┤
│  N bytes: JSON header            │
│  {                               │
│    "type": "SEND_CHUNK",         │
│    "payload": {                  │
│      "fileId": "...",            │
│      "partIndex": 0,             │
│      "length": 262144            │
│    }                             │
│  }                               │
├──────────────────────────────────┤
│  length bytes: binary chunk data │
└──────────────────────────────────┘
```

## Архитектура

```
src/main/java/org/torrents/
├── client/                          # Клиентская часть (JavaFX)
│   ├── ClientApp.java              # Главное приложение JavaFX
│   ├── ClientLauncher.java         # Точка входа для jar
│   ├── DownloadManager.java        # Управление загрузками файлов
│   ├── model/                      
│   │   └── TorrentModel.java       # Модель MVVM
│   ├── view/                       
│   │   └── ClientView.java         # FXML-контроллер UI
│   └── viewmodel/                  
│       ├── ClientViewModel.java    # Логика взаимодействия с сервером
│       └── FileInfoViewModel.java  # Модель представления файла
│
├── server/                          # Серверная часть
│   ├── Server.java                 # Главный класс сервера
│   ├── ServerMain.java             # Точка входа сервера
│   ├── ClientHandler.java          # Обработка клиентских подключений
│   ├── ClientListener.java         # Интерфейс слушателя событий
│   ├── db/                         
│   │   ├── DatabaseManager.java    # Управление БД (HikariCP)
│   │   └── FileRepository.java     # Репозиторий файлов и пиров
│   ├── handlers/                   # Обработчики сообщений
│   │   ...
│   └── service/                    
│       ├── PeerService.java        # Управление peer-соединениями
│       ├── BroadcastService.java   # Рассылка сообщений клиентам
│       └── FileTransferService.java # Передача файлов между клиентами
│
└── shared/                          # Общие классы
    ├── Message.java                # Класс сообщения
    ├── MessageType.java            # Типы сообщений (enum)
    ├── ProtocolUtil.java           # Работа с протоколом (чтение/запись)
    └── schemas/                    # DTO классы
        ├── AddFile.java
        ├── FileInfo.java
        ├── RegisterPayload.java
        ├── RequestFile.java
        └── SendChunk.java
```
## Паттерны и решения
1. MVVM для клиентской части с JavaFX.
2. Паттерн "Команда" для обработки сообщений клиента на сервере.
3. HikariCP для управления пулом соединений с базой данных(sqlite3).