# PocketOps Server Integration Spec

## 1. 目标与范围

本文档描述 `PocketOps` 客户端当前需要服务端提供的两类能力：

1. 启动阶段的远程同步
   - 拉取本次同步版本信息
   - 获取需要下载的核心资源清单
   - 校验资源大小、哈希和落盘路径，并保留版本用于展示和追踪
2. 诊断阶段的资料获取
   - 根据设备、症状、工单等上下文返回可下载资料
   - 支持 PDF、ZIP、JSON 等附件类型

本文档不覆盖本地 NPU 推理服务本身。`Genie` 模型目录、`127.0.0.1:8910` 等本机推理链路仍由客户端本地管理。

## 2. 客户端当前流程

### 启动阶段

客户端会：

1. 请求远程 `manifest`
2. 从 `resources` 中选择 `knowledge_graph`，没有时选择第一个
   `requiredAtBoot=true` 的资源
3. 如果本地已有文件且 `sha256` 匹配，直接复用
4. 否则下载资源并校验大小和 `sha256`
5. 对 `requiredAtBoot=true` 的资源，完成后再继续主流程；远程同步失败时，
   客户端会先尝试使用上次缓存，再回退到 APK 内置知识图谱

### 诊断阶段

客户端会：

1. 生成诊断结果
2. 在工单页或结果页请求 `materials`
3. 按需下载 PDF、SOP 包、历史工单附件、结构化 JSON 等资料

## 3. 服务端职责边界

### 需要负责

- 资源版本发布
- 启动同步清单生成
- 资料查询接口
- 文件下载地址分发
- 文件大小与哈希计算
- 下载鉴权与访问控制

### 不需要负责

- 本机模型加载
- 本机推理 HTTP 服务
- 客户端 UI 渲染

## 4. 鉴权建议

建议把业务接口访问和文件下载拆开：

1. 业务接口使用 `Authorization: Bearer <token>`
2. 接口返回短时效 `downloadUrl`
3. 客户端直接使用签名地址下载文件

这样做的好处：

- 文件流量不压在业务服务上
- 方便接 CDN / 对象存储
- 鉴权逻辑集中在业务接口层
- 签名 URL 可设置过期时间

如果短期内还没有签名 URL，也可以退一步：

- 业务接口返回受保护的下载路径
- 客户端在下载请求中继续带 `Authorization` 头

推荐请求头：

```http
Authorization: Bearer <token>
X-App-Version: 4.8
X-Device-Id: <uuid>
X-Tenant-Id: <tenantId>
X-Site-Id: <siteId>
```

## 5. V1 最小可交付范围

建议服务端第一期至少交付：

1. `GET /api/pocketops/bootstrap/manifest`
2. `knowledge_graph.json` 真实远程下载
3. `POST /api/pocketops/materials/query`
4. 文件下载支持 `HEAD` 与 `Range`

做到这四项后，客户端即可先跑通：

- 启动真同步
- 知识图谱远程更新
- 诊断后资料真下载

## 6. 接口一：启动同步清单

### 6.1 定义

```http
GET /api/pocketops/bootstrap/manifest?tenantId=t1&siteId=s1&appVersion=4.8
```

### 6.2 作用

返回本次同步的：

- 版本号
- 启动页摘要
- 启动时展示的同步步骤
- 需要实际下载的资源列表

### 6.3 当前字段结构

```json
{
  "syncVersion": "2026-05-06.demo.1",
  "minSupportedAppVersion": "4.8",
  "generatedAt": "2026-05-06T08:00:00Z",
  "summary": {
    "equipmentCount": 32,
    "faultCaseCount": 286,
    "graphNodeCount": 1247,
    "graphEdgeCount": 3892,
    "sopCount": 168,
    "partCount": 523,
    "workOrderCount": 2156,
    "personnelCount": 48
  },
  "displaySteps": [
    {
      "key": "equipment",
      "title": "Sync equipment records",
      "detail": "32 devices across 6 workshops"
    }
  ],
  "resources": [
    {
      "id": "knowledge_graph",
      "name": "Diagnosis knowledge graph",
      "category": "core",
      "requiredAtBoot": true,
      "version": "2026-05-06.demo.1",
      "sizeBytes": 238123,
      "sha256": "...",
      "mimeType": "application/json",
      "downloadUrl": "https://.../files/core/knowledge_graph.json",
      "localPath": "core/knowledge_graph.json",
      "zip": false,
      "unzipDir": ""
    }
  ]
}
```

### 6.4 字段约定

- `syncVersion`
  - 资源快照版本
  - 同一批资源建议共用同一个版本号
