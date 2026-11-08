package com.tibo.eagentdroid;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

/**
 * 极简 CDP 客户端。两条通道：
 *   ① 直连抽象 socket webview_devtools_remote_<pid>（多数 ROM 被 SELinux 拦）
 *   ② 走 root 中转：127.0.0.1:9333 → socat → 抽象 socket
 * 只做 Runtime.evaluate 验证，不影响现有发送/上传逻辑。
 */
public final class Cdp {

    public static final int RELAY_PORT = 9333;

    private Cdp() {
    }

    private interface Link {
        void close();
    }

    public static String selfTest() {
        StringBuilder log = new StringBuilder();
        // ① unix 直连
        for (String n : unixNames()) {
            try {
                final LocalSocket s = new LocalSocket();
                s.connect(new LocalSocketAddress(n, LocalSocketAddress.Namespace.ABSTRACT));
                s.setSoTimeout(4000);
                String r = run(s.getInputStream(), s.getOutputStream(), new Link() {
                    @Override
                    public void close() {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                    }
                });
                if (r.startsWith("OK")) return "CDP 可用 ✔ [unix " + n + "] " + r.substring(2);
                log.append(n).append(": ").append(r).append("\n");
            } catch (Exception e) {
                log.append(n).append(": ").append(e.getClass().getSimpleName())
                        .append(" ").append(e.getMessage()).append("\n");
            }
        }
        // ② root 中转（App 自己 su 拉起 socat，不靠用户手动）
        String relayMsg = startRelay();
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", RELAY_PORT), 2500);
            s.setSoTimeout(4000);
            String r = run(s.getInputStream(), s.getOutputStream(), new Link() {
                @Override
                public void close() {
                }
            });
            if (r.startsWith("OK")) return "CDP 可用 ✔ [relay " + RELAY_PORT + "] " + r.substring(2);
            log.append("relay: ").append(r).append("\n");
        } catch (Exception e) {
            log.append("relay 127.0.0.1:").append(RELAY_PORT).append(": ")
                    .append(e.getClass().getSimpleName()).append(" ")
                    .append(e.getMessage()).append("\n");
        }
        String t = log.toString().trim();
        return "CDP 全部失败\n" + (t.length() > 600 ? t.substring(t.length() - 600) : t);
    }

    private static final String SOCAT = "/data/data/com.termux/files/usr/bin/socat";

    /** 用 root 后台拉起 socat 中转，返回状态说明。 */
    private static String startRelay() {
        String cmd = "P=$(ps -A | grep com.tibo.eagentdroid | grep -v grep | tr -s ' ' | cut -d' ' -f1 | head -1); "
                + "[ -z \"$P\" ] && P=" + android.os.Process.myPid() + "; "
                + "pkill -f 'TCP-LISTEN:" + RELAY_PORT + "' 2>/dev/null; "
                + "( " + SOCAT + " TCP-LISTEN:" + RELAY_PORT + ",fork,reuseaddr "
                + "ABSTRACT-CONNECT:webview_devtools_remote_$P >/dev/null 2>&1 & ) ; sleep 1; echo started $P";
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append(' ');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "relay 启动失败: " + e.getMessage();
        }
    }

    private static java.util.List<String> unixNames() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        set.add("webview_devtools_remote_" + android.os.Process.myPid());
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/net/unix")));
            String l;
            while ((l = br.readLine()) != null) {
                int at = l.indexOf('@');
                if (at < 0) continue;
                String nm = l.substring(at + 1).trim();
                if (nm.contains("devtools_remote")) set.add(nm);
            }
            br.close();
        } catch (Exception ignored) {
        }
        return new java.util.ArrayList<>(set);
    }

    /** 返回 "OK…" 或错误说明。 */
    private static String run(InputStream in, OutputStream out, Link link) {
        try {
            String page = discoverPage(in, out);
            if (page == null) return "没有调试页";
            if (!handshake(out, in, page)) return "WS 握手失败";
            sendFrame(out, "{\"id\":1,\"method\":\"Runtime.evaluate\","
                    + "\"params\":{\"expression\":\"1+1\",\"returnByValue\":true}}");
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) {
                String msg = readFrame(in);
                if (msg == null) break;
                if (msg.contains("\"id\":1")) {
                    return msg.contains("\"value\":2") ? "OK（1+1=2）" : "应答异常 " + clip(msg);
                }
            }
            return "无应答";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + " " + e.getMessage();
        } finally {
            link.close();
        }
    }

    private static String clip(String s) {
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static String discoverPage(InputStream in, OutputStream out) throws Exception {
        out.write(("GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .getBytes("UTF-8"));
        out.flush();
        // 先读完响应头（不能等 EOF：服务器可能保持连接不关）
        StringBuilder head = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            head.append((char) c);
            int n = head.length();
            if (n >= 4 && head.charAt(n - 4) == '\r' && head.charAt(n - 3) == '\n'
                    && head.charAt(n - 2) == '\r' && head.charAt(n - 1) == '\n') break;
            if (n > 16384) break;
        }
        String hs = head.toString();
        int clen = -1;
        for (String line : hs.split("\r\n")) {
            String low = line.toLowerCase();
            if (low.startsWith("content-length:")) {
                try {
                    clen = Integer.parseInt(line.substring(15).trim());
                } catch (Exception ignored) {
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (clen > 0) {
            byte[] buf = new byte[clen];
            int off = 0;
            while (off < clen) {
                int r = in.read(buf, off, clen - off);
                if (r < 0) break;
                off += r;
            }
            sb.append(new String(buf, 0, off, "UTF-8"));
        } else {
            byte[] buf = new byte[4096];
            int n;
            try {
                while ((n = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                    if (sb.length() > 60000) break;
                }
            } catch (Exception ignored) {
            }
        }
        String resp = sb.toString();
        int body = resp.indexOf('[');
        if (body < 0) return null;
        JSONArray arr = new JSONArray(resp.substring(body));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String u = o.optString("webSocketDebuggerUrl", "");
            if (u.startsWith("ws://")) {
                int p = u.indexOf("/devtools/");
                if (p >= 0) return u.substring(p);
            }
        }
        return null;
    }

    private static boolean handshake(OutputStream out, InputStream in, String path) throws Exception {
        byte[] key = new byte[16];
        new Random().nextBytes(key);
        String k = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP);
        String req = "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Key: " + k + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes("UTF-8"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            sb.append((char) c);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) break;
        }
        return sb.toString().contains("101");
    }

    private static void sendFrame(OutputStream out, String text) throws Exception {
        byte[] data = text.getBytes("UTF-8");
        int len = data.length;
        out.write(0x81);
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) out.write((int) (((long) len >> (8 * i)) & 0xFF));
        }
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        out.write(mask);
        for (int i = 0; i < len; i++) out.write(data[i] ^ mask[i % 4]);
        out.flush();
    }

    private static String readFrame(InputStream in) throws Exception {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read();
        if (b1 < 0) return null;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = (in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = new byte[4];
        if (masked) {
            for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
        }
        byte[] data = new byte[(int) len];
        int off = 0;
        while (off < len) {
            int r = in.read(data, off, (int) len - off);
            if (r < 0) return null;
            off += r;
        }
        if (masked) {
            for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
        }
        if (opcode == 0x8) return null;
        if (opcode != 0x1) return readFrame(in);
        return new String(data, "UTF-8");
    }
}
