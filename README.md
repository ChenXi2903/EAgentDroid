# EAgentDroid · 1.5-Test

EAgent 原版形态的安卓移植：WebView 驱动 chat.deepseek.com，本地常驻 root shell 执行工具。
本版**照 EAgent 原版协议**（识别头/识别尾 + 命名参数），与 Pro 版为两个独立包名。

## 维护状态

> 更新者精力有限且需备战中考，项目为**长期缓慢更新**：频率由每周两次降为**每月两次**，不会完全停更。
> 仍接受建议与 Pull Request。若对你有用，**记得点个 Star**。

## 本版核心：CDP 通道

```
上传不再靠模拟点击：App → root 中转(socat/自带 eadrelay) → 127.0.0.1:9333
→ WebView DevTools 抽象 socket → DOM.setFileInputFiles
（绕过 user gesture 限制，与 EAgent 原版同思路）
失败自动退回：JS 点击上传入口 → 原生触摸 → 纯文本回填
```

## 界面（六页签）

| 页签 | 内容 |
|---|---|
| 设定 | 三步提示 + 工作目录（可改）+ 新/旧对话 + 【开始】+ 自动回复开关 + 提示词 |
| 对话 | 输入 + 【填入 WEB】/【上传文件】 |
| WEB | DeepSeek 网页本体，首次先登录，右上角【⌨ 键盘】|
| 高频风控 | 封号警告 + 延迟区间（默认 3~12s，仅自动执行时生效）|
| Shell | 命令/结果双窗（root sh）|
| 其它 | 置入窗口 / 启动时置入 / 草稿本 / 删除数据 / 权限 |

底部：夜间模式 · 允许自动执行 · 隐藏命令行 · // 急停 //；顶部状态行可点开看历史。

## 自动化能力

- **自动回复**：开启后填完直接点发送键（实测选择器），人可不在 WEB 页
- **超长输出**：>4000 字落盘为 txt 并自动作为附件上传，回填首尾摘要
- **截图**：落 `/sdcard/Pictures/EAD/` 并自动上传
- **防误触发**：只处理本次发送之后的消息（消息计数基线 + 文本哈希去重）
- **超时**：默认 300s，AI 可用 `timeout` 工具自调 5~3600s

## 协议标记

```
# EAgent_sh_识别头_完全保留本行注释 … # EAgent_sh_识别尾_完全保留本行注释
agent_getjpg_1_star(屏幕)_1_getjpg_end
窗口_置顶_1_star(包名)_窗口_置顶_1_end
agent_mouse_simulate_1_start(X)_…_3_start(0单击 1长按 2下滑 3上滑)_
agent_keyboard_simulate_1_start(键)_…_2_start(文本)_
agent_getfile_head('路径')agent_getfile_end
httpgetfile_1_star(网址)_…_2_star(格式)_
网页访问对象_参数1_(网址)_…_参数7_(超时)_
```

## 权限与构建

- 仅 `INTERNET` + 可选电池优化豁免；ROOT 走 su，启动校验 `id -u`
- JDK 17+ / Gradle 8.9 / AGP 8.5.2 / compileSdk 34 / minSdk 24
- `./gradlew :app:assembleRelease`（签名见 `keystore.properties`）
- `assets/eadrelay`：aarch64 静态中转，启动时自动安装到 `/data/local/tmp/ead_ws/bin`

## 已知限制

- 本地 shell 为 mksh + toybox（非 bash），按 POSIX 写
- 网页改版会使选择器失效；状态行会报实际命中入口便于排查
- 后台 WebView 定时器被节流；置入窗口 = 重载页面
- 32 位设备需要另外编译一份 eadrelay
