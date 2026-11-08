package com.tibo.eagentdroid;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 * 极简 CDP 客户端：连 WebView 自带的 DevTools 抽象 socket，跑一条 Runtime.evaluate。
 * 仅用于验证「安卓也能走调试通道」，不影响现有逻辑。
 */
public final class Cdp {

    private Cdp() {
    }

    /** 返回一段可读的探测结果，直接丢到状态行。 */
    /** 候选 socket 名：先按 pid，再扫 /proc/net/unix，最后试常见名。 */
    private static java.util.List<String> candidates() {
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
        set.add("chrome_devtools_remote");
        return new java.util.ArrayList<>(set);
    }

    public static String selfTest() {
        java.util.List<String> names = candidates();
        String name = names.get(0);
        Exception last = null;
        for (String n : names) {
            String r = tryOne(n);
            if (r.startsWith("CDP: 可用")) return "[" + n + "] " + r;
            last = new Exception(r.replace("\n", " / "));
        }
        return "CDP: 全部候选失败（候选: " + names + "）\n最后: "
                + (last == null ? "无" : last.getMessage());
    }

    private static String tryOne(String name) {
        try {
            String page = discoverPage(name);
            if (page == null) return "CDP: /json/list 里没有调试页（WebView 可能未开启调试）";
            LocalSocket ws = new LocalSocket();
            ws.connect(new LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT));
            ws.setSoTimeout(4000);
            InputStream in = ws.getInputStream();
            OutputStream out = ws.getOutputStream();
            if (!handshake(out, in, page)) return "CDP: WebSocket 握手失败";
            sendFrame(out, "{\"id\":1,\"method\":\"Runtime.evaluate\","
                    + "\"params\":{\"expression\":\"1+1\",\"returnByValue\":true}}");
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) {
                String msg = readFrame(in);
                if (msg == null) break;
                if (msg.contains("\"id\":1")) {
                    ws.close();
                    boolean ok = msg.contains("\"value\":2");
                    return (ok ? "CDP: 可用 ✔（Runtime.evaluate 返回 2）" : "CDP: 已连上，返回异常")
                            + "\n" + clip(msg);
                }
            }
            ws.close();
            return "CDP: 握手成功但没收到应答";
        } catch (Exception e) {
            return "CDP: 失败 " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "（socket: " + name + "）";
        }
    }

    private static String clip(String s) {
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static String discoverPage(String name) throws Exception {
        LocalSocket s = new LocalSocket();
        s.connect(new LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT));
        s.setSoTimeout(3000);
        OutputStream out = s.getOutputStream();
        out.write(("GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .getBytes("UTF-8"));
        out.flush();
        InputStream in = s.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, "UTF-8"));
            if (sb.length() > 60000) break;
        }
        s.close();
        String resp = sb.toString();
        int body = resp.indexOf("[");
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
