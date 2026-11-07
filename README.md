# EAgentDroid · 1.4-Test

EAgent 原版形态的安卓移植版：WebView 驱动 chat.deepseek.com，本地常驻 root shell 执行工具。
本版**照 EAgent 原版 UI 与协议**（识别头/识别尾 + 命名参数），与 Pro 版为两个独立包名。

## 维护状态（先读这段）

> 更新者精力已经不太充足，而且要备战中考，项目进入**长期缓慢更新**：
> 更新频率由原来的每周两次，降为**每月两次**；不会完全停更。
> 仍然接受建议与 Pull Request。
> 本次更新（1.4-Test）已经修复了大量实用性的 bug，易用性大幅提升；如果它对你有用，**记得点个 Star**。

## 界面（六页签）

| 页签 | 内容 |
|---|---|
| 设定 | 三步提示 + **工作目录（可改）** + 新/旧对话 + 【开始】+ 自动回复（Beta）+ 可编辑系统提示词 |
| 对话 | 输入框 + 【填入 WEB】/【上传文件】 + 去 WEB 页按钮 |
| WEB | DeepSeek 网页本体，整页铺满；**首次务必先在此登录**；右上角【⌨ 键盘】 |
| 高频风控 | 封号警告 + 随机延迟区间（默认 3~12s，仅自动执行时生效）|
| Shell | (执行指令) / (执行结果) 双窗回显（root sh）|
| 其它 | 置入窗口 / 启动时置入 / 草稿本 / 删除数据 / 权限 |

底栏：夜间模式 · 允许自动执行 · 隐藏命令行窗口 · // 急停 //
顶部状态行：原 toast 内容全部汇入，过长省略，点击看全文。

## 发送：手动 / 自动回复（Beta）

- 默认**手动**：只把文本填入网页输入框（不 focus、不派发回车），由你在 WEB 页点发送
- 勾选【自动回复（Beta）】后：用实测选择器定位发送键并点击，人可以在其它页面
- 填值不抢焦点；要打字点右上角【⌨ 键盘】，再点一次收起

## 自动附件

- **超长输出**（>4000 字）：全文写 `.txt` 并自动作为附件上传，回填只带首尾摘要 + 路径
- **截图**：落 `/sdcard/Pictures/EAD/`（相册可见），自动作为附件上传
- 上传走网页官方通道：`FileProvider` + `onShowFileChooser` 直接回 `Uri`，不弹系统选择器；
  JS 点击缺 user gesture 时降级为原生触摸点入口，7s 未触发则退化为文本回填

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
| 截图(带光标) | root screencap，相册目录 + 自动上传 |
| 上传文件(多模态) | 接管网页 file chooser → 官方上传（失败回退读文本）|
| skill.zip / 7za | 不实现 |

## 权限

仅 `INTERNET` + 可选 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；ROOT 走 su 授权，启动 `id -u` 校验，非 0 退出。
无存储/通知/无障碍权限。

## 构建与签名

- JDK 17+（21 可）· Gradle 8.9 · AGP 8.5.2 · compileSdk 34 / minSdk 24
- `./gradlew :app:assembleRelease`
- 签名：`keystore.properties` 存在且密码非空才启用 release 签名，否则产出 unsigned

## 已知限制

- 截图与长输出依赖网页上传通道，站点大改版可能失效（状态行会报命中入口便于排查）
- 置入窗口 = 重载页面；后台 WebView 定时器会被节流，跑任务请保持前台
- 思考块靠 `.ds-think-content` + 通配双保险剔除，站点改名仍可能混入
- 高危黑名单已取消：工具调用只有「手动执行 / 自动执行」两态
- 本地 shell 为 mksh + toybox，不是 bash；提示词要求按 POSIX 写
