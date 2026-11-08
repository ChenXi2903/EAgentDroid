# Updata 更新记录

## v1.5-Test · CDP 注入版

### 维护说明

- 更新者精力有限且需备战中考，项目为**长期缓慢更新**：由每周两次降为**每月两次**，不会完全停更
- 仍接受建议与 PR；喜欢请 Star

### 新增

1. **CDP 上传通道**：App 经 root 中转连 WebView DevTools socket，
   用 `DOM.setFileInputFiles` 直接把文件塞进网页 —— 彻底绕开 user gesture
2. **自带静态中转** `assets/eadrelay`（aarch64 静态，启动时自动装到 `/data/local/tmp/ead_ws/bin`），
   不再强依赖 Termux socat
3. **自动回复去掉 Beta 字样**，成为正式功能
4. **状态历史**：`filesDir/status_history.txt`，一行一条，上限 100 行，超限删最早 20 行
5. **超时工具**：默认 300s，AI 可调 5~3600s

### 修复

- 命令被拼成一行：克隆节点无 innerText → 自写 DOM 遍历补换行
- 刚进对话页就执行上次命令：新增 `sentOnce` 门禁 + 消息计数基线 + 哈希去重
- 文件自动上传不通：三级降级（CDP → JS点击+原生触摸 → 文本回填）
- 截图必须手动发送：改落相册目录 + 自动上传
- 协议头去 PowerShell 痕迹：`EAgent_ps_` → `EAgent_sh_`（旧写法仍兼容）
- 重命名后密码勘误、文案与实现对齐、若干空指针防护

### 对比

| 维度 | 1.4-Test | 1.5-Test |
|---|---|---|
| 上传 | JS 点击 + 手势兵底 | CDP 注入，无需手势 |
| 外部依赖 | 必须 Termux socat | 自带静态中转 |
| 历史命令 | 仍可能误触发 | sentOnce + 计数 + 哈希三重防护 |
| 长输出 | 转附件（依赖点击） | 转附件（CDP 直上） |
| 可观测性 | 状态行 | 状态行 + 历史文件 |

### 已知边界

- 中转依赖 root 域执行；部分 ROM 可能限制 `/data/local/tmp` 执行
- 32 位设备需另编 eadrelay（仅提供 arm64）
- 网页 DOM 大改版时选择器失效，状态行会报实际命中入口
- 发布请用 `assembleRelease`，不要分发 debug 包
