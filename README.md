# 🎱 台球小游戏 (TaiBall)

一个基于 Android 的台球游戏，使用 Java 开发。

## 项目信息

- **包名**: com.openclaw.taiball
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **开发语言**: Java

## 功能特点

- ✅ 真实物理碰撞效果
- ✅ 6 个标准袋口
- ✅ 15 个彩球 + 白球
- ✅ 力度控制和瞄准线
- ✅ 得分统计
- ✅ 振动反馈
- ✅ 横屏游戏
- ✅ 适配主流安卓手机

## 游戏玩法

1. 点击白球附近区域
2. 拖动鼠标/手指控制方向和力度
3. 松开击球
4. 将所有球打入袋后，最后打入 8 号黑球获胜
5. ⚠️ 注意：8 号球必须最后打入，否则游戏结束

## 构建说明

### 前提条件

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK 34

### 构建步骤

1. 用 Android Studio 打开此项目文件夹
2. 等待 Gradle 同步完成
3. 点击 `Run` 按钮或使用 `Shift+F10` 运行

### 命令行构建

```bash
# 进入项目目录
cd E:\AndroidRel\OpenClawSpace\TaiBall

# Windows
gradlew.bat assembleDebug

# 生成的 APK 位置
# app\build\outputs\apk\debug\app-debug.apk
```

## 项目结构

```
TaiBall/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/openclaw/taiball/
│   │       │   ├── MainActivity.java    # 主活动
│   │       │   └── GameView.java        # 游戏视图
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   ├── values/
│   │       │   ├── drawable/
│   │       │   └── mipmap-anydpi-v26/
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 技术实现

- **自定义 View**: GameView 继承自 View，实现游戏渲染
- **物理引擎**: 简单的碰撞检测和响应
- **游戏循环**: 使用 View 的 postDelayed 实现 60FPS 刷新
- **触摸控制**: 处理 MotionEvent 实现击球控制

## 许可证

本项目仅供学习和个人娱乐使用。

---

*Created by OpenClaw Assistant*
