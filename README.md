# EAgentDroid

EAgent 原版形态的安卓移植版：WebView 驱动 chat.deepseek.com，本地常驻 root shell 执行工具。
与 Pro 版的区别：本版**照 EAgent 原版 UI 与协议**（识别头/识别尾 + 命名参数）。

## 界面（六页签）

| 页签 | 内容 |
|---|---|
| 设定 | 三步提示 + 新/旧对话 + 【开始】+ 可编辑系统提示词 |
| 对话 | 输入框 + 【填入 WEB】/【上传文件】（优先网页官方上传，失败回退读文本）+ 去 WEB 页按钮 |
| WEB | DeepSeek 网页本体，整页铺满；**首次务必先在此登录**；右上角【⌨ 键盘】；不自动发送 |
| 高频风控 | 封号警告 + 随机延迟区间（默认 3~12s，仅自动执行时生效）|
| PowerShell | (执行指令) / (执行结果) 双窗回显（实际执行 root sh）|
| 其它 | 置入窗口 / 启动时置入 / 草稿本 / 删除数据 / 权限 |

底栏：夜间模式 · 允许自动执行 · 隐藏命令行窗口 · // 急停 //

## 发送与键盘（手动模式）

- App **不自动发送**：只把文本填入网页输入框，然后切到 WEB 页，由你手动点发送
- 填值不抢焦点、不唤输入法；要打字点右上角【⌨ 键盘】，再点一次收起
- 上传文件优先走网页官方上传（接管文件选择器）；拿不到网页上传控件时回退“读文本发送”

## 协议标记（EAgent 原版）

```
# EAgent_ps_识别头_完全保留本行注释 … # EAgent_ps_识别尾_完全保留本行注释
agent_getjpg_1_star(屏幕)_1_getjpg_end
窗口_置顶_1_star(包名)_窗口_置顶_1_end
agent_mouse_simulate_1_start(X)_…_3_start(0单击 1长按 2下滑 3上滑)_
agent_keyboard_simulate_1_start(键)_…_2_start(文本)_
agent_getfile_head('路径')agent_getfile_end
httpgetfile_1_star(网址)_…_2_star(格式)_
网页访问对象_参数1_(网址)_…_参数7_(超时)_
```

## 安卓化替换

| EAgent(Windows) | 本版 |
|---|---|
| PowerShell 新进程 | 常驻 root sh（cwd/env 保留，多行脚本）|
| 鼠标/键盘 | input tap / swipe / keyevent / text |
| 窗口_置顶(句柄) | 包名 → monkey 拉前台 |
| 截图(带光标标) | root screencap，双层落盘；图需手动附件 |
| 上传文件(多模态) | SAF 选文件 → 读文本 4000 字 |
| skill.zip / 7za | 不实现 |

## 权限

仅 `INTERNET` + 可选 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；ROOT 走 su 授权，启动时 `id -u` 校验，非 0 直接退出。
无存储/通知/无障碍权限。

## 构建与签名

- JDK 17+，Android Studio 打开根目录，`assembleDebug` / `assembleRelease`
- 签名：本仓库不含密钥。将 `keystore.properties.example` 拷为
  `keystore.properties` 并填入自己的值；不存在或密码为空时自动跳过签名，仍可编出 unsigned APK。

## 已知限制

- 截图不进对话，需手动作为附件发送
- 置入窗口 = 重载页面并切到对话页（安卓无窗口嵌入）
- 后台 WebView 定时器被节流，跑任务请保持前台
- 页面 DOM 改版时识别失败 → 用【填入 WEB】兜底；仍不行则手动复制粘贴
- 自动发送已移除：RESULT 回填也只填入不发送
