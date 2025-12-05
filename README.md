# Mini-Torrent

**Mini-Torrent** — сетевое JavaFX-приложение, имитирующее базовый функционал торрент-трекера.

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
            "partsCount": 3,
            "fileHash": "sha256..."
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
            "fileHash": "sha256...",
            "availableParts": [0,1,2]
          }
        ]
      }
    }
    ```

3. **REQUEST_FILE** - клиент → сервер: запрос файла fileId

- payload:
    ```json
    {
    "type": "REQUEST_FILE",
    "payload": {
      "fileId": "f1"
      }
    }
    ```

4. **SEND_CHUNK** - сервер -> клиент: сервер передаёт клиенту часть файла
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

5. **COMPLETE** - клиент → сервер: уведомление о полной загрузке файла
- payload:
    ```json
    {
      "type": "COMPLETE",
      "payload": {
        "fileId": "f1"
      }
    }
    ```
6. **ERROR** - сообщение об ошибке
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