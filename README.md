# KartLapSE

轻量 Android 可穿戴记录应用（OPPO Watch SE 等）。本仓库已包含可用的 Gradle Wrapper，便于在本地直接编译 APK。

## 一键编译

要求：
- JDK 17（建议安装 Temurin/OpenJDK 17）
- Android SDK（Compile SDK 34）
- Android 构建工具将由 Gradle 自动解析并下载（使用 Android Studio 会更方便）

在 macOS/Linux 终端或 Windows PowerShell 中执行：

```bash
# 克隆仓库后在项目根目录
./gradlew clean :app:assembleDebug
```

成功后 APK 位置：

- `app/build/outputs/apk/debug/app-debug.apk`

若需要 Release 构建，请在 `app/build.gradle.kts` 配置签名后执行：

```bash
./gradlew :app:assembleRelease
```

> 提示：本项目默认未配置 Release 签名，直接打包 Debug 版本即可安装、体验全部功能。

## Android Studio 打包

1. 使用 Android Studio（Giraffe/AGP 8+）打开项目
2. 等待依赖同步完成
3. 选择 Build Variants 为 `debug`
4. 菜单 Build > Build Bundle(s) / APK(s) > Build APK(s)
5. 通过右下角弹窗或 `app/build/outputs/apk/debug/` 获取 APK

## 权限与运行

首次运行时需要授予定位权限（包含后台定位），并建议在手机设置中为应用关闭电池优化，以保证前台服务稳定记录。

