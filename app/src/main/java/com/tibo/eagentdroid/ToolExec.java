package com.tibo.eagentdroid;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** EAgent 工具执行器：shell / 截图 / 窗口置顶 / 鼠标(触屏) / 键盘 / 文件 / HTTP。 */
public final class ToolExec {

    private ToolExec() {
    }

    private static Term.Result r(int code, String out) {
        Term.Result x = new Term.Result();
        x.code = code;
        x.out = out == null ? "" : out;
        return x;
    }

    public static File filesDir(Context ctx) {
        File f = ctx.getExternalFilesDir(null);
        return f != null ? f : ctx.getFilesDir();
    }

    public static String readFileCap(File f, int cap) {
        try {
            return readCap(new FileInputStream(f), cap);
        } catch (Exception e) {
            return "";
        }
    }

    private static String readCap(InputStream is, int cap) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        int limit = cap * 3 + 16;
        while ((n = is.read(buf)) > 0 && bo.size() < limit) bo.write(buf, 0, n);
        is.close();
        String s = new String(bo.toByteArray(), "UTF-8");
        return s.length() > cap ? s.substring(0, cap) : s;
    }

    /** AI 自行调整命令超时（秒，5~3600）。 */
    private static Term.Result setTimeout(Term term, String a) {
        try {
            int sec = Integer.parseInt(a == null ? "" : a.replaceAll("[^0-9]", ""));
            if (sec < 5 || sec > 3600) return r(1, "超时范围 5~3600 秒");
            term.setTimeoutSec(sec);
            return r(0, "已设置命令超时: " + sec + " 秒");
        } catch (Exception e) {
            return r(1, "参数应为秒数");
        }
    }

    private static String shQuote(String p) {
        return "'" + p.replace("'", "'\\''") + "'";
    }

    private static boolean safePath(String p) {
        return p != null && p.startsWith("/") && !p.matches(".*[;&|`><$\\r\\n].*");
    }

    public static Term.Result run(Context ctx, Term term, Tools.Call c, String shotDir) {
        if (c == null || c.tool == null) return r(1, "空调用");
        String a = c.arg == null ? "" : c.arg.trim();
        try {
            switch (c.tool) {
                case "shell":
                    return shellRun(term, a);
                case "timeout":
                    return setTimeout(term, a);
                case "screencap":
                    return screencap(ctx, term, shotDir);
                case "win":
                    if (!a.matches("[A-Za-z0-9._]+")) return r(1, "参数应为包名");
                    return term.run("monkey -p " + a + " -c android.intent.category.LAUNCHER 1");
                case "mouse":
                    return mouse(term, a);
                case "keyboard":
                    return keyboard(term, a);
                case "file":
                    if (!safePath(a)) return r(1, "路径需为绝对路径且无非法字符");
                    return term.run("wc -c < " + shQuote(a) + "; head -c 4000 " + shQuote(a)
                            + "; echo; echo '---'; file " + shQuote(a) + " 2>/dev/null");
                case "httpget":
                    return httpget(ctx, a);
                case "web":
                    return web(a);
                default:
                    return r(1, "未知工具: " + c.tool);
            }
        } catch (Exception e) {
            return r(1, "执行异常: " + e.getMessage());
        }
    }

    /**
     * 脚本整体传入：base64 落盘后 `sh 文件 < /dev/null` 执行。
     * 不再逐行写进交互式 stdin —— 避免注释被拆、脚本抢 stdin、会话被污染。
     */
    private static Term.Result shellRun(Term term, String script) throws Exception {
        if (script == null || script.trim().isEmpty()) return r(1, "脚本为空");
        String b64 = Base64.encodeToString(script.getBytes("UTF-8"), Base64.NO_WRAP);
        String cmd = "mkdir -p /data/local/tmp/ead_ws; printf %s '" + b64
                + "' | base64 -d > /data/local/tmp/ead_ws/.ead_cmd.sh 2>/dev/null"
                + "; sh /data/local/tmp/ead_ws/.ead_cmd.sh < /dev/null";
        return term.run(cmd);
    }

    /** 截图直接落到人类能看见的相册目录（可用 root 写），并由主界面自动作为附件上传。 */
    private static Term.Result screencap(Context ctx, Term term, String shotDir) {
        String dir = (shotDir == null || shotDir.trim().isEmpty())
                ? "/sdcard/Pictures/EAD" : shotDir.trim();
        String name = "截图_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        String path = dir.endsWith("/") ? dir + name : dir + "/" + name;
        try {
            Term.Result x = term.run("mkdir -p " + shQuote(dir)
                    + " && screencap -p " + shQuote(path)
                    + " && ls -l " + shQuote(path));
            if (x.code != 0) {
                String tmp = "/data/local/tmp/ead_ws/" + name;
                x = term.run("mkdir -p /data/local/tmp/ead_ws && screencap -p " + shQuote(tmp));
                if (x.code != 0) return x;
                path = tmp;
            }
            // 上传需要本应用可读的副本（/sdcard 在无存储权限下只能由 root 写）
            String up = new File(filesDir(ctx), name).getAbsolutePath();
            Term.Result cp = term.run("cp -f " + shQuote(path) + " " + shQuote(up)
                    + " && chmod 644 " + shQuote(up));
            StringBuilder sb = new StringBuilder();
            sb.append("已保存: ").append(path).append("\n");
            if (cp.code == 0) sb.append("上传副本: ").append(up).append("\n");
            else sb.append("(可上传副本创建失败，将只回填文本)\n");
            sb.append("(应用会自动把图作为附件上传，无需用户手动发送)\n").append(x.out);
            x.out = sb.toString();
            return x;
        } catch (Exception e) {
            return r(1, "截屏失败: " + e.getMessage());
        }
    }

    private static Term.Result mouse(Term term, String a) {
        String[] p = a.split("\\s+");
        if (p.length < 2) return r(1, "参数应为: x y 行为");
        String x = p[0];
        String y = p[1];
        String b = p.length > 2 ? p[2] : "0";
        if (!x.matches("-?\\d+") || !y.matches("-?\\d+")) return r(1, "坐标需为整数");
        try {
            int xv = Integer.parseInt(x);
            int yv = Integer.parseInt(y);
            if ("1".equals(b)) {
                return term.run("input swipe " + xv + " " + yv + " " + xv + " " + yv + " 800");
            }
            if ("2".equals(b)) {
                return term.run("input swipe " + xv + " " + yv + " " + xv + " " + (yv + 300) + " 300");
            }
            if ("3".equals(b)) {
                return term.run("input swipe " + xv + " " + yv + " " + xv + " " + Math.max(0, yv - 300) + " 300");
            }
            return term.run("input tap " + xv + " " + yv);
        } catch (Exception e) {
            return r(1, "鼠标执行失败: " + e.getMessage());
        }
    }

    private static Term.Result keyboard(Term term, String a) {
        try {
            if (a.startsWith("TEXT:")) {
                String t = a.substring(5).replaceAll("[\\r\\n\\t]", " ").replace(" ", "%s");
                if (t.isEmpty()) return r(1, "文本为空");
                return term.run("input text " + shQuote(t));
            }
            String k = a.startsWith("KEY:") ? a.substring(4).trim() : a;
            if (!k.matches("[A-Za-z0-9_]+")) return r(1, "按键参数非法");
            return term.run("input keyevent " + k);
        } catch (Exception e) {
            return r(1, "键盘执行失败: " + e.getMessage());
        }
    }

    private static Term.Result httpget(Context ctx, String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return r(1, "参数应为完整 URL");
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url.trim()).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EAgent");
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 2 * 1024 * 1024) bo.write(buf, 0, n);
            is.close();
            File f = new File(filesDir(ctx), "HTTP读文件.txt");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bo.toByteArray());
            fo.close();
            String preview = new String(bo.toByteArray(), 0, Math.min(512, bo.size()), "UTF-8");
            return r(0, "HTTP " + code + "\n已保存: " + f.getAbsolutePath()
                    + " (" + bo.size() + " 字节)\n预览:\n" + preview);
        } catch (Exception e) {
            return r(1, "读取失败: " + e.getMessage());
        }
    }

    private static Term.Result web(String spec) {
        String[] parts = spec.split("\\|", 6);
        String method = parts.length > 0 ? parts[0].trim().toUpperCase(Locale.US) : "GET";
        String url = parts.length > 1 ? parts[1].trim() : "";
        String timeoutSec = parts.length > 2 ? parts[2].trim() : "15";
        String cookies = parts.length > 3 ? parts[3].trim() : "";
        String headers = parts.length > 4 ? parts[4] : "";
        String body = parts.length > 5 ? parts[5] : "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) return r(1, "URL 非法");
        int timeout = 15000;
        try {
            int s = Integer.parseInt(timeoutSec.replaceAll("[^0-9]", ""));
            if (s >= 1 && s <= 120) timeout = s * 1000;
        } catch (Exception ignored) {
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(Math.min(timeout, 10000));
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("POST".equals(method) ? "POST" : "GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EAgent");
            if (!cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies.replace("\r", "").replace("\n", "; "));
            }
            if (!headers.trim().isEmpty()) {
                for (String raw : headers.split("[\\r\\n]+")) {
                    String line = raw.trim();
                    if (line.isEmpty()) continue;
                    int i = line.indexOf(':');
                    if (i <= 0) continue;
                    String k = line.substring(0, i).trim();
                    String v = line.substring(i + 1).trim();
                    if (k.isEmpty()) continue;
                    try {
                        conn.setRequestProperty(k, v);
                    } catch (Exception ignored) {
                    }
                }
            }
            if ("POST".equals(method) && body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                byte[] b = body.getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(b.length);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                }
                OutputStream os = conn.getOutputStream();
                os.write(b);
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            return r(0, "HTTP " + code + "\n" + readCap(is, 2000));
        } catch (Exception e) {
            return r(1, "网页访问失败: " + e.getMessage());
        }
    }
}
