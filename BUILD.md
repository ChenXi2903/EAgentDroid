# EAgentDroid 编译说明（人类 / AI 双用）

> 一句话：**AGP 8.5.2 锁死**。版本冲突就换工具，不要改工程里的版本号。

---

## 1. 版本矩阵

| 工具 | 推荐 | Fallback | 禁止 |
|---|---|---|---|
| JDK | **17 (17.0.x)** | 21 | 8 / 11 / 24+ |
| Gradle | **8.9** | 8.7 / 8.10.2 | < 8.7 |
| Android Studio | Ladybug 2024.2+ | Koala | — |
| SDK Platform | **android-34** | — | — |
| Build-Tools | **34.0.0** | — | — |
| NDK / CMake / Kotlin | **不需要** | — | — |

---

## 2. 环境变量

```bash
export JAVA_HOME=/path/to/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
```

自检：`java -version`（期望 17.x）、`gradle -v`（期望 8.7+）。

## 3. local.properties

工程根目录创建：

```properties
sdk.dir=/path/to/Android/Sdk
```

---

## 4. 构建

```bash
cd EAgentDroid
gradle wrapper --gradle-version 8.9   # 工程未带 wrapper，先跑一次
./gradlew :app:assembleDebug          # 或 assembleRelease（签名见 §6）
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Android Studio 路线：`File → Open → 选 EAgentDroid 根目录（含 settings.gradle 那层）→ Sync → Build APK(s)`。

---

## 5. 纪律（AI 必须遵守）

**不得改动：** `applicationId` / `namespace` / AGP 版本 / `compileSdk` / `targetSdk` / `minSdk`；
`PromptKit` 的识别头常量；`Tools` 的 DENY_PAT；`AndroidManifest.xml` 的权限与 icon；
`res/layout` 的控件 id（Java 全靠它们绑定）。

**不得新增：** 依赖、Kotlin、ProGuard、主题或图标替换。

**只能：** 修构建日志指出的那一行，改完重新构建。源文件必须 UTF-8。

---

## 6. 签名

本工程**不附带任何密钥**。发布时自行准备 keystore：把 `keystore.properties.example` 拷为 `keystore.properties` 并填入自己的值（该文件已被 .gitignore 忽略）。密码为空或文件不存在时自动跳过签名，仍可编出 unsigned APK。

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（你的证书已签）
```

若报 `Alias ... does not exist` → 别名与 keystore 中的不一致；
`password was incorrect` → 密码错；
覆盖安装旧包报签名冲突 → 先卸载。

---

## 7. 报错对照

| 报错 | 处置 |
|---|---|
| `SDK location not found` | 补 local.properties |
| `Unsupported class file major version` | JDK 过高 → 切 17 |
| `Minimum supported Gradle version is 8.7` | 升 Gradle 8.9 |
| `Failed to find Build Tools revision 34.0.0` | `sdkmanager "build-tools;34.0.0"` |
| `platforms;android-34` 缺失 | `sdkmanager "platforms;android-34"` |
| `Could not download aapt2` | 清 `~/.gradle/caches` 重试 |
| deprecation warning | 正常，忽略 |

---

## 8. 验收

```
APK 体积 ≈ 2.5～3.5 MB（含 240KB 图标），无 .so
装机：su 授权 → 设定页【开始】发提示词 → 对话页发需求 → 审核/自动执行 → 结果回填
日志出现“EAgent 监听已启动”且工具执行有回显 = 通过
```
