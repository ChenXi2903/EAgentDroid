package com.tibo.eagentdroid;

/** ROOT 闸门：su -c id -u 必须返回 0，否则整个应用拒绝工作。 */
public final class RootGate {

    private RootGate() {
    }

    public static boolean check() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id -u"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            p.waitFor();
            p.destroy();
            return line != null && line.trim().equals("0");
        } catch (Exception e) {
            return false;
        }
    }
}
