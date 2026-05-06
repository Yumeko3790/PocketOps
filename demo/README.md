# PocketOps Demo Mock Server

这个目录提供一个电脑侧的 PocketOps 演示服务，用来给 Android 端演示远程同步和资料下载流程。

## 它做什么

当前脚本 `mock_pocketops_server.py` 会：

- 返回 `GET /api/pocketops/bootstrap/manifest`
- 返回 `POST /api/pocketops/materials/query`
- 通过 `/files/...` 提供静态资源下载
- 支持 `GET`、`HEAD` 和 `Range`，可用于验证断点续传
- 优先读取真实仓库里的 `knowledge_graph.json`
- 为 demo 现场生成示例 PDF、ZIP、JSON 文件

## 目录文件

| 文件 | 作用 |
| --- | --- |
| `mock_pocketops_server.py` | 主服务脚本 |
| `README.md` | 本说明文档 |

## 前置条件

- Python 3.10+
- 本机能访问 `knowledge_graph.json`
- 如果你想给手机走 `adb reverse`，需要本机已安装 `adb`

## 运行

最简单的方式：

```bash
python demo/mock_pocketops_server.py
```

指定 host 和 port：

```bash
python demo/mock_pocketops_server.py --host 0.0.0.0 --port 8080
```

显式指定 knowledge graph 路径：

```bash
python demo/mock_pocketops_server.py \
  --knowledge-graph D:/aisrc/PocketOps/Android/src/app/src/main/assets/maintenance/knowledge_graph.json
```

覆盖版本信息：

```bash
python demo/mock_pocketops_server.py \
  --sync-version 2026-05-06.demo.2 \
  --min-supported-app-version 1.0.11
```

## 自动搜索 knowledge graph 的逻辑

如果没有传 `--knowledge-graph`，脚本会按顺序尝试：

1. 当前仓库内的 `Android/src/app/src/main/assets/maintenance/knowledge_graph.json`
2. 上一级目录里的同路径
3. 上一级目录下所有兄弟项目里的同路径
4. 兄弟项目下一层子目录里的同路径

这意味着你不需要把 Android 仓库固定命名成某个 release 名称，脚本会尽量自动探测。

## 暴露的接口

### 1. 启动同步清单

```http
GET /api/pocketops/bootstrap/manifest
```

返回字段包括：

- `syncVersion`
- `summary`
- `displaySteps`
- `resources`

### 2. 诊断资料列表

```http
POST /api/pocketops/materials/query
Content-Type: application/json
```

请求体示例：

```json
{
  "equipmentId": "eq_001",
  "symptomId": "sym_001",
  "workOrderIds": ["wo_1024", "wo_2048"]
}
```

### 3. 文件下载

```http
GET  /files/core/knowledge_graph.json
HEAD /files/core/knowledge_graph.json
GET  /files/materials/hydraulic_pump_sop.pdf
Range: bytes=0-1023
```

## 返回的示例文件

当前 mock server 会提供这些下载资源：

- `knowledge_graph.json`
- `work_orders.json`
- `sop_docs.zip`
- `hydraulic_pump_sop.pdf`
- `history_attachments.zip`
- `diagnostic_summary.json`

其中：

- `knowledge_graph.json` 尽量来自真实 Android 资产
- 其他文件由脚本在启动时生成

## 用 curl 快速验证

### 1. 看 manifest

```bash
curl http://127.0.0.1:8080/api/pocketops/bootstrap/manifest
```

### 2. 查资料列表

```bash
curl -X POST http://127.0.0.1:8080/api/pocketops/materials/query \
  -H "Content-Type: application/json" \
  -d '{"equipmentId":"eq_001","symptomId":"sym_001","workOrderIds":["wo_1024"]}'
```

### 3. 验证 range 下载

```bash
curl http://127.0.0.1:8080/files/core/knowledge_graph.json \
  -H "Range: bytes=0-1023" \
  -o partial.json
```

## 手机接入方式

### 方式一：同一 Wi-Fi

脚本启动后会打印可用 URL，例如：

```text
http://192.168.1.8:8080
```

把这个地址填到 Android demo 的 server URL 即可。

### 方式二：USB + adb reverse

```bash
adb reverse tcp:8080 tcp:8080
```

然后把 Android 端地址设置成：

```text
http://127.0.0.1:8080
```

## Android demo 流程建议

1. 打开登录页并配置 demo server URL
2. 登录
3. 客户端拉取远程 `manifest`
4. 下载 `knowledge_graph.json`
5. 生成诊断结果
6. 打开工单页并点击获取资料
7. 触发真实文件下载

## 当前实现限制

- 没有真实鉴权
- 没有数据库
- 没有对象存储/CDN
- `summary` 里的统计大部分是演示值
- `materials` 结果主要用于验证链路，不代表真实业务召回逻辑

## 建议的维护方式

如果你修改了 `mock_pocketops_server.py` 的返回字段，请同步更新：

- `../POCKETOPS_SERVER_INTEGRATION_SPEC.md`
- 本文档
