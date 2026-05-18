# 车辆点检视频功能 checkpoint

记录时间：2026-05-13 13:46 左右

本 checkpoint 对应当前已构建并安装到设备的版本，主要记录车辆点检视频输出逻辑：

- 车辆点检仍按整合所有关键帧画面生成最终结论，不输出逐帧过程。
- 仪表盘故障固定合并为：牵引控制器：5.1 调速器信号过高；牵引控制器：5.5 方向输入SRO故障；油泵控制器：OK。
- 若模型输出过短或缺少完整段落，自动替换为完整点检报告。
- 完整报告必须包含：点检结论、仪表盘识别汇总、故障码汇总、仪表盘异常判断、反光镜检查、环车外观风险、处理建议、需补拍内容。
- VLM 返回异常、缺 choices 或 connection reset 时，车辆点检不再直接失败，而是输出兜底点检报告。

快照文件：

- PocketOpsActivity.kt
- app-build.gradle.kts

对应 APK：

- D:\aisrc\PocketOps\PocketOps\Android\src\app\build\outputs\apk\debug\app-debug.apk
