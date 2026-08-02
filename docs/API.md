# NMSCI API 接口文档

消费意愿数值化衡量系统（NMSCI）的 HTTP API 参考。本文档描述对外暴露的 REST 端点：请求方法、路径、参数、响应结构与错误约定。

- **链上字节协议**（各消息的字节布局、验证项、区块结构）见 [PROTOCOL.md](../PROTOCOL.md)。
- **运行、构建、部署**见 [README.md](../README.md)。
- 当前共 **13 控制器 / 37 个业务端点**（31 个 GET + 6 个二进制 POST）。Actuator 与静态文件入口不计入该数量。

> **Base URL**：默认 `http://localhost:8080`（见 README「本地运行」）。
> **鉴权**：当前所有端点均无鉴权与限流。

## 目录

- [1. 通用约定](#1-通用约定)
  - [1.1 响应信封](#11-响应信封)
  - [1.2 状态码](#12-状态码)
  - [1.3 分页](#13-分页)
  - [1.4 字段序列化约定](#14-字段序列化约定)
  - [1.5 时间戳与时间区间](#15-时间戳与时间区间)
  - [1.6 二进制写入（POST）](#16-二进制写入post)
  - [1.7 id 与 pubkey 不可混用](#17-id-与-pubkey-不可混用)
  - [1.8 常见参数错误](#18-常见参数错误)
- [2. 区块 `/blocks`](#2-区块-blocks)
- [3. 系统与验证 `/system`、`/verify`](#3-系统与验证-systemverify)
- [4. 元数据 `/metadata`](#4-元数据-metadata)
- [5. 流转节点 `/flow-nodes`](#5-流转节点-flow-nodes)
- [6. 消费链 `/consume-chains`](#6-消费链-consume-chains)
- [7. 回流率 `/returning-flow-rates`](#7-回流率-returning-flow-rates)
- [8. 协议消息（写入与查询）](#8-协议消息写入与查询)
- [9. 静态资源](#9-静态资源)
- [10. 数据结构附录](#10-数据结构附录)

---

## 1. 通用约定

### 1.1 响应信封

除静态资源外，所有端点返回统一信封 `ResponseResult<T>`：

```json
{ "code": 200, "message": "Success", "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 响应体业务状态码；成功为 `200`，除二进制写入成功外通常与 HTTP 状态码一致 |
| `message` | String | 成功为 `"Success"`；失败时为具体错误消息或通用错误消息 |
| `data` | T | 成功时为业务数据；失败时固定为 `null` |

6 个二进制写入端点成功时使用 HTTP `201 Created`，但响应体仍为 `code: 200` / `message: "Success"`。错误响应统一将详情放在 `message`，`data` 为 `null`。

### 1.2 状态码

| HTTP 状态 | 响应体 `code` | `message` | 何时出现 |
|---|---:|---|---|
| 200 OK | 200 | Success | GET/查询类请求成功 |
| 201 Created | 200 | Success | 6 个二进制 POST 写入成功 |
| 400 Bad Request | 400 | Bad Request 或具体错误消息 | 参数非法：id 与 pubkey 混用、必填参数缺失、二进制体长度不符、协议校验失败等 |
| 404 Not Found | 404 | Not Found 或具体错误消息 | 按 `{id}`/高度/哈希/pubkey 查询的资源或状态不存在 |
| 409 Conflict | 409 | Conflict 或具体错误消息 | 资源状态冲突（如重复提交） |
| 500 Internal Server Error | 500 | Internal Server Error 或 `服务器内部错误` | 服务端异常；未被控制器请求边界捕获的运行时异常不会向客户端暴露内部细节 |
| 503 Service Unavailable | 503 | Service Unavailable | 服务不可用 |

> `401 Unauthorized` / `403 Forbidden` 已在 `ResponseCode` 中定义，但当前无鉴权机制，业务不会触发。

### 1.3 分页

列表/检索端点接受分页参数，`data` 为 `SliceResponseDTO<T>`（基于 Spring Data `Slice`，**不返回总条数**，靠 `hasNext` 翻页）。

| 参数 | 类型 | 默认 | 约束 |
|---|---|---|---|
| `page` | int | `0` | ≥ 0，从 0 起 |
| `size` | int | `50` | 1 ~ `200`，超出报 400 |

```json
{
  "code": 200, "message": "Success",
  "data": {
    "content": [ ],
    "page": 0,
    "size": 50,
    "numberOfElements": 50,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

**默认排序**：消息列表按中心确认时间戳 `confirmTimestamp` 倒序、`id` 倒序兜底；流转节点注册信息无确认时间戳，按 `id` 升序；消费链列表按 `tailMountTimestamp` 倒序、`id` 倒序；消费链边查询按每条链去重后，再按 `relatedTransactionMountTimestamp` 倒序、`id` 倒序。

### 1.4 字段序列化约定

实体/DTO 中的二进制与引用字段经自定义 Jackson 序列化器输出，JSON 形态与 Java 类型不同：

| Java 类型 | JSON 形态 | 序列化器 | 示例 |
|---|---|---|---|
| `byte[]`（公钥、签名、哈希等） | hex 字符串（**无 `0x` 前缀**，小写） | `BytesToHexSerializer` | `"02a1b2…"` |
| `Integer`（难度 nbits） | hex 字符串（含 `0x`） | `IntToHexSerializer` | `"0x1d00ffff"` |
| 实体引用（如 `ConsumeChain.start`、`ConsumeChainEdge.source`） | 仅 UUID 字符串 | `IdentifiableToStringSerializer` | `"7c9e…-…"` |
| `rawBytes`（消息原始字节缓存） | **不输出**（`@JsonIgnore`） | — | 与 `BlockInfo.rawBytes` 一致，不计入对外契约；避免泄露原始字节并使列表/详情响应体翻倍。 |
| `UUID`（如消息 `id`、`mountedTransactionRecordId`） | 标准 UUID 字符串 | 默认 | `"7c9e6679-…"` |

### 1.5 时间戳与时间区间

- 实体内 `confirmTimestamp`、区块 `timestamp`/`maxMsgTimestamp` 均为**微秒级** Unix 时间戳（UTC，见 `DateUtil.getCurrentMicros`）。
- 检索端点的 `startTime` / `endTime` 过滤的是 `confirmTimestamp`，因此也应传**微秒**值。区间为闭区间。
- 例外：`/system/status` 的 `blockIntervalMs` 是**毫秒**（区块生成间隔，固定 600000 = 10 分钟）。

### 1.6 二进制写入（POST）

6 类协议消息的写入端点接收**原始字节**（非 JSON）：

- **Content-Type**：`application/octet-stream`
- **请求体**：严格按 [PROTOCOL.md](../PROTOCOL.md) 对应章节拼装的字节；长度必须**精确等于**该类型的入站字节数（`@ByteArraySize` 校验，不符报 400）。
- 入站字节数 = 中心签名之前的字节数（见 [§4 元数据](#4-元数据-metadata) `inboundSize`）：

  | 端点 | 信息类型 | 入站字节数 | 落库字节数 |
  |---|---|---|---|
  | `POST /flow-node-registrations` | 0x0000 | 123 | 123 |
  | `POST /central-pubkey-empowerments` | 0x0001 | 148 | 220 |
  | `POST /central-pubkey-locks` | 0x0002 | 115 | 187 |
  | `POST /flow-node-locks` | 0x0003 | 148 | 220 |
  | `POST /transaction-records` | 0x0004 | 263 | 335 |
  | `POST /transaction-mounts` | 0x0005 | 269 | 341 |

- **响应**：校验通过后，中心补签名与确认时间戳并落库，HTTP 状态为 `201 Created`，响应体为 `ResponseResult.success(完整实体)`（`code` 仍为 `200`；注册信息无中心签名，入站=落库）。

```bash
curl -X POST http://localhost:8080/transaction-records \
  -H 'Content-Type: application/octet-stream' \
  --data-binary @transaction-record.bin
```

> **如何构造 `.bin`（端到端 smoke）**：请求体是按 [PROTOCOL.md](../PROTOCOL.md) 对应章节逐字段拼接的原始字节，其中含 secp256k1 成员签名（low-S）与工作量证明随机数，**无法用纯 shell/curl 手搓**，需程序化生成：
> 1. 用 [README《配置》](../README.md) 的 `GenerateCentralKeyPairCli` 生成中心密钥对，并为流转/消费节点各生成一对密钥；
> 2. 按字节布局拼装消息体、对待签数据做 low-S 签名、对带难度目标的消息挖出满足难度的 nonce；
> 3. 按完整生命周期顺序提交：注册流转节点 → 授权中心公钥 → 交易记录 → 交易挂载。
>
> 仓库内已有可执行的参考实现，可直接照搬其字节拼装与签名/挖矿逻辑：测试支撑类 `ProtocolMessageBuilder`（`src/test/java/.../support/`）与压测脚本 `stress/FullLifecycleSimulation` 的 `BUILDER`，二者覆盖上述第 2、3 步（字节拼装 + low-S 签名 + PoW，以及注册→授权→交易→挂载的生命周期顺序），是最权威的 smoke 起点；第 1 步它们用测试夹具 `TestKeyPairs` 派生密钥，与 `GenerateCentralKeyPairCli` 等价（任一合法 secp256k1 密钥对均可）。

### 1.7 id 与 pubkey 不可混用

消费链与回流率的查询同时支持「按 id」与「按 pubkey」两套定位参数，**同一请求只能用其中一套**，混用返回 400（`"id 与 pubkey 查询参数不能混用"`）。pubkey 会在服务端解析为对应流转节点的 id 后再查询。

### 1.8 常见参数错误

参数解析失败统一返回 `ResponseResult` 错误信封，`data` 为 `null`。常见 `message` 包括：

| 场景 | HTTP 状态 | 响应体 `code` | 典型 `message` |
|---|---:|---:|---|
| 框架参数绑定/请求体长度校验失败 | 400 | 400 | `请求参数非法` |
| UUID 格式错误 | 400 | 400 | `UUID格式不正确` |
| hex 字符串包含非十六进制字符 | 400 | 400 | `十六进制字符串包含非法字符` |
| 压缩公钥过滤/查询参数长度不是 33 字节 | 400 | 400 | `公钥长度错误，必须为33字节` 或 `{参数名}不能为空或长度不为33字节` |
| id 与 pubkey 查询模式混用 | 400 | 400 | `id 与 pubkey 查询参数不能混用` |
| 签名/公钥字节非法（低 S、DER 解析、曲线点解码失败等） | 400 | 400 | 对应校验失败消息（如 `中心预签名验证失败`） |

---

## 2. 区块 `/blocks`

返回 `BlockInfo`（字段见[附录 10.1](#101-blockinfo)）。

### GET `/blocks/latest`
最新区块。无参数。响应 `ResponseResult<BlockInfo>`。创世区块产生前（区块链尚未初始化）返回 **404**（`区块链尚未初始化，暂无区块`）。

### GET `/blocks/{height}`
按区块高度查询。

| 路径参数 | 类型 | 说明 |
|---|---|---|
| `height` | long | 区块高度 |

不存在返回 404。

### GET `/blocks?hash={hash}`
按区块哈希查询。

| 查询参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `hash` | String | 是 | 区块头哈希（hex） |

哈希格式非法返回 400；格式合法但不存在返回 404。

---

## 3. 系统与验证 `/system`、`/verify`

`/system` 为运行参数与状态；`/verify` 为链完整性自检。

### GET `/system/params`
系统参数（版本、中心公钥、难度、源码包哈希、最新区块）。响应 `SystemParamsDTO`（[附录 10.9](#109-系统与运维-dto)）。

### GET `/system/status`
运行状态：最新区块高度/哈希/时间戳、未入块消息数、最早未确认时间戳、区块间隔（毫秒）、中心公钥是否冻结。响应 `SystemStatusDTO`。

### GET `/system/storage`
`.dat` 存储用量：目录、文件数、当前文件名与大小、总字节、单文件上限、当前利用率（%）。响应 `StorageStatusDTO`。

### GET `/verify/chain`
对本节点落盘的 `blk*.dat` 重新解析并**独立核验链完整性**：帧格式、哈希链衔接、各区块中心签名、默克尔根、最大时间戳，以及每条消息的工作量证明/成员签名/中心签名；可选有状态回放（注册/授权/挂载等引用与唯一性）。每个区块的中心签名均在其**自身区块头公钥**下验证，故对中心公钥轮换/版本升级后的多代链仍稳健；本端点不强制「区块头中心公钥等于本节点配置」与源码哈希一致——这类带信任锚的更严格独立核验请用离线 CLI `VerifyChainCli`。响应 `ChainVerificationSummaryDTO`（[附录 10.9](#109-系统与运维-dto)）。

| 查询参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `stateful` | boolean | `true` | 是否执行有状态回放 |

> 仅做只读核验，不修改任何数据。无需运行本服务的离线第三方核验见仓库内 `com.cooperativesolutionism.nmsci.verifier.VerifyChainCli`（退出码 0=通过 / 1=不通过 / 2=用法错误）。

### 可观测性（Actuator）
`GET /actuator/health`、`GET /actuator/info`、`GET /actuator/metrics`（`/actuator/metrics/{name}` 查单项）、`GET /actuator/prometheus`（Prometheus 抓取）。自定义业务指标（出块耗时/大小/积压、消费链分配耗时等）与公共标签 `application=nmsci` 见 [README《可观测性》](../README.md#可观测性)。

---

## 4. 元数据 `/metadata`

静态协议元数据，无参数。

### GET `/metadata/message-types`
全部消息类型。响应 `List<MsgTypeMetadataDTO>`，每项含 `size`（落库字节数）与 `inboundSize`（入站 POST 字节数）。

```json
{
  "code": 200, "message": "Success",
  "data": [
    { "code": "FlowNodeRegisterMsg", "value": 0, "hexValue": "0x0000", "size": 123, "inboundSize": 123, "sizeUnit": "字节", "name": "流转节点注册信息" },
    { "code": "TransactionRecordMsg", "value": 4, "hexValue": "0x0004", "size": 335, "inboundSize": 263, "sizeUnit": "字节", "name": "交易记录信息" }
  ]
}
```

### GET `/metadata/currency-types`
全部货币类型。响应 `List<CurrencyTypeMetadataDTO>`。

```json
{
  "code": 200, "message": "Success",
  "data": [
    { "code": "GOLD", "value": 0, "description": "黄金(微克)", "unit": "微克", "unitDescription": "1 = 1微克黄金" },
    { "code": "CNY",  "value": 1, "description": "人民币(分)", "unit": "分",   "unitDescription": "1 = 1分人民币" }
  ]
}
```

### GET `/metadata/block-format`
区块/存储格式常量。响应 `BlockFormatMetadataDTO`。

```json
{
  "code": 200, "message": "Success",
  "data": {
    "magicNumber": 1782489477, "magicNumberHex": "0x6a466d85",
    "genesisHash": "0000000000000000000000000000000000000000000000000000000000000000",
    "hashSize": 32, "messageCountFieldSize": 8,
    "blockVersion": 3, "blockHeaderSize": 229,
    "blockMaxSizeBytes": 1048576, "blockDatMaxSizeBytes": 134217728,
    "datFilePrefix": "blk", "datFileSuffix": ".dat", "datFileIndexWidth": 8,
    "sourceCodeZipPrefix": "source_code_v", "sourceCodeZipSuffix": ".zip"
  }
}
```

### GET `/metadata/difficulty`
当前注册与交易 PoW 难度。优先取最新区块的 nbits，无区块时回退配置。响应 `DifficultyMetadataDTO`，`register`/`transaction` 各含 `{ nbitsInt, nbitsHex, targetDecimal, targetHex }`。

```json
{
  "code": 200, "message": "Success",
  "data": {
    "register":    { "nbitsInt": 486604799, "nbitsHex": "0x1d00ffff", "targetDecimal": "26959535291011309493156476344723991336010898738574164086137773096960", "targetHex": "0xffff0000000000000000000000000000000000000000000000000000" },
    "transaction": { "nbitsInt": 486604799, "nbitsHex": "0x1d00ffff", "targetDecimal": "…", "targetHex": "0xffff…" }
  }
}
```
> 上例 nbits 仅为说明格式，实际值以运行实例为准。

---

## 5. 流转节点 `/flow-nodes`

### GET `/flow-nodes/{flowNodePubkey}`
按公钥查询单个流转节点的状态聚合。响应 `FlowNodeStateResponseDTO`：`{ registered, authorized, locked, currentCentralPubkeyAuthorized }`。其中 `authorized` 表示该节点**曾对任一中心公钥**授权过，`currentCentralPubkeyAuthorized` 表示已对**当前**中心公钥授权；中心公钥轮换后两者可不一致（曾授权旧中心公钥、但尚未对新的当前中心公钥授权时，前者为 `true`、后者为 `false`）。

| 路径参数 | 类型 | 说明 |
|---|---|---|
| `flowNodePubkey` | String | 流转节点公钥（hex，33 字节） |

格式非法或长度不是 33 字节返回 400。

### GET `/flow-nodes`
节点列表（分页），按布尔状态过滤。响应 `SliceResponseDTO<FlowNodeListItemDTO>`。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `registered` | Boolean | `true` | 是否已注册 |
| `authorized` | Boolean | （不过滤） | 是否曾授权过（任一）中心公钥，不限当前中心公钥 |
| `locked` | Boolean | （不过滤） | 是否已冻结 |
| `page` / `size` | int | 0 / 50 | 见 [§1.3](#13-分页) |

---

## 6. 消费链 `/consume-chains`

`ConsumeChainResponseDTO` = `{ consumeChain, consumeChainEdges }`（实体见[附录 10.3/10.4](#103-consumechain)）。

### GET `/consume-chains/{id}`
按消费链 UUID 查询单条。

| 路径参数 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 消费链 id |

### GET `/consume-chains`
消费链列表（分页）。必须选择一种查询模式：按起点/终点/途经节点过滤（id 或 pubkey 二选一，且三者必须且只能提供一个），或按挂载交易过滤。响应 `SliceResponseDTO<ConsumeChainResponseDTO>`。

| 查询参数 | 类型 | 说明 |
|---|---|---|
| `startId` / `endId` / `nodeId` | String(UUID) | 起点 / 终点 / 途经节点（id 模式） |
| `startPubkey` / `endPubkey` / `nodePubkey` | String(hex) | 起点 / 终点 / 途经节点（pubkey 模式） |
| `isLoop` | Boolean | 是否只取成环链 |
| `mountedTransactionId` | String(UUID) | 按挂载交易过滤 |
| `page` / `size` | int | 分页 |

**400**：未提供任何查询条件；`start`/`end`/`node` 在同一模式下不止一个；id 模式与 pubkey 模式混用；`mountedTransactionId` 与节点过滤参数混用。

### GET `/consume-chains/edges`
查询「流入某目标节点」的消费链边集合（用于回流分析）。`target` 必填，`source` 可选（缺省=所有流入 target 的边）。结果按 `chain` 去重：同一条消费链存在多条匹配边时，只返回该链内挂载时间最早的一条，再按挂载时间倒序分页。响应 `SliceResponseDTO<ConsumeChainEdge>`，不返回总条数。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `targetId` 或 `targetPubkey` | String | — | **必填**（按所选模式） |
| `sourceId` 或 `sourcePubkey` | String | — | 可选，指定来源节点 |
| `currencyType` | short | `1` | 货币类型 |
| `startTime` / `endTime` | long | `0` / `Long.MAX` | 微秒时间区间（过滤挂载时间） |
| `page` / `size` | int | `0` / `50` | 见 [§1.3](#13-分页)，`size` 最大 200 |

**400**：id 与 pubkey 混用；目标参数（`targetId`/`targetPubkey`）为空；分页参数非法；pubkey 格式或长度非法。
**404**：按 pubkey 查询时目标或来源流转节点不存在。

> 路由说明：字面量 `/consume-chains/edges` 优先于 `/{id}`（UUID），不会被吞。

---

## 7. 回流率 `/returning-flow-rates`

### GET `/returning-flow-rates`
计算回流率/滞留指数。响应 `ReturningFlowRateResponseDTO`，**按是否提供 source 分两种形态**：

- **提供 source + target**：A(source)→B(target) 的回流率，返回 `returningFlowRate`、`loopedAmount`、`unloopedAmount`。
- **仅 target**：B 的总成环/总滞留，返回 `targetTotalLoopedAmount`、`targetTotalUnloopedAmount`。

未涉及的字段取默认值 `0.0`。聚合口径与 `/consume-chains/edges` 一致，按 `chain` 去重后统计金额。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `targetId` 或 `targetPubkey` | String | — | **必填**（按所选模式） |
| `sourceId` 或 `sourcePubkey` | String | — | 可选；提供则计算 source→target 回流率 |
| `currencyType` | short | `1` | 货币类型 |
| `startTime` / `endTime` | long | `0` / `Long.MAX` | 微秒时间区间 |

**400**：id 与 pubkey 混用；目标参数为空；pubkey 格式或长度非法。
**404**：按 pubkey 查询时目标或来源流转节点不存在。

```json
{
  "code": 200, "message": "Success",
  "data": {
    "returningFlowRate": 0.62, "loopedAmount": 6200.0, "unloopedAmount": 3800.0,
    "targetTotalLoopedAmount": 0.0, "targetTotalUnloopedAmount": 0.0, "currencyType": 1
  }
}
```

---

## 8. 协议消息（写入与查询）

6 类消息控制器遵循统一模式：

- `POST /{资源}` — 提交原始字节（见 [§1.6](#16-二进制写入post)），成功时 HTTP `201 Created`，响应体 `code` 为 `200`，返回落库实体。
- `GET /{资源}/{id}` — 按 UUID 查询单条，不存在 404。
- `GET /{资源}` — 集合根，分页 + 可选过滤，返回 `SliceResponseDTO<实体>`。
- 冻结类额外提供 `GET /{资源}/status` — 返回 `LockedMessageResponseDTO<实体>` = `{ locked, lockedMsg }`。

| 资源 | POST 字节数 | 集合根可选过滤参数 | `status` 子资源 |
|---|---|---|---|
| `/flow-node-registrations` | 123 | `flowNodePubkey` | — |
| `/flow-node-locks` | 148 | （仅分页） | `GET /flow-node-locks/status?flowNodePubkey=`（必填） |
| `/central-pubkey-empowerments` | 148 | `flowNodePubkey` | — |
| `/central-pubkey-locks` | 115 | （仅分页） | `GET /central-pubkey-locks/status?centralPubkey=`（必填） |
| `/transaction-records` | 263 | `consumeNodePubkey`、`flowNodePubkey`、`currencyType`、`startTime`、`endTime` | — |
| `/transaction-mounts` | 269 | `consumeNodePubkey`、`flowNodePubkey`、`mountedTransactionRecordId`、`startTime`、`endTime` | — |

所有过滤参数均为可选；全空即返回全量（分页）。pubkey 为 33 字节压缩公钥 hex，时间为微秒。过滤参数中的 pubkey 格式或长度非法返回 400；`{id}` 或 `mountedTransactionRecordId` 不是合法 UUID 时返回 400。

> **中心公钥公证的轮换语义（`POST /central-pubkey-empowerments`）**：公证唯一性按 `(流转节点公钥, 中心公钥)` 组合判定。同一流转节点对**同一**中心公钥重复授权返回 **409**（`该流转节点公钥(...)已对该中心公钥授权过`）；授权目标必须是**当前配置且未冻结**的中心公钥，否则拒绝。当原中心公钥被冻结、中心公钥轮换为新值后，流转节点可对**新的当前中心公钥**再次授权——因此发生过轮换的节点会持有**多条**公证记录，`GET /central-pubkey-empowerments?flowNodePubkey=` 会返回多条（每个被授权过的中心公钥一条）。流转节点的「是否已授权当前中心公钥」可经 [§5](#5-流转节点-flow-nodes) 的 `currentCentralPubkeyAuthorized` 字段判断。

**示例：查询某流转节点的交易记录**

```
GET /transaction-records?flowNodePubkey=02a1b2...&currencyType=1&page=0&size=20
```

```json
{
  "code": 200, "message": "Success",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "msgType": 4,
        "amount": 10000,
        "currencyType": 1,
        "transactionDifficultyTarget": "0x1d00ffff",
        "nonce": 123456,
        "consumeNodePubkey": "03c4...",
        "flowNodePubkey": "02a1b2...",
        "centralPubkey": "02ff...",
        "consumeNodeSignature": "…",
        "flowNodeSignature": "…",
        "confirmTimestamp": 1718352000000000,
        "centralSignature": "…",
        "txid": "9f86d0…"
      }
    ],
    "page": 0, "size": 20, "numberOfElements": 1, "hasNext": false, "hasPrevious": false
  }
}
```

---

## 9. 静态资源

由 `WebMvcConfig` 暴露，直接返回文件（非 `ResponseResult` 信封）：

| 路径 | 说明 |
|---|---|
| `/dat/**` | 历史区块 `.dat` 文件 |
| `/source-code/**` | 各版本源码压缩包 |

---

## 10. 数据结构附录

字节字段一律 hex 字符串输出（见 [§1.4](#14-字段序列化约定)）；字节布局语义见 [PROTOCOL.md](../PROTOCOL.md)。

### 10.1 BlockInfo
区块头 + 文件路径。`rawBytes` 不输出。

| 字段 | JSON 形态 | 说明 |
|---|---|---|
| `id` | hex | 区块头哈希 |
| `version` | int | 区块版本 |
| `height` | Long | 高度 |
| `sourceCodeZipHash` | hex | 源码包 SHA-256 |
| `previousBlockHash` | hex | 前区块头哈希 |
| `merkleRoot` | hex | 默克尔根（沿用 Bitcoin 算法，含 CVE-2012-2459 重复尾延展性，见 [PROTOCOL.md §7](../PROTOCOL.md)） |
| `maxMsgTimestamp` | Long | 信息内最大时间戳（微秒） |
| `registerDifficultyTarget` | hex(`0x…`) | 注册难度 nbits |
| `transactionDifficultyTarget` | hex(`0x…`) | 交易难度 nbits |
| `centralPubkey` | hex | 中心公钥 |
| `timestamp` | Long | 固定区块时间戳（微秒） |
| `centralSignature` | hex | 中心签名 |
| `datFilepath` | String | 所在 `.dat` 路径 |
| `sourceCodeZipFilepath` | String | 源码包文件名 |

### 10.2 协议消息实体（共有约定）
所有消息实体：`id`(UUID)、`msgType`(数值类型码)、各公钥/签名为 hex、`txid`(hex)；可中心签名类型含 `confirmTimestamp`(微秒) 与 `centralSignature`(hex)。`rawBytes` 与 `BlockInfo` 一致**不输出**（`@JsonIgnore`）。各类型专有字段：

- **FlowNodeRegisterMsg**（0x0000）：`registerDifficultyTarget`(hex)、`nonce`(int)、`flowNodePubkey`、`flowNodeSignature`。无 `confirmTimestamp`/`centralSignature`。
- **CentralPubkeyEmpowerMsg**（0x0001）：`flowNodePubkey`、`centralPubkey`、`flowNodeSignature`。
- **CentralPubkeyLockedMsg**（0x0002）：`centralPubkey`、`centralSignaturePre`（对前 3 项的预签名）。
- **FlowNodeLockedMsg**（0x0003）：`flowNodePubkey`、`centralPubkey`、`flowNodeSignature`。
- **TransactionRecordMsg**（0x0004）：`amount`(Long)、`currencyType`(Short)、`transactionDifficultyTarget`(hex)、`nonce`、`consumeNodePubkey`、`flowNodePubkey`、`centralPubkey`、`consumeNodeSignature`、`flowNodeSignature`。
- **TransactionMountMsg**（0x0005）：`mountedTransactionRecordId`(UUID)、`transactionDifficultyTarget`(hex)、`nonce`、`consumeNodePubkey`、`flowNodePubkey`、`centralPubkey`、`consumeNodeSignature`、`flowNodeSignature`。

### 10.3 ConsumeChain
| 字段 | JSON 形态 | 说明 |
|---|---|---|
| `id` | UUID | 消费链 id |
| `start` | UUID | 起点流转节点（引用→UUID） |
| `end` | UUID | 终点流转节点（引用→UUID） |
| `amount` | Long | 金额（最小面值单位） |
| `currencyType` | Short | 货币类型 |
| `isLoop` | Boolean | 是否成环 |
| `tailMountTimestamp` | Long | 末端挂载时间（微秒） |

### 10.4 ConsumeChainEdge
| 字段 | JSON 形态 | 说明 |
|---|---|---|
| `id` | UUID | 边 id |
| `source` / `target` | UUID | 来源 / 目标流转节点（引用→UUID） |
| `amount` | Long | 金额 |
| `currencyType` | Short | 货币类型 |
| `chain` | UUID | 所属消费链（引用→UUID） |
| `relatedTransactionRecord` | UUID | 关联交易记录（引用→UUID） |
| `relatedTransactionMount` | UUID | 关联交易挂载（引用→UUID） |
| `relatedTransactionMountTimestamp` | Long | 关联挂载时间（微秒） |
| `isLoop` | Boolean | 该边是否属于成环链 |

### 10.5 ReturningFlowRateResponseDTO
| 字段 | 类型 | 说明 |
|---|---|---|
| `returningFlowRate` | double | 回流率（source→target 模式） |
| `loopedAmount` | double | 成环金额总和 |
| `unloopedAmount` | double | 未成环金额总和（滞留指数） |
| `targetTotalLoopedAmount` | double | 目标总成环金额（仅 target 模式） |
| `targetTotalUnloopedAmount` | double | 目标总滞留指数（仅 target 模式） |
| `currencyType` | short | 货币类型 |

### 10.6 FlowNodeStateResponseDTO
`registered`、`authorized`、`locked`、`currentCentralPubkeyAuthorized`（均 boolean）。`authorized`=曾授权任一中心公钥；`currentCentralPubkeyAuthorized`=已授权当前中心公钥；中心公钥轮换后二者可不同。

### 10.7 FlowNodeListItemDTO
`id`(UUID)、`flowNodePubkey`(hex)、`registered`、`authorized`、`locked`、`currentCentralPubkeyAuthorized`(boolean)。

### 10.8 元数据 DTO
- **MsgTypeMetadataDTO**：`code`(枚举名)、`value`(short)、`hexValue`(`0x####`)、`size`(int，落库字节)、`inboundSize`(int，入站字节)、`sizeUnit`(`"字节"`)、`name`。
- **CurrencyTypeMetadataDTO**：`code`、`value`(short)、`description`、`unit`、`unitDescription`。
- **BlockFormatMetadataDTO**：`magicNumber`(int)、`magicNumberHex`、`genesisHash`、`hashSize`、`messageCountFieldSize`、`blockVersion`、`blockHeaderSize`、`blockMaxSizeBytes`(long)、`blockDatMaxSizeBytes`(long)、`datFilePrefix`、`datFileSuffix`、`datFileIndexWidth`、`sourceCodeZipPrefix`、`sourceCodeZipSuffix`。
- **DifficultyMetadataDTO**：`register`、`transaction`，各为 `DifficultyTarget { nbitsInt(int), nbitsHex(String), targetDecimal(String), targetHex(String) }`。

### 10.9 系统与运维 DTO
- **SystemParamsDTO**：`blockVersion`、`centralPubkey`(hex)、`registerDifficultyTargetNbits`(int)、`registerDifficultyTargetNbitsHex`、`transactionDifficultyTargetNbits`(int)、`transactionDifficultyTargetNbitsHex`、`sourceCodeZipHash`(hex)、`latestBlockHeight`(Long)、`latestBlockHash`(hex)。
- **SystemStatusDTO**：`latestBlockHeight`(Long)、`latestBlockHash`(hex)、`latestBlockTimestamp`(Long，微秒)、`pendingMessageCount`(long)、`oldestPendingConfirmTimestamp`(Long)、`blockIntervalMs`(long，**毫秒**)、`currentCentralPubkeyLocked`(boolean)。
- **StorageStatusDTO**：`datDirectory`、`datFileCount`(int)、`currentDatFileName`、`currentDatFileSizeBytes`(long)、`totalDatBytes`(long)、`datMaxSizePerFileBytes`(long)、`currentDatUtilizationPct`(double)。
- **LockedMessageResponseDTO\<T\>**：`locked`(boolean)、`lockedMsg`(T，对应冻结消息实体或 null)。
- **ChainVerificationSummaryDTO**：`valid`(boolean，链是否通过核验)、`datDirectory`、`blockCount`(int)、`messageCount`(long)、`passedChecks`/`failedChecks`/`skippedChecks`(long)、`statefulReplayIncluded`(boolean)、`failureCount`(int)、`failures`(列表，每项 `{scope, name, category, detail}`，最多 100 条)、`configuredCentralPubkeyHex`、`runningSourceCodeZipHash`。

---

> 本文档随代码演进维护；若与控制器/实体不一致，以源码为准。协议字节细节见 [PROTOCOL.md](../PROTOCOL.md)。
