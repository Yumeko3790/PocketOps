# PocketOps

PocketOps 是一个源自 Google AI Edge Gallery、面向工业车辆诊断深度定制的 Android 应用。

当前仓库的默认主线不是上游 Gallery 的通用展示壳，而是 `PocketOps v4.8`：应用会在设备侧检查并按需拉起本地 Genie NPU HTTP 推理服务，结合本地 GraphRAG 知识图谱，为工业车辆维保场景提供文字、图片和视频诊断能力。

当前 Android 主线只保留 PocketOps 需要的代码与资源；旧 Gallery 壳、技能系统、模型管理 allowlist、示例任务和历史实验资产已从源码与打包资源中移除。

## 当前主线速览

- 产品名称：`PocketOps`
- `applicationId`：`com.pocketops.app`
- 默认启动 Activity：`com.pocketops.app.PocketOpsActivity`
- 内嵌前台推理服务：`com.pocketops.app.GenieHttpService`
- Android `namespace`：`com.pocketops.app`
- `Application`：无自定义 Application class
- `minSdk` / `targetSdk` / `compileSdk`：`31` / `35` / `35`
- 当前构建版本：`4.8 (versionCode 48)`
- 目标 ABI：`arm64-v8a`
- 当前本机推理端口：`127.0.0.1:8910`

需要特别注意的是：`MainActivity`、Gallery 导航壳、技能系统、模型下载流程等上游模块已清理，不再作为可编译源码保留。

## 核心能力

- 文字诊断
  - 先走本地 GraphRAG；命中时直接产出结构化诊断卡片
  - 未完整命中时，拼接部分知识图谱上下文后走本机 HTTP LLM 推理
- 图片诊断
  - 支持相册导入和拍照输入
  - 通过 `POST /v1/chat/completions` 调用本机 VLM
- 视频诊断
  - 对设备视频抽取关键帧，拼接为 contact sheet 后统一送入 VLM 分析
  - 空文本上传视频时默认进入车辆环车点检模式，输出完整点检报告
  - 点检视频会抽取更多关键帧，并重点保留末尾仪表盘画面；报告包含点检结论、仪表盘识别汇总、故障码汇总、仪表盘异常判断、反光镜检查、环车外观风险、处理建议和需补拍内容
- 结构化维修建议
  - 输出根因、备件、维修步骤、推荐维修人员和相似工单
- 蓝牙故障码辅助
  - 支持通过蓝牙诊断弹窗导入示例故障码与参数，再次进入诊断链路
- 工单与历史
  - 支持工单预览、相似工单展开、诊断历史回看以及 PDF 导出与分享

## 运行链路

### 1. 启动阶段

应用启动后，`PocketOpsActivity` 会按以下顺序工作：

1. 读取 `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`
2. 展示一段“同步 + NPU 装载”的前置界面
3. 探测 `GET http://127.0.0.1:8910/v1/models`
4. 若已有兼容服务且模型已 ready，则直接复用
5. 若模型未就绪且 Android 11+ 尚未授予 `/sdcard/GenieModels` 的 all-files access，则提示授权
6. 否则拉起 `GenieHttpService`
7. 轮询等待本机推理服务可用

当前代码中的最长等待窗口约为 180 秒。就绪判定依赖 `/v1/models` 返回的模型元数据，而不是只看端口能否连通。

### 2. 内嵌推理服务

`GenieHttpService` 当前负责：

- 扫描 `/sdcard/GenieModels`
- 选择首个包含 `config.json` 的模型目录
- 设置 `ADSP_LIBRARY_PATH` 和 `LD_LIBRARY_PATH`
- 通过 `com.example.genieapiservice.MyNativeLib` 启动 native HTTP 服务
- 以前台服务方式常驻运行，供 App 后续复用
- native 服务异常退出后清理内部状态，允许 App 直接重试拉起

### 3. GraphRAG 与多模态诊断

当前默认知识图谱路径位于：

- `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`
- `Android/src/app/src/main/java/com/pocketops/app/MaintenanceKnowledgeGraph.kt`

图谱层主要负责：

- 设备识别
- 故障症状匹配
- 最多 4 跳图遍历
- 根因、备件、步骤、人员、工单关联
- 为 LLM fallback 提供部分上下文

多模态链路当前分为三种：

- 纯文字：GraphRAG 命中优先，否则进入 LLM
- 图片：图片转 base64 PNG 后走本机 VLM
- 视频：先抽帧再组合成关键帧拼图，最后走本机 VLM
  - 车辆点检视频：按环车一周场景抽帧，输出完整点检报告；仪表盘多页故障码会合并识别，反光镜、环车外观风险和补拍内容一并进入结论

## 关键运行依赖

当前 PocketOps 主线依赖以下条件同时成立：

