# EAgentDroid

EAgent 原版形态的安卓移植版：WebView 驱动 chat.deepseek.com，本地常驻 root shell 执行工具。
与 Pro 版的区别：本版**照 EAgent 原版 UI 与协议**（识别头/识别尾 + 命名参数）。

## 界面（六页签）

| 页签 | 内容 |
|---|---|
| 设定 | 三步提示 + 新/旧对话 + 【开始】+ 可编辑系统提示词 |
| 对话 | 发送框 + 发送 / 上传文件（SAF，读文本 4000 字）+ 去 WEB 页按钮 |
| WEB | DeepSeek 网页本体，整页铺满；**首次务必先在此登录** |
| 高频风控 | 封号警告 + 随机延迟区间（默认 3~12s，仅自动执行时生效）|
| PowerShell | (执行指令) / (执行结果) 双窗回显（实际执行 root sh）|
| 其它 | 置入窗口 / 启动时置入 / 草稿本 / 删除数据 / 权限 |

底栏：夜间模式 · 允许自动执行 · 隐藏命令行窗口 · // 急停 //

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
- 签名：本仓库不含密钥。把 `keystore.properties.example` 拷为
  `keystore.properties` 并填入自己的 storeFile/storePassword/keyAlias/keyPassword；
  文件不存在或密码为空时自动跳过签名（仍可编出 unsigned APK）。

## 已知限制

- 截图不进对话，需手动作为附件发送
- 置入窗口 = 重载页面并切到对话页（安卓无窗口嵌入）
- 后台 WebView 定时器被节流，跑任务请保持前台
- 页面 DOM 改版时自动收发失灵 → 手动粘贴兜底

## 项目状态（重要）

- 本项目处于**测试阶段，存在大量不稳定因素**，请勿用于生产环境。
- 借鉴 [YaoGaoChaoLe/EAgent](https://github.com/YaoGaoChaoLe/EAgent) 的思路迁移到 Android；
  由于移动端权限模型、后台限制、WebView/CDP 差异，**搬到安卓有很多违和的地方**。
- 作者会**随缘更新、修 bug**，主要看 issue 反馈。

## 许可

GPL-3.0，遵循原项目许可并保留其署名。
