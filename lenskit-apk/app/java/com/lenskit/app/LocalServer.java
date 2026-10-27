package com.lenskit.app;

import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 极简本地 HTTP 服务：只服务于 127.0.0.1，把 assets/index.html 吐给 WebView。
 * http://127.0.0.1 在 Chromium 内核里是安全上下文，getUserMedia（摄像头）才能用。
 */
public class LocalServer {

    private final ServerSocket serverSocket;
    private final byte[] htmlBytes;

    public LocalServer(AssetManager am) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        InputStream is = am.open("index.html");
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        is.close();
        htmlBytes = bos.toByteArray();
        serverSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket sk = serverSocket.accept();
                handle(sk);
            } catch (Exception e) {
                return;
            }
        }
    }

    private void handle(Socket sk) {
        try {
            sk.setSoTimeout(3000);
            InputStream in = sk.getInputStream();
            OutputStream out = sk.getOutputStream();

            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) >= 0) {
                head.write(c);
                byte[] b = head.toByteArray();
                int len = b.length;
                if (len >= 4 && b[len - 4] == '\r' && b[len - 3] == '\n'
                        && b[len - 2] == '\r' && b[len - 1] == '\n') {
                    break;
                }
                if (len > 16384) {
                    break;
                }
            }
            String request = head.toString("ISO-8859-1");
            String path = "/";
            String first = request.isEmpty() ? "" : request.split("\r\n")[0];
            String[] parts = first.split(" ");
            if (parts.length >= 2) {
                path = parts[1];
            }
            boolean ok = path.equals("/") || path.equals("/index.html") || path.startsWith("/?");
            byte[] body = ok ? htmlBytes : "Not Found".getBytes(StandardCharsets.UTF_8);
            String status = ok ? "200 OK" : "404 Not Found";
            String type = ok ? "text/html; charset=utf-8" : "text/plain; charset=utf-8";
            String resp = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: " + type + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (Exception e) {
            // 忽略单次请求错误
        } finally {
            try {
                sk.close();
            } catch (Exception e2) {
                // 忽略
            }
        }
    }
}