- APK 内已打包当前 native 推理相关动态库
- 设备存在可读的模型目录：`/sdcard/GenieModels/<model-dir>/config.json`
- Android 11+ 已向应用授予 all-files access，以便扫描共享存储模型目录
- `127.0.0.1:8910` 上存在可复用或可被应用拉起的本机 HTTP 推理服务

如果缺少其中任意一项，当前主线诊断流程都会不完整或无法就绪。

## HTTP 协议面

当前 PocketOps 默认依赖的本机接口是：

- `GET http://127.0.0.1:8910/v1/models`
- `POST http://127.0.0.1:8910/v1/chat/completions`
- `POST http://127.0.0.1:8910/clear`

说明：

- 每次文字、图片或视频诊断请求前都会先调用 `/clear` 清理服务侧会话状态
- 文本推理按流式响应消费
- 图片与视频诊断走非流式多模态请求
- App 负责 prompt 组装、GraphRAG 上下文拼接、结果解析和 UI 渲染
- 本机服务负责模型加载和推理执行

## 模型命名注意事项

当前多模态推理请求真正使用的运行时模型名在 `PocketOpsActivity` 中维护：

- `qwen2.5vl-3b-8850-2.42`

旧 Gallery 的 `model_allowlist.json` 与 `model_allowlists/` 快照已经删除。更换部署模型或修改本机服务契约时，以 `PocketOpsActivity`、`GenieHttpService` 和本机 HTTP 服务返回的 `/v1/models` 元数据为准。

## 目录说明

- `Android/src/`
  - 实际 Android Gradle 工程根目录
- `Android/src/app/src/main/java/com/pocketops/app/`
  - 当前生产主线入口、Compose UI、GraphRAG 逻辑和内嵌推理服务
- `Android/src/app/src/main/assets/maintenance/`
  - 本地知识图谱资源；旧图谱可视化 HTML/JS 已删除

## 本地构建与运行

### 前提

- Android Studio
- Android 12+ `arm64-v8a` 设备
- 设备上已准备模型目录：`/sdcard/GenieModels/<model-dir>/config.json`
- 首次运行时可授予 all-files access

### 基本步骤

1. 使用 Android Studio 打开 `Android/src`
2. Sync Gradle 并构建应用
3. 安装到 Android 12+ 真机
4. 将模型目录准备到 `/sdcard/GenieModels`
5. 启动 PocketOps 并登录
6. 如有提示，先授予 `/sdcard/GenieModels` 的 all-files access
7. 等待同步页和 NPU 装载流程完成
8. 确认 `GET /v1/models` 返回已加载模型后进入主界面

命令行构建可在 `Android/src` 下执行：

```shell
# Windows
.\gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

## 当前主线与已清理模块边界

当前应该优先按“现状”理解的代码路径是：

- `PocketOpsActivity`
- `GenieHttpService`
- `MaintenanceKnowledgeGraph`
- `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`

当前已删除默认主路径不再使用的内容，包括：

- `MainActivity` 与 Gallery 导航壳
- `MaintenanceTask` / `MaintenanceScreen`
- `GenieNative.kt`
- `GenieModelHelper.kt`
- `OnnxVIT.kt`
- `skills/` 下的 Agent Skills 文档和示例
- `model_allowlist.json` 与 `model_allowlists/`
- `Android/src/app/src/main/proto/`
- `Android/src/app/src/main/cpp/`
- `Android/src/app/src/main/assets/skills/`
- `Android/src/app/src/main/assets/tinygarden/`
- HuggingFace 登录、模型管理与下载流程

当前可编译代码应围绕 `PocketOpsActivity + GenieHttpService` 主线维护。

## 说明文件索引

- [`docs/project/DEVELOPMENT.md`](docs/project/DEVELOPMENT.md)
  - 开发入口、代码归属、调试要点和模型/服务改动清单
- [`Android/README.md`](Android/README.md)
  - Android 目录结构、构建入口和运行前提
- [`docs/project/POCKETOPS_SERVER_INTEGRATION_SPEC.md`](docs/project/POCKETOPS_SERVER_INTEGRATION_SPEC.md)
  - 电脑侧服务需要提供的登录、启动同步、资料查询和下载接口
- [`demo/README.md`](demo/README.md)
  - 本地 demo mock server 的启动参数、账号和验证方式
- [`docs/project/Bug_Reporting_Guide.md`](docs/project/Bug_Reporting_Guide.md)
  - Android 设备问题采集方式，含 bugreport 与 logcat 建议
- [`docs/final/FINAL_PPT_CONTENT.md`](docs/final/FINAL_PPT_CONTENT.md)
  - 决赛答辩 PPT / codesign 内容资料
- [`docs/final/DEMO_VIDEO_SCRIPT.md`](docs/final/DEMO_VIDEO_SCRIPT.md)
  - 演示视频拍摄脚本

外层工作目录中的中文架构摘要属于本地笔记，不作为仓库内正式说明文件维护。

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
