package com.tibo.eagentdroid;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/** 常驻 root shell（su）。支持多行脚本；cwd/env 跨命令保留。 */
public final class Term {

    public static class Result {
        public int code;
        public String out;
    }

    private static final int SOFT_CAP = 200000;
    private static final int KEEP = 8000;
    /** 默认 300s；AI 可通过“超时”工具自行调整。 */
    private volatile long timeoutMs = 300000L;

    public void setTimeoutSec(int sec) {
        if (sec >= 5 && sec <= 3600) timeoutMs = sec * 1000L;
    }

    public int timeoutSec() {
        return (int) (timeoutMs / 1000);
    }

    private Process proc;
    private PrintStream stdin;
    private BufferedReader stdout;
    private volatile boolean alive = false;
    private int seq = 0;

    private String shellPath = "sh";
    private String shellName = "mksh";

    /** 供 UI 显示：当前终端解释器。 */
    public String shellInfo() {
        return "sh".equals(shellPath) ? "mksh" : (shellName + " @ " + shellPath);
    }

    private static String dirOf(String p) {
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(0, i) : p;
    }

    /** 一次性 su 探测 bash；设备没有 bash 就返 "none"。 */
    private static String probeBash() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "for p in /data/data/com.termux/files/usr/bin/bash /system/xbin/bash /system/bin/bash "
                            + "/vendor/bin/bash /data/adb/magisk/bash /data/local/tmp/bash /data/local/tmp/ead_ws/bash; "
                            + "do [ -x \"$p\" ] && LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib "
                            + "\"$p\" --norc --noprofile -c 'echo ok' >/dev/null 2>&1 "
                            + "&& { echo -n \"$p\"; exit 0; }; done; echo -n none"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            p.waitFor();
            p.destroy();
            return line == null ? "none" : line.trim();
        } catch (Exception e) {
            return "none";
        }
    }

    private void spawnShell() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("su");
        pb.redirectErrorStream(true);
        proc = pb.start();
        stdin = new PrintStream(new BufferedOutputStream(proc.getOutputStream()), true, "UTF-8");
        stdout = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
        alive = true;
        stdin.println("export PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:/data/adb/ksu/bin:/debug_ramdisk:$PATH");
        stdin.flush();
    }

    public void start() throws IOException {
        spawnShell();
        String bash = probeBash();
        if (!bash.isEmpty() && !"none".equals(bash)) {
            if (bash.contains("com.termux")) {
                stdin.println("export PREFIX=/data/data/com.termux/files/usr");
                stdin.println("export LD_LIBRARY_PATH=$PREFIX/lib");
                stdin.println("export PATH=$PREFIX/bin:$PATH");
            } else {
                stdin.println("export PATH=" + dirOf(bash) + ":$PATH");
            }
            stdin.println("exec " + bash + " --norc --noprofile");
            stdin.flush();
            boolean ok = false;
            try {
                Result probe = run("echo EAD_VER=${BASH_VERSION:-none}");
                ok = !probe.out.contains("EAD_VER=none") && isAlive();
            } catch (Exception ignored) {
            }
            if (ok) {
                shellPath = bash;
                shellName = "bash";
            } else {
                shellPath = "sh";
                shellName = "mksh";
                if (!isAlive()) {
                    spawnShell();
                    try {
                        run("true");
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        stdin.println("mkdir -p /data/local/tmp/ead_ws && cd /data/local/tmp/ead_ws");
        stdin.flush();
    }

    public boolean isAlive() {
        return alive && proc != null;
    }

    public Result run(String cmd) throws IOException {
        if (!isAlive()) throw new IOException("root shell 会话未建立或已断开");
        if (cmd == null || cmd.trim().isEmpty()) throw new IOException("空命令");
        seq++;
        String nonce = "__EAD_DONE_" + seq + "_";
        // 清掉上一轮遗留（后台进程输出等），防止旧输出被当成本轮结果
        try {
            while (stdout.ready()) {
                if (stdout.read() < 0) break;
            }
        } catch (Exception ignored) {
        }
        stdin.print(cmd.endsWith("\n") ? cmd : cmd + "\n");
        stdin.println("__EAD_RC=$?; echo; echo " + nonce + "$__EAD_RC");
        stdin.flush();

        StringBuilder sb = new StringBuilder();
        StringBuilder cur = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (!stdout.ready()) {
                if (processDead()) break;
                if (System.currentTimeMillis() > deadline) {
                    alive = false;
                    try {
                        proc.destroy();
                    } catch (Exception ignored) {
                    }
                    throw new IOException("命令超时，可能等待交互输入；会话已强制终止");
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            int ch;
            try {
                ch = stdout.read();
            } catch (IOException e) {
                break;
            }
            if (ch < 0) break;
            if (ch == '\n') {
                String line = cur.toString();
                cur.setLength(0);
                int idx = line.indexOf(nonce);
                if (idx >= 0) {
                    if (idx > 0 && sb.length() < SOFT_CAP) {
                        sb.append(line, 0, idx).append('\n');
                    }
                    Result r = new Result();
                    String c = line.substring(idx + nonce.length()).trim();
                    try {
                        r.code = Integer.parseInt(c);
                    } catch (NumberFormatException ignored) {
                        r.code = 0;
                    }
                    r.out = clip(sb.toString());
                    return r;
                }
                if (sb.length() < SOFT_CAP) {
                    sb.append(line).append('\n');
                }
                continue;
            }
            if (ch != '\r' && cur.length() < 65536) {
                cur.append((char) ch);
            }
        }
        if (cur.length() > 0 && sb.length() < SOFT_CAP) {
            sb.append(cur);
        }
        alive = false;
        throw new IOException("root shell 已退出(EOF)");
    }

    private boolean processDead() {
        try {
            proc.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            return false;
        }
    }

    private static String clip(String s) {
        if (s.length() <= KEEP) return s;
        return s.substring(0, 500) + "\n…[输出过长，已截断]…\n" + s.substring(s.length() - (KEEP - 500));
    }

    public void kill() {
        alive = false;
        try {
            if (stdin != null) {
                stdin.println("exit");
                stdin.flush();
            }
        } catch (Exception ignored) {
        }
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (proc != null) proc.destroy();
        } catch (Exception ignored) {
        }
    }
}
