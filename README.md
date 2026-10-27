# LensKit · 镜头调色

> 手机上的实时调色 + 运镜相机。**纯本地处理**，照片和视频不离开设备。

一个单文件 WebGL 相机应用：一套代码同时跑在浏览器和 Android APK 里。
实时滤镜取景、一键出片运镜模板、光轨长曝光、延时摄影、专业参数、AI 场景识别与构图提示。

[![HTML](https://img.shields.io/badge/HTML-single--file-e34f26?style=flat-square)](#)
[![WebGL](https://img.shields.io/badge/WebGL-GLSL%20ES%201.0-5586a4?style=flat-square)](#)
[![Android](https://img.shields.io/badge/Android-10%2B%20%C2%B7%2053KB-3ddc84?style=flat-square)](#)
[![License](https://img.shields.io/badge/License-MIT-f0a03c?style=flat-square)](LICENSE)

---

## 快速开始

| 方式 | 说明 |
|---|---|
| **网页版** | 直接打开 [`lenskit/index.html`](lenskit/index.html)（需 `https://` 或 `localhost` 才能调用摄像头） |
| **在线安装** | 打开 GitHub Pages 站点 → 下载 APK → 手机上点开安装 |
| **自行构建** | 见下方[构建](#构建-apk) |

Android 端最低 **10.0（API 29）**，已在 Xiaomi 17 Max 上验证，主流安卓机型通用。

## 功能

### 调色
- **10 款预设**：胶片 Gold / 富士 NC / 日系 / 港风 / 德味 / 黑金 / 赛博 / 莫兰迪 / 黑白 …
- **十余项手动调节**：曝光、对比度、饱和度、色温、色调、高光、阴影、胶片感、褪色、颗粒、暗角、锐化、黑白、磨皮、滤镜强度
- **自定义预设**：一键保存当前调色（最多 6 组，长按图标删除）
- 长按「对比」秒看原图

### 运镜与视频
- **15 个运镜模板**：推近 / 拉远 / 左右移 / 升降 / 旋转 / 环绕 / 摇摆 / 甩镜 / 手持感 / 希区柯克变焦 / 脉冲 / 漂浮 …
- **6 个一键出片模板**：滤镜 + 多段运镜组合，选一个直接录出成片（8–20 秒）
- **照片 / 视频双模式**：视频模式按快门开始/停止，最长 5 分钟，REC 实时显示时长
- 视频模式下运镜**往返循环**，预览所见即所得；支持麦克风收音

### 拍摄辅助
- **构图提示**：实时检测主体位置、三分交点、地平线倾角，给出「往哪移、回正几度」的建议 + 0–99 构图评分
- **AI 场景识别**：本地分析画面亮度分布与色相构成，识别夜景 / 美食 / 人像 / 风景 / 逆光 / 雪景，并对当前模板做**动态微调**
- 九宫格网格、水平仪、画幅比例（原始 / 1:1 / 4:3 / 16:9）、日期水印
- 定时 3/10 秒、连拍 10 张

### 进阶
- **光轨（长曝光）**：车流、星轨，强度可调，按快门直接出片
- **延时摄影**：间隔 1/2/5/10 秒，30 fps 合成
- **专业参数**：变焦、对焦距离、曝光补偿、色温、ISO、亮度、对比度、饱和度、锐度、补光灯——滑杆按设备 `getCapabilities()` 实际支持的项自动生成

## 技术实现

**一套源文件，两个运行环境。** `lenskit/index.html` 是唯一主源文件（约 2000 行，无依赖、无构建步骤）。

```
camera stream / image
        │
        ▼
   WebGL 1.0  单 pass 片元着色器
   sharpen → smooth → exposure → 白平衡 → 高光/阴影 → 对比
   → 胶片曲线 → 褪色 → 饱和 → 黑白 → 分离色调 → 暗角 → 颗粒
        │  u_scale / u_off / u_rot   （运镜 = uv 变换）
        │  u_cropS / u_cropO         （画幅裁剪）
        ▼
   canvas → captureStream / toBlob → MediaRecorder / 保存
```

- **调色全部在 GPU**：单次全屏 pass，所有参数都是 uniform，改参数即改下一帧
- **运镜 = 视图变换**：每个模板是一个 `f(e) → {scale, offset, rotation}` 函数，录制时按时间轴求值，因此任意模板都能实时预览
- **长曝光不需要 FBO**：`preserveDrawingBuffer` + alpha 混合累积，片元着色器输出 `u_alpha` 控制每帧权重
- **延时**：间隔存 JPEG Blob（不存 Bitmap，省内存），合成时 30 fps 按需解码 + 预取
- **AI / 构图提示不使用神经网络**：把画面缩到 64×48 / 120×90，算 11 个统计量（亮度分位数、通道均值、饱和度、肤色/绿植/天空占比），再用线性打分判场景、用兴趣度图找主体。无模型文件、零网络请求、每一步可解释

### Android 端

WebView 里 `getUserMedia` 需要 secure context，`file://` 不满足，所以：

1. `LocalServer` 在 `127.0.0.1` 随机端口起一个 `ServerSocket`，只服务 `assets/index.html`
2. WebView 加载 `http://127.0.0.1:<port>/`——localhost 在 Blink 里算 secure context
3. 保存文件走 `JavascriptInterface` 桥：base64 **分片**传输（每片 256 KB，避开 binder 大小限制）→ Java 侧 `MediaStore` 写入相册 `Pictures/LensKit`、`Movies/LensKit`

## 构建 APK

不需要 Gradle，直接用 Android SDK 命令行工具链：`aapt2` → `javac` → `d8` → `zipalign` → `apksigner`。

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # 需 build-tools 34.0.0 + platforms/android-34
export JAVA_HOME=/path/to/jdk17
cd lenskit-apk && bash build.sh                # 产物：lenskit-apk/LensKit.apk
```

依赖：JDK 17、Android SDK、Python 3（`tools/pack.py` 负责把 `classes.dex` 压进 APK 并保留各条目的压缩方式）。

可选变量 `LENSKIT_BUILD` 指定中间产物目录（默认 `C:/Users/Public/lenskit_build`）。

## 目录结构

```
lenskit/index.html          # 主源文件：网页版 = APK 资产 = 唯一真相
lenskit-apk/
  app/AndroidManifest.xml   # 权限：CAMERA / RECORD_AUDIO / INTERNET
  app/java/.../MainActivity.java   # WebView + 保存桥
  app/java/.../LocalServer.java    # 本地 HTTP 服务（secure context）
  app/assets/index.html     # 构建时由主源文件自动同步
  app/res/                  # 图标、字符串
  build.sh                  # 一键构建
  tools/                    # pack.py（dex 合并）、gen-icon.js（图标生成）
docs/                       # GitHub Pages 站点：下载页 + 网页版 + APK
```

## 已知限制

诚实地说明这个方案做不到的事——它们需要相机底层权限或服务端能力：

- **慢动作**：WebView 的 `getUserMedia` 只给 30 fps，无法高帧率拍摄
- **RAW / 全景 / Live Photo**：需要 Camera2 / CameraX 原生管线，WebView 拿不到
- **深度网络场景识别、人物追焦**：当前用统计特征代替，模型推理（ONNX/TF.js）会让体积从 53 KB 涨到十几 MB
- **社区、云同步、会员体系**：这是服务端功能

## 隐私

无统计、无上报、无第三方 SDK。唯一的网络权限用于本机内部通信（`127.0.0.1`）。照片和视频只写入本机相册，不会离开设备。

## License

[MIT](LICENSE) © 2026 李佳航
