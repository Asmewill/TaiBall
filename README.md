# 🎱 台球小游戏 (TaiBall)

[English README](./README.en.md)

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
- ✅ 加宽球桌（桌面宽高比调优为 1.95）
- ✅ 力度控制与瞄准线
- ✅ 动态力度提示（实时数值 + 进度条 + 轻/中/重档位）
- ✅ 力度范围 0~50，满力度击球速度已增强
- ✅ 得分统计
- ✅ 振动反馈
- ✅ 横屏游戏
- ✅ 适配主流安卓手机

## 游戏玩法

1. 点击白球附近区域
2. 拖动鼠标/手指控制方向和力度（0~50）
3. 拖动过程中可实时查看力度数值、进度条和强度档位提示
4. 松开击球
5. 将所有球打入袋后，最后打入 8 号黑球获胜
6. ⚠️ 注意：8 号球必须最后打入，否则游戏结束

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




## TaiBall项目优化提示词
- 1.目前项目的主界面如上图所示,台球桌面太长，高度不够，白球和其他其他球类太小，挥杆的力度范围只有0到100，太小了.
- 2.需要修改的部分：缩短台球桌面，高度增加，增加白球和其他其他球类太小，增加挥杆的力度范围到0到300,合理调整各个UI的位置，确保整个界面能够增加用户的游戏体验.
- 3.把台球宽度修改为原来的1.5倍，力度范围到由原来的0到100，增加到0到300，并可以动态提示挥杆的力度.
- 4.力度范围变成0到50，但是力度的强度要在原来的基础上增加3倍，现在就是力度范围太大，需要手指滑动的距离太远，始终感觉白球力度不够，无法撞击其他球类。
- 5.现在就是拉满力度后，白球撞击其他球类的力度任然偏弱，需求：当我拉满50的力度时，白球撞击力度在现在的基础上翻3倍。
- 6.在就是拉满力度后，白球撞击其他球类的力度依旧偏弱，需求：当我拉满50的力度时，白球在撞击力度在现在的基础上翻2倍。


