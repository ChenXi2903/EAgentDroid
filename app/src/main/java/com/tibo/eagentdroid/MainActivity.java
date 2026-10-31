package com.tibo.eagentdroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String WORK_DIR = "/data/local/tmp/ead_ws";
    private static final String TMP_DIR = "/data/local/tmp/ead_tmp";
    private static final int REQ_PICK = 2001;

    private View root;
    private TextView tvTitle;
    private Button tabSet, tabChat, tabWeb, tabRisk, tabPs, tabOther;
    private View scrollHost, panelWeb;
    private View panelSet, panelChat, panelRisk, panelPs, panelOther;
    private TextView tvLoginState;
    private Button btnGoWeb;
    private Button btnKeyboard;
    private RadioButton rbNew, rbOld;
    private Button btnStart, btnSendChat, btnUpload, btnBring, btnDraft, btnWipe, btnStop, btnPerm;
    private EditText etPrompt, etChat, etMinDelay, etMaxDelay, etPsCmd, etPsOut;
    private CheckBox cbBringStart, cbNight, cbAuto, cbHidePs;
    private WebView wv;

    private Term term;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private String shotDir = "";
    private volatile boolean paused = false;
    private int convNo = 0;
    private int toolNo = 0;
    private int lastSentHash = 0;
    private int lastFwdHash = 0;
    private boolean approving = false;
    private boolean shellReported = false;
    private boolean webReady = false;
    private int sentStage = 0;
    private ValueCallback<Uri[]> chooserCallback;
    private boolean uploadArmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        root.post(() -> new Thread(() -> {
            final boolean ok = RootGate.check();
            runOnUiThread(() -> {
                if (!ok) {
                    fatal();
                    return;
                }
                initApp();
            });
        }).start());
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        tvTitle = findViewById(R.id.tvTitle);
        tabSet = findViewById(R.id.tabSet);
        tabChat = findViewById(R.id.tabChat);
        tabRisk = findViewById(R.id.tabRisk);
        tabPs = findViewById(R.id.tabPs);
        tabWeb = findViewById(R.id.tabWeb);
        tabOther = findViewById(R.id.tabOther);
        panelSet = findViewById(R.id.panelSet);
        panelChat = findViewById(R.id.panelChat);
        panelRisk = findViewById(R.id.panelRisk);
        panelPs = findViewById(R.id.panelPs);
        panelOther = findViewById(R.id.panelOther);
        panelWeb = findViewById(R.id.panelWeb);
        scrollHost = findViewById(R.id.scrollHost);
        rbNew = findViewById(R.id.rbNew);
        rbOld = findViewById(R.id.rbOld);
        btnStart = findViewById(R.id.btnStart);
        btnSendChat = findViewById(R.id.btnSendChat);
        btnUpload = findViewById(R.id.btnUpload);
        btnBring = findViewById(R.id.btnBring);
        btnDraft = findViewById(R.id.btnDraft);
        btnWipe = findViewById(R.id.btnWipe);
        btnPerm = findViewById(R.id.btnPerm);
        btnStop = findViewById(R.id.btnStop);
        etPrompt = findViewById(R.id.etPrompt);
        etChat = findViewById(R.id.etChat);
        etMinDelay = findViewById(R.id.etMinDelay);
        etMaxDelay = findViewById(R.id.etMaxDelay);
        etPsCmd = findViewById(R.id.etPsCmd);
        etPsOut = findViewById(R.id.etPsOut);
        cbBringStart = findViewById(R.id.cbBringStart);
        cbNight = findViewById(R.id.cbNight);
        cbAuto = findViewById(R.id.cbAuto);
        cbHidePs = findViewById(R.id.cbHidePs);
        tvLoginState = findViewById(R.id.tvLoginState);
        btnGoWeb = findViewById(R.id.btnGoWeb);
        btnKeyboard = findViewById(R.id.btnKeyboard);
        wv = findViewById(R.id.wv);
        etPsCmd.setMovementMethod(new ScrollingMovementMethod());
        etPsOut.setMovementMethod(new ScrollingMovementMethod());
        etPrompt.setMovementMethod(new ScrollingMovementMethod());
    }

    private void initApp() {
        shotDir = ToolExec.filesDir(this).getAbsolutePath();
        etPrompt.setText(PromptKit.system(WORK_DIR, TMP_DIR, shotDir));

        tabSet.setOnClickListener(v -> showPanel(0));
        tabChat.setOnClickListener(v -> showPanel(1));
        tabWeb.setOnClickListener(v -> showPanel(2));
        tabRisk.setOnClickListener(v -> showPanel(3));
        tabPs.setOnClickListener(v -> showPanel(4));
        tabOther.setOnClickListener(v -> showPanel(5));

        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        wv.addJavascriptInterface(new Bridge(), "EAD");
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooserCallback != null) {
                    chooserCallback.onReceiveValue(null);
                }
                chooserCallback = cb;
                uploadArmed = false;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(i, "选择要上传的文件"), REQ_PICK);
                    return true;
                } catch (Exception e) {
                    chooserCallback = null;
                    return false;
                }
            }
        });
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("deepseek.com")) {
                    wv.evaluateJavascript(PromptKit.POLLER_JS, null);
                    wv.evaluateJavascript(PromptKit.LOGIN_JS, null);
                }
            }
        });
        wv.loadUrl("https://chat.deepseek.com/");

        btnStart.setOnClickListener(v -> startConversation());
        btnSendChat.setOnClickListener(v -> sendUserMessage());
        btnUpload.setOnClickListener(v -> startWebUpload());
        btnBring.setOnClickListener(v -> {
            showPanel(1);
            wv.reload();
        });
        btnGoWeb.setOnClickListener(v -> showPanel(2));
        btnKeyboard.setOnClickListener(v -> toggleKeyboard());
        btnDraft.setOnClickListener(v -> editDraft());
        btnWipe.setOnClickListener(v -> wipeData());
        btnPerm.setOnClickListener(v -> showPermissionCenter());
        btnStop.setOnClickListener(v -> {
            paused = !paused;
            btnStop.setText(paused ? "// 继续 //" : "// 急停 //");
            toast(paused ? "已急停：停止监听与执行" : "已恢复");
        });
        cbNight.setOnCheckedChangeListener((b, c) -> applyNight(c));

        updateTitle();
        showPanel(cbBringStart.isChecked() ? 1 : 2);
        askWorkspace();
    }

    private void showPanel(int idx) {
        boolean web = (idx == 2);
        scrollHost.setVisibility(web ? View.GONE : View.VISIBLE);
        panelWeb.setVisibility(web ? View.VISIBLE : View.GONE);
        View[] panels = {panelSet, panelChat, panelRisk, panelPs, panelOther};
        for (View p : panels) p.setVisibility(View.GONE);
        if (!web) {
            int p = idx < 2 ? idx : idx - 1;
            if (p >= 0 && p < panels.length) panels[p].setVisibility(View.VISIBLE);
        }
        Button[] tabs = {tabSet, tabChat, tabWeb, tabRisk, tabPs, tabOther};
        for (int i = 0; i < tabs.length; i++) tabs[i].setEnabled(i != idx);
    }

    private void updateTitle() {
        tvTitle.setText("EAgent | 第 " + convNo + " 次对话 | 第 " + toolNo + " 次用工具");
    }

    private void askWorkspace() {
        new AlertDialog.Builder(this)
                .setTitle("继续工作")
                .setMessage("你想继续这个工作目录吗？\n" + WORK_DIR)
                .setPositiveButton("确定", (d, w) -> toast("继续工作目录 " + WORK_DIR))
                .setNegativeButton("取消", (d, w) -> runToolSilent(Tools.shellCall(
                        "mkdir -p " + WORK_DIR + "; find " + WORK_DIR + " -mindepth 1 -delete 2>/dev/null; "
                                + "mkdir -p " + TMP_DIR + "; echo CLEANED")))
                .setCancelable(false)
                .show();
    }

    /* ---------- 对话 ---------- */

    private void startConversation() {
        if (!webReady) {
            toast("请先在 WEB 页登录 DeepSeek");
            showPanel(2);
            return;
        }
        String prompt = etPrompt.getText().toString().trim();
        if (prompt.isEmpty()) {
            toast("系统提示词为空");
            return;
        }
        if (rbNew.isChecked()) {
            convNo++;
            toolNo = 0;
            updateTitle();
            sendToWeb(prompt);
            toast("新对话已开始，系统提示词已发送");
        } else {
            sendToWeb(prompt + "\n\n" + PromptKit.reconnect(TMP_DIR));
            toast("旧对话：已重连并重提提示词");
        }
        showPanel(1);
    }

    private void sendUserMessage() {
        if (!webReady) {
            toast("请先在 WEB 页登录 DeepSeek");
            showPanel(2);
            return;
        }
        String t = etChat.getText().toString().trim();
        if (t.isEmpty()) {
            toast("请输入需求");
            return;
        }
        etChat.setText("");
        sendToWeb(PromptKit.task(t));
    }

    /** 优先走网页官方上传：点附件按钮 → 触发 onShowFileChooser 接管；2.5s 未触发则回退读文本。 */
    private void startWebUpload() {
        if (!webReady) {
            toast("请先在 WEB 页登录 DeepSeek");
            showPanel(2);
            return;
        }
        uploadArmed = true;
        wv.evaluateJavascript(PromptKit.UPLOAD_JS, null);
        wv.postDelayed(() -> {
            if (uploadArmed) {
                uploadArmed = false;
                toast("未触发网页上传，改为读取文本发送");
                pickFile();
            }
        }, 2500);
    }

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_PICK);
        } catch (Exception e) {
            toast("无法打开文件选择器: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 网页发起的文件选择（走 DeepSeek 官方上传通道）
        if (chooserCallback != null) {
            Uri[] uris = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                uris = new Uri[]{data.getData()};
            }
            try {
                chooserCallback.onReceiveValue(uris);
            } catch (Exception ignored) {
            }
            chooserCallback = null;
            return;
        }
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            int n;
            while (is != null && (n = is.read(buf)) > 0 && bo.size() < 12000) bo.write(buf, 0, n);
            if (is != null) is.close();
            String text = new String(bo.toByteArray(), "UTF-8");
            if (text.length() > 4000) text = text.substring(0, 4000) + "\n…[已截断]";
            ethSetTextAndSend(text, uri.toString());
        } catch (Exception e) {
            toast("读取文件失败: " + e.getMessage());
        }
    }

    private void ethSetTextAndSend(String text, String name) {
        sendToWeb("【上传文件】" + name + "\n内容前 4000 字符：\n" + text);
    }

    /* ---------- Agent 回路 ---------- */

    private void handleAiReply(String text) {
        if (paused) return;
        String head = text.trim();
        if (head.startsWith("# 工具返回结果")) return;
        int h = text.hashCode();
        if (h == lastFwdHash || h == lastSentHash) return;
        lastFwdHash = h;

        Tools.Call c = Tools.extract(text);
        if (c == null) {
            if (text.contains(PromptKit.DONE_MARK)) toast("AI: 任务完成");
            return;
        }
        execTool(c);
    }

    private void execTool(final Tools.Call c) {
        if (isFinishing() || isDestroyed()) return;
        final int lvl = Tools.level(c);
        if (lvl == Tools.DENY) {
            psLog(c, "命中高危黑名单，已拒绝");
            sendToWeb(PromptKit.refused("脚本命中高危黑名单，已拒绝。请换方案。"));
            return;
        }
        if (cbAuto.isChecked()) {
            runTool(c);
            return;
        }
        if (approving) return;
        approving = true;
        final String msg = "工具: " + c.tool + "\n" + (c.arg.isEmpty() ? "(无参数)" : c.arg);
        new AlertDialog.Builder(this)
                .setTitle("审核：AI 请求调用工具")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("执行", (d, w) -> {
                    approving = false;
                    runTool(c);
                })
                .setNegativeButton("拒绝", (d, w) -> {
                    approving = false;
                    sendToWeb(PromptKit.refused("用户拒绝了该工具调用，请询问用户意图，不要原样重发。"));
                })
                .show();
    }

    private void runTool(final Tools.Call c) {
        if (!cbHidePs.isChecked()) {
            etPsCmd.setText(c.tool + "\n" + c.arg);
            etPsOut.setText("执行中…");
        }
        pool.execute(() -> {
            try {
                if (term == null || !term.isAlive()) {
                    Term t = new Term();
                    t.start();
                    term = t;
                }
                final Term cur = term;
                runOnUiThread(() -> {
                    if (!shellReported && cur != null) {
                        shellReported = true;
                        toast("终端: " + cur.shellInfo());
                    }
                });
                final Term.Result r = ToolExec.run(MainActivity.this, term, c);
                final String block = PromptKit.result(c.tool, r.code, r.out);
                runOnUiThread(() -> {
                    toolNo++;
                    updateTitle();
                    if (!cbHidePs.isChecked()) etPsOut.setText(r.out);
                    sendToWebDelayed(block);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    if (!cbHidePs.isChecked()) etPsOut.setText("失败: " + e.getMessage());
                    sendToWeb(PromptKit.result(c.tool, -1, "本地执行失败: " + e.getMessage()));
                });
            }
        });
    }

    /** 本地维护类操作：执行但不回填网页。 */
    private void runToolSilent(final Tools.Call c) {
        pool.execute(() -> {
            try {
                if (term == null || !term.isAlive()) {
                    Term t = new Term();
                    t.start();
                    term = t;
                }
                ToolExec.run(MainActivity.this, term, c);
            } catch (Exception ignored) {
            }
        });
    }

    private void psLog(Tools.Call c, String note) {
        if (cbHidePs.isChecked()) return;
        etPsCmd.setText(c.tool + "\n" + c.arg);
        etPsOut.setText(note);
    }

    private void sendToWebDelayed(final String raw) {
        final String text = clipForWeb(raw);
        if (paused) return;
        if (!cbAuto.isChecked()) {
            sendToWeb(text);
            return;
        }
        final int lo0 = parseInt(etMinDelay.getText().toString(), 3);
        final int hi0 = parseInt(etMaxDelay.getText().toString(), 12);
        pool.execute(() -> {
            int lo = lo0;
            int hi = hi0;
            if (hi < lo) hi = lo;
            int sec = lo + (int) (Math.random() * (hi - lo + 1));
            try {
                Thread.sleep(sec * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            runOnUiThread(() -> sendToWeb(text));
        });
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /* ---------- WebView ---------- */

    /** 新发送：JS 只负责填值，回车由原生 dispatchKeyEvent 发（可信事件，isTrusted=true）。 */
    private void sendToWeb(String text) {
        if (wv == null || isFinishing() || isDestroyed() || text == null) return;
        lastSentHash = text.hashCode();
        wv.evaluateJavascript(PromptKit.sendJs(text), null);
    }

    /** 旧实现（合成事件，前端不认，保留作参考，不再调用）。 */
    private void sendToWebLegacy(String text) {
        if (wv == null || isFinishing() || isDestroyed() || text == null) return;
        lastSentHash = text.hashCode();
        String js = "(function(){var txt=" + org.json.JSONObject.quote(text) + ";"
                + "function find(){var t=document.getElementById('chat-input');"
                + "if(t&&t.tagName==='TEXTAREA')return t;"
                + "var c=document.querySelectorAll('div[contenteditable=true]');if(c.length)return c[c.length-1];"
                + "var a=document.querySelectorAll('textarea');return a.length?a[a.length-1]:null;}"
                + "var el=find();if(!el){window.EAD.toast('未找到输入框，请手动粘贴');return;}"
                + "if(el.tagName==='TEXTAREA'){"
                + "var d=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value');"
                + "if(d&&d.set){d.set.call(el,txt);}else{el.value=txt;}}"
                + "else{el.textContent=txt;}"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "function boxc(){var n=el;for(var i=0;i<5&&n;i++){n=n.parentElement;"
                + "if(n&&(n.tagName==='FORM'||n.querySelectorAll('button,[role=button]').length>0))return n;}"
                + "return el.parentElement||document.body;}"
                + "function findBtn(){var bs=boxc().querySelectorAll('button,[role=button]');var best=null,bestScore=0;"
                + "for(var i=0;i<bs.length;i++){var b=bs[i];"
                + "var lab=((b.getAttribute('aria-label')||'')+' '+(b.title||'')+' '+(b.className||'')).toLowerCase();"
                + "if(/上传|附件|upload|attach|深度|思考|联网|搜索|search|voice|mic/.test(lab))continue;"
                + "var s=0;if(/发送|send|submit|提交/.test(lab))s+=10;if(b.querySelector('svg'))s+=2;"
                + "if(b.disabled||b.getAttribute('aria-disabled')==='true')s-=20;"
                + "if(s>bestScore){bestScore=s;best=b;}}"
                + "return bestScore>0?best:null;}"
                + "function trySend(){var b=findBtn();"
                + "if(b){['mousedown','mouseup','click'].forEach(function(t){try{b.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}));}catch(e){}});}"
                + "var ev={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true,composed:true};"
                + "try{el.dispatchEvent(new KeyboardEvent('keydown',ev));el.dispatchEvent(new KeyboardEvent('keypress',ev));el.dispatchEvent(new KeyboardEvent('keyup',ev));}catch(e){}}"
                + "function empty(){return ((el.value||el.textContent||'').trim()).length===0;}"
                + "var tries=0;try{el.focus();}catch(e){}"
                + "function attempt(){trySend();setTimeout(function(){if(empty()){window.EAD.toast('已发送');return;}"
                + "tries++;if(tries<2){attempt();return;}"
                + "window.EAD.toast('文本已填入但未自动发出，请手动点发送');},900);}"
                + "setTimeout(attempt,400);})()";
        wv.evaluateJavascript(js, null);
    }

    private class Bridge {
        @JavascriptInterface
        public void onAiReply(String text) {
            if (text == null || text.trim().isEmpty()) return;
            runOnUiThread(() -> {
                String url = wv != null ? wv.getUrl() : null;
                if (url == null || !url.contains("deepseek.com")) return;
                handleAiReply(text);
            });
        }

        @JavascriptInterface
        public void onTextReady() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                hideIme();
                showPanel(2);
                toast("已填入输入框，请在网页内手动点发送");
            });
        }

        @JavascriptInterface
        public void onLoginReady() {
            runOnUiThread(() -> {
                if (webReady) return;
                webReady = true;
                if (tvLoginState != null) tvLoginState.setText("登录状态：已登录 ✔");
                toast("已检测到登录，可以回设定页点【开始】");
            });
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }

    /* ---------- 其它 ---------- */

    private void editDraft() {
        File f = new File(ToolExec.filesDir(this), "草稿本.txt");
        String cur = ToolExec.readFileCap(f, 5000);
        final EditText input = new EditText(this);
        input.setText(cur);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setMovementMethod(new ScrollingMovementMethod());
        new AlertDialog.Builder(this)
                .setTitle("草稿本（追加保存）")
                .setView(input)
                .setPositiveButton("追加保存", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.isEmpty()) {
                        toast("内容为空");
                        return;
                    }
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter(f, true);
                        fw.write(v + "\n");
                        fw.close();
                        toast("已保存: " + f.getAbsolutePath());
                    } catch (Exception e) {
                        toast("保存失败: " + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void wipeData() {
        new AlertDialog.Builder(this)
                .setTitle("删除数据")
                .setMessage("将删除：截图 / 下载文件 / 草稿本 / 临时目录，并重置计数。确定？")
                .setPositiveButton("删除", (d, w) -> {
                    try {
                        File dir = ToolExec.filesDir(this);
                        File[] fs = dir.listFiles();
                        if (fs != null) {
                            for (File f : fs) f.delete();
                        }
                        convNo = 0;
                        toolNo = 0;
                        updateTitle();
                        runToolSilent(Tools.shellCall("find " + WORK_DIR + " -mindepth 1 -delete 2>/dev/null; echo WIPED"));
                        toast("数据已删除");
                    } catch (Exception e) {
                        toast("删除失败: " + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyNight(boolean night) {
        int bg = night ? 0xFF101418 : 0xFFFFFFFF;
        int inputBg = night ? 0xFF1A2027 : 0xFFFFFFFF;
        int fg = night ? 0xFFE6EDF3 : 0xFF1B1F24;
        root.setBackgroundColor(bg);
        tvTitle.setTextColor(fg);
        etPrompt.setTextColor(fg);
        etChat.setTextColor(fg);
        etPsCmd.setTextColor(fg);
        etPsOut.setTextColor(fg);
        etPrompt.setBackgroundColor(inputBg);
        etChat.setBackgroundColor(inputBg);
        etPsCmd.setBackgroundColor(inputBg);
        etPsOut.setBackgroundColor(inputBg);
    }

    private void showPermissionCenter() {
        final String[] items = {"忽略电池优化（后台轮询保活）", "本应用系统设置页", "权限说明"};
        new AlertDialog.Builder(this)
                .setTitle("权限")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            toast("无法打开设置页: " + e.getMessage());
                        }
                    } else if (which == 1) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            toast("无法打开设置页: " + e.getMessage());
                        }
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("权限说明")
                                .setMessage("本应用仅申请：\n"
                                        + "· INTERNET（访问 DeepSeek 网页）\n"
                                        + "· 忽略电池优化（可选，保后台轮询）\n"
                                        + "· ROOT（su 授权，启动时校验，失败即退出）\n\n"
                                        + "不申请存储、通知、无障碍等权限；文件读写走应用私有目录 + root。")
                                .setPositiveButton("知道了", null)
                                .show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 原生可信回车（可选 Ctrl+Enter）。
     *  EAgent 在桌面端用 CDP 注入平台级事件；Android 侧等价手法就是往 WebView 分发真实 KeyEvent。 */
    private void nativePressEnter(boolean ctrl) {
        try {
            wv.requestFocus();
            long t = SystemClock.uptimeMillis();
            if (ctrl) {
                wv.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, KeyEvent.META_CTRL_ON));
            }
            wv.dispatchKeyEvent(new KeyEvent(t, t + 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, ctrl ? KeyEvent.META_CTRL_ON : 0));
            wv.dispatchKeyEvent(new KeyEvent(t, t + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, ctrl ? KeyEvent.META_CTRL_ON : 0));
            if (ctrl) {
                wv.dispatchKeyEvent(new KeyEvent(t, t + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
            }
        } catch (Exception ignored) {
        }
    }

    private void verifySendResult() {
        if (wv == null) return;
        wv.evaluateJavascript(PromptKit.CHECK_JS, value -> {
            boolean empty = value != null && value.contains("true");
            if (empty) {
                toast("已发送");
                return;
            }
            if (sentStage == 0) {
                sentStage = 1;
                nativePressEnter(true);
                wv.postDelayed(this::verifySendResult, 1200);
                return;
            }
            if (sentStage == 1) {
                sentStage = 2;
                wv.evaluateJavascript(PromptKit.CLICK_SEND_JS, null);
                wv.postDelayed(this::verifySendResult, 1200);
                return;
            }
            toast("文本已填入但未自动发出，请手动点发送");
        });
    }

    /** 收软键盘（填值后不抢焦点、不弹输入法）。 */
    private void hideIme() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && wv != null) {
                imm.hideSoftInputFromWindow(wv.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    /** 右上角键盘键：先让网页内的输入框获焦，再弹输入法；再点一次收起。 */
    private void toggleKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null || wv == null) return;
            if (imm.isActive()) {
                imm.hideSoftInputFromWindow(wv.getWindowToken(), 0);
                wv.clearFocus();
                return;
            }
            showPanel(2);
            wv.evaluateJavascript(PromptKit.FOCUS_JS, null);
            wv.requestFocus();
            wv.postDelayed(() -> {
                try {
                    imm.showSoftInput(wv, InputMethodManager.SHOW_FORCED);
                } catch (Exception ignored) {
                }
            }, 200);
        } catch (Exception ignored) {
        }
    }

    /** 回填内容过长会把网页输入框撑炸、吞掉发送键，这里做中间截断。 */
    private static String clipForWeb(String s) {
        if (s == null) return null;
        if (s.length() <= 4000) return s;
        return s.substring(0, 1800)
                + "\n…[中间省略 " + (s.length() - 3400) + " 字符]…\n"
                + s.substring(s.length() - 1600);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void fatal() {
        new AlertDialog.Builder(this)
                .setTitle("需要 ROOT")
                .setMessage("未获得 ROOT 权限，EAgent 无法运行。请在 su 授权弹窗中允许，或先完成 root。")
                .setCancelable(false)
                .setPositiveButton("退出", (d, w) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack() && panelWeb.getVisibility() == View.VISIBLE) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        approving = false;
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        final Term t = term;
        term = null;
        if (t != null) new Thread(t::kill).start();
        super.onDestroy();
    }
}