- `minSupportedAppVersion`
  - 客户端版本过低时可拒绝同步
- `summary`
  - 启动页摘要展示用
- `displaySteps`
  - 启动页逐条展示的同步进度文案
- `resources`
  - 客户端真正要下载的资源清单
- `resources[].version`
  - 资源版本元数据，用于展示、审计和服务端排查；当前客户端是否复用文件
    以 `sha256` 为准
- `resources[].sizeBytes` / `resources[].sha256`
  - 下载后的完整性校验依据
- `requiredAtBoot`
  - `true` 表示主流程前必须完成下载
- `localPath`
  - 服务端建议的相对缓存路径
- `zip`
  - 标记是否为压缩包
- `unzipDir`
  - 压缩包解压后的目标相对目录

### 6.5 状态码建议

- `200` 成功
- `400` 参数错误
- `401` token 失效
- `403` 无租户或站点权限
- `426` 客户端版本过低
- `500` 服务异常

## 7. 接口二：诊断资料列表

### 7.1 定义

```http
POST /api/pocketops/materials/query
Content-Type: application/json
```

### 7.2 作用

返回和本次诊断结果相关的附件资料，例如：

- 维修 SOP
- PDF 手册
- 结构化 JSON 报告
- 历史工单附件压缩包

### 7.3 请求体建议

```json
{
  "tenantId": "t1",
  "siteId": "s1",
  "equipmentId": "eq_001",
  "symptomId": "sym_001",
  "workOrderIds": ["wo_1024", "wo_2048"],
  "keywords": ["液压泵", "举升缓慢"]
}
```

### 7.4 当前返回结构

```json
{
  "materials": [
    {
      "id": "mat_demo_sop",
      "title": "Hydraulic pump SOP",
      "type": "pdf",
      "category": "sop",
      "sizeBytes": 12345,
      "sha256": "...",
      "mimeType": "application/pdf",
      "version": "v1",
      "previewable": true,
      "shareable": true,
      "downloadUrl": "https://.../files/materials/hydraulic_pump_sop.pdf",
      "thumbnailUrl": "",
      "linkedEntities": {
        "equipmentIds": ["eq_001"],
        "symptomIds": ["sym_001"],
        "workOrderIds": ["wo_1024", "wo_2048"]
      }
    }
  ]
}
```

### 7.5 字段约定

- `type`
  - 建议取值：`pdf`、`zip`、`json`、`image`、`video`
- `category`
  - 按业务语义分类，如 `sop`、`report`、`manual`、`work_order_attachment`
- `previewable`
  - 是否可在客户端内预览
- `shareable`
  - 是否允许分享
- `linkedEntities`
  - 当前资料关联到的设备、症状、工单集合

## 8. 文件下载要求

### 8.1 路径形式

建议最终下载地址走对象存储或 CDN，例如：

- `/files/core/knowledge_graph.json`
- `/files/core/work_orders.json`
- `/files/docs/sop_docs.zip`
- `/files/materials/hydraulic_pump_sop.pdf`

### 8.2 HTTP 能力

建议支持：

- `GET`
- `HEAD`
- `Range`
- `ETag`
- `Content-Length`
- `Content-Type`
- `Cache-Control`

### 8.3 断点续传

如果请求头里带：

```http
Range: bytes=0-1023
```

服务端应返回：

- `206 Partial Content`
- `Content-Range: bytes 0-1023/<full-size>`
- `Accept-Ranges: bytes`

如果 range 不合法，应返回：

- `416 Requested Range Not Satisfiable`
- `Content-Range: bytes */<full-size>`

## 9. 与当前 demo mock server 的关系

`demo/mock_pocketops_server.py` 当前已经把这份契约中的核心字段跑通了一版演示实现：

- `manifest` 和 `materials` 结构与本文档保持一致
- `knowledge_graph.json` 使用真实 Android 资产文件
- PDF / ZIP / JSON 资料由 mock server 现场生成
- 文件下载支持 `HEAD` 和 `Range`

但它是演示服务，不是生产实现：

- 不做真实鉴权
- 不接数据库
- 不接对象存储
- 统计数据与资料内容大多是样例值

## 10. 实施建议

- 先把 `manifest` 与文件下载链路做稳定，再扩展更多资料类型
- 所有资源都给出可追踪版本号和哈希
- 旧版本资源保留一段时间，避免客户端升级窗口内出现 `404`
- 如果客户端字段变更，优先同步更新：
  - 本文档
  - `demo/mock_pocketops_server.py`
  - `demo/README.md`
