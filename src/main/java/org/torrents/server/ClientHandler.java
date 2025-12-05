package org.torrents.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.torrents.shared.Message;
import org.torrents.shared.ProtocolUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

@AllArgsConstructor
@Getter
public class ClientHandler implements Runnable {
    private PrintWriter out;
    private InputStream in;
    private Server server;

    @Override
    public void run() {
        try {
            while (true) {
                Message message = ProtocolUtil.receiveMessage(in);
                switch (message.getType()) {
                    case REGISTER:

                        break;
                    case FILE_LIST:

                        break;
                    case REQUEST_FILE:

                        break;
                    case COMPLETE:

                        break;

                    case SEND_CHUNK:

                        break;
                    case ERROR:

                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
