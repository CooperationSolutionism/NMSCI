# 更新日志（Changelog）

本项目所有值得注意的变更记录于此。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> **两套版本说明**：`block-version`（上链区块版本）与软件 SemVer 是两套独立版本。2.0.1 将 `block-version` 升至 **3**，使本次构建绑定独立源码包；字节/wire 协议与区块头字节数（229）仍冻结。

## [2.0.1] - 2026-08-02

### 变更
- 生产 profile 的注册 PoW 难度由 `0x1f002708` 降至最低难度编码 `0x20ffffff`，与交易及 dev/test/load profile 对齐。
- `block-version` 由 2 升至 3，验证器支持上限同步升至 3，新区块绑定 `source_code_v3.zip`。

### 升级
- 本版本不改变字节/wire 协议，也不新增数据库迁移；部署到既有链前仍须按 README 升级流程停止外部写入、排空未入块消息并完成静默校验。

## [2.0.0] - 2026-07-01

面向生产的重构与硬化版本：API 全面 REST 化、引入 Flyway 迁移与安全基线升级、独立链验证器与可观测性、Java 21，以及多轮性能优化。`block-version` 升至 2（既有 v1 链原地升级）。

### 新增
- **API 全面 REST 化**，补全读侧/运维/元数据端点：系统参数、系统状态、存储状态；消息类型/货币类型/单位说明；流转节点状态与分页查询；消费链按起点/终点/节点/公钥/挂载交易等多维查询；统一分页。
- **独立区块链验证器**：`.dat` 离线核验 CLI 与 `/verify/chain` 端点；有状态回放按入账顺序闭环时序敏感校验；空链 fail-closed。
- **Flyway 数据库迁移**，替换手写双轨 schema/patches；基线对齐线上真实模式，安全升级既有 v1.0.0 库。
- **可观测性**：Micrometer 指标 + `/actuator/prometheus`，出块耗时/失败与拒绝日志上下文。
- **Docker 支持**：多阶段从源码构建 + PostgreSQL Compose。
- **中心公钥轮换**：公证唯一性放宽为 `(流转节点公钥, 中心公钥)` 组合。
- **区块版本升级控制**：有界单调接受 + 生产降级护栏 + 启动版本断言；出块开关 `block-generation-enabled`（升级静默校验窗）。
- **崩溃自愈**：`.dat` 与数据库启动对账，安全自愈尾部孤儿块。
- **源码包完整性**：完整性校验 + 原子发布；升级前置只读检测 CLI（`UpgradePreflightCli`）与中心密钥生成 CLI。
- **成环优化**：挂载优先选择链头 `start = 目标` 的消费链以提升成环概率。
- **CI 流水线** 与 Gatling 压测；配置类启动期校验。

### 变更
- **发布 2.0.0**，`block-version` 升至 2（原地升级既有 v1 链）。
- **Java 17 → 21**。
- 锁死区块头字节数为协议冻结值 **229**，并增强 `block-max-size` 下限校验。
- `Sha256Util` 改用 JDK `MessageDigest`（启用 SHA-256 intrinsic）。
- `/metadata/message-types` 增补入站字节数 `inboundSize`，并由 codec 启动期校验。
- 关闭 `spring.jpa.open-in-view`；显式配置 HikariCP 连接池参数。
- 项目结构标准化；依赖版本集中管理，新增 `.env.example`。

### 性能
- **写路径**：4 个消息写服务事务收窄（PoW/验签/央签移出事务，仅冲突复检 + 原子落库进窄事务），并改用 `EntityManager.persist` 消除赋值主键实体的 select-before-insert。
- **验签**：直接以压缩公钥验签，消除 `KeyFactory`/`PublicKey` 往返，`KeyFactory` 静态缓存；缓存中心签名 `ECKey` 避免每次重派生公钥点。
- **查询索引**：新增交易时间戳复合索引（V5）及既有多处查询索引。
- **读端点**：运营/元数据端点改用 `BlockInfoSummary` 投影；`BlockInfo.rawBytes` 排除序列化，元数据响应不再返回整块字节。
- **验证/重放**：`/verify/chain` 结果缓存 + 单航道收敛；全链重放预算入账排序键、记忆化成员签名。
- **出块/消费链**：装块选择批量按区块预算估算；消费链分配单条窗口累计和查询；缩短挂载锁窗口；收敛只读查询边界。
- **源码包**：完整性校验按已验证版本缓存。

### 修复
- HTTP 边界异常映射（400/404/405/415）与挂载分配 **TOCTOU 竞态**；保存链路事务边界；区块生成强一致保护。
- `.dat` 轮转在 Linux/WSL/macOS 下的路径拆分；区块大小计算漏算 6 个消息数量字段（共 48 字节）。
- 消费链成环改用持久化标识判断；消费链边去重排序稳定；分页限制溢出。
- 统一缺失资源返回 404；`/blocks/latest` 无区块返回 404；创建响应契约统一。
- 交易金额正数校验 + 数据库约束；搜索公钥参数长度校验；hex 校验与组合查询编码一致。
- 消息实体不再输出 `rawBytes`；避免 `datDirectory` 暴露 dat 目录绝对路径。
- 处理创世前请求与调度异常；重构中心公钥冻结停机流程。

### 安全
- 源码哈希路径加固：不跟随符号链接、加强路径校验；仓库文本统一为 LF，使上链源码哈希跨平台可复现。
- 中心密钥对一致性校验；协议原语与公钥前缀边界校验加固。
- 依赖升级至安全版本。

### 迁移与升级
- 数据库迁移 **V1–V5**（V1 基线快照 / V2 约束 + 索引 / V3 硬化 / V4 公钥轮换 / V5 交易时间戳索引）。既有生产库升级前**必须**确认存量数据满足新增约束（V2/V3 追加唯一/检查/外键约束，存量违例将导致启动失败）；V4 放宽约束、V5 仅新增索引，二者均无需预检。

## [1.0.0] - 2025-09-20
- 首个发布：消费意愿数值化衡量系统（NMSCI）基础实现。

[2.0.1]: https://github.com/Cooperative-Solutionism/NMSCI/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/Cooperative-Solutionism/NMSCI/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/Cooperative-Solutionism/NMSCI/releases/tag/v1.0.0
