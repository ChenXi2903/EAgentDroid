package com.tibo.eagentdroid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** EAgent 原版标记解析（识别头/识别尾 + 命名参数）+ 分级审核。 */
public final class Tools {

    public static final int AUTO = 0;
    public static final int ASK = 1;

    public static class Call {
        public String tool;
        public String arg;
    }

    private Tools() {
    }

    private static final String[][] MAP = {
            {PromptKit.SH_HEAD, "shell"},
            {PromptKit.SH_HEAD_LEGACY, "shell"},
            {PromptKit.TIMEOUT_HEAD, "timeout"},
            {PromptKit.JPG_HEAD, "screencap"},
            {PromptKit.WIN_HEAD, "win"},
            {PromptKit.MOUSE_HEAD, "mouse"},
            {PromptKit.KEY_HEAD, "keyboard"},
            {PromptKit.FILE_HEAD, "file"},
            {PromptKit.HTTPGET_HEAD, "httpget"},
            {PromptKit.WEB_HEAD, "web"}
    };

    public static Call shellCall(String cmd) {
        Call c = new Call();
        c.tool = "shell";
        c.arg = cmd == null ? "" : cmd;
        return c;
    }

    private static String param(String body, String fmt, int group) {
        try {
            Matcher m = Pattern.compile(fmt, Pattern.DOTALL).matcher(body);
            if (m.find()) return m.group(group).trim();
        } catch (Exception ignored) {
        }
        return "";
    }

    public static Call extract(String reply) {
        if (reply == null) return null;
        int pos = -1;
        String tool = null;
        String head = null;
        for (String[] e : MAP) {
            int i = reply.indexOf(e[0]);
            if (i >= 0 && (pos < 0 || i < pos)) {
                pos = i;
                tool = e[1];
                head = e[0];
            }
        }
        if (pos < 0) return null;
        int s = pos + head.length();
        // 头行本身（如 "# EAgent_ps_识别头_完全保留本行注释"）整行跳过，
        // 否则行尾的 "_完全保留本行注释" 会被当命令执行。
        int nl = reply.indexOf('\n', s);
        if (nl >= 0) s = nl + 1;
        int end = reply.length();
        int t = reply.indexOf(PromptKit.TAIL, s);
        if (t >= 0 && t < end) end = t;
        int fence = reply.indexOf("```", s);
        if (fence >= 0 && fence < end) end = fence;
        int nextHead = reply.indexOf(head, s);
        if (nextHead >= 0 && nextHead < end) end = nextHead;
        if (s > end) return null;
        String body = reply.substring(s, Math.min(end, reply.length()));
        Call c = new Call();
        c.tool = tool;
        c.arg = buildArg(tool, body);
        return c;
    }

    private static String buildArg(String tool, String body) {
        switch (tool) {
            case "shell": {
                StringBuilder sb = new StringBuilder();
                for (String raw : body.split("[\\r\\n]+")) {
                    String l = raw.trim();
                    if (l.isEmpty() || l.startsWith("#") || l.startsWith("```")) continue;
                    sb.append(l).append('\n');
                }
                return sb.toString().trim();
            }
            case "timeout": {
                String v = param(body, "agent_timeout_1_star\\((.*?)\\)_agent_timeout_1_end", 1);
                if (v.isEmpty()) v = firstInt(body);
                return v;
            }
            case "screencap":
                return "";
            case "win": {
                String p1 = param(body, "窗口_置顶_1_star\\((.*?)\\)_窗口_置顶_1_end", 1);
                return pickPackage(p1);
            }
            case "mouse": {
                String x = param(body, "agent_mouse_simulate_1_start\\((.*?)\\)_agent_mouse_simulate_1_end", 1);
                String y = param(body, "agent_mouse_simulate_2_start\\((.*?)\\)_agent_mouse_simulate_2_end", 1);
                String b = param(body, "agent_mouse_simulate_3_start\\((.*?)\\)_agent_mouse_simulate_3_end", 1);
                int bx = 0;
                try {
                    bx = Integer.parseInt(firstInt(b));
                } catch (Exception ignored) {
                }
                return firstInt(x) + " " + firstInt(y) + " " + bx;
            }
            case "keyboard": {
                String k = param(body, "agent_keyboard_simulate_1_start\\((.*?)\\)_agent_keyboard_simulate_1_end", 1);
                String txt = param(body, "agent_keyboard_simulate_2_start\\((.*?)\\)_agent_keyboard_simulate_2_end", 1);
                if (!txt.isEmpty()) return "TEXT:" + txt;
                return "KEY:" + k;
            }
            case "file": {
                String all = param(body, "agent_getfile_head\\((.*?)\\)agent_getfile_end", 1);
                String first = all.split(",")[0].trim();
                first = first.replace("'", "").replace("\"", "");
                if (first.isEmpty()) {
                    String line = param(body, "# 我需要获取以下文件：(.*?)(?:\\n|$)", 1);
                    first = line.split("[、,]")[0].replace("<", "").replace(">", "").trim();
                }
                return first;
            }
            case "httpget":
                return param(body, "httpgetfile_1_star\\((.*?)\\)_httpgetfile_1_end", 1);
            case "web": {
                String u = param(body, "网页访问对象_参数1_\\((.*?)\\)_网页访问对象_参数1", 1);
                String m = param(body, "网页访问对象_参数2_\\((.*?)\\)_网页访问对象_参数2", 1);
                String d3 = param(body, "网页访问对象_参数3_\\((.*?)\\)_网页访问对象_参数3", 1);
                String ck = param(body, "网页访问对象_参数4_\\((.*?)\\)_网页访问对象_参数4", 1);
                String hd = param(body, "网页访问对象_参数5_\\((.*?)\\)_网页访问对象_参数5", 1);
                String d6 = param(body, "网页访问对象_参数6_\\((.*?)\\)_网页访问对象_参数6", 1);
                String tm = param(body, "网页访问对象_参数7_\\((.*?)\\)_网页访问对象_参数7", 1);
                String method = m.contains("1") ? "POST" : "GET";
                String data = d3.isEmpty() ? d6 : d3;
                String sec = firstInt(tm);
                return method + "|" + u + "|" + sec + "|" + ck + "|" + hd + "|" + data;
            }
            default:
                return "";
        }
    }

    private static String pickPackage(String s) {
        if (s == null) return "";
        String[] tokens = s.replace("_", " ").replace("如", " ").split("\\s+");
        String best = "";
        for (String t : tokens) {
            String w = t.replace("\"", "").replace("'", "").trim();
            if (w.matches("[A-Za-z][A-Za-z0-9._]*") && w.contains(".")) {
                best = w;
            }
        }
        if (!best.isEmpty()) return best;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String w = tokens[i].replace("\"", "").replace("'", "").trim();
            if (w.matches("[A-Za-z0-9._]+")) return w;
        }
        return "";
    }

    private static String firstInt(String s) {
        Matcher m = Pattern.compile("-?\\d+").matcher(s);
        return m.find() ? m.group() : "0";
    }

    public static int level(Call c) {
        if (c == null || c.tool == null) return ASK;
        switch (c.tool) {
            case "shell":
                return levelCmd(c.arg);
            case "file":
            case "httpget":
            case "web":
            case "win":
                return ASK;
            default:
                return AUTO;
        }
    }

    public static int levelCmd(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return ASK;
        return ASK;
    }
}
