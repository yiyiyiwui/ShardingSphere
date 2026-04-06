# ShardingSphere 分库分表实战示例

> 开箱即用的 Apache ShardingSphere 5.x + Spring Boot 3.x + MyBatis-Plus 分库分表完整示例
> 含前端可视化测试页面、性能对比、多种分片策略演示

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![ShardingSphere](https://img.shields.io/badge/ShardingSphere-5.2.1-blue.svg)](https://shardingsphere.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 目录

- [项目介绍](#项目介绍)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [分片策略说明](#分片策略说明)
- [如何整合到已有项目](#如何整合到已有项目)
- [分片后如何查询](#分片后如何查询)
- [常见问题](#常见问题)
- [注意事项](#注意事项)

---

## 项目介绍

本项目演示了 Apache ShardingSphere 三种核心分片模式：

| 配置文件 | 模式 | 物理表数量 | 适用场景 |
|---------|------|-----------|---------|
| `sharding.yml` | 单库分表 | 1库 × 2表 = 2张 | 数据量 500万~5000万 |
| `sharding-01.yml` | 多库多表 | 2库 × 3表 = 6张 | 数据量 >5000万 |
| `sharding-02.yml` | 不分片（单表） | 1库 × 1表 = 1张 | 基准对照 / 小数据量 |

---

## 快速开始

### 第一步：环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 第二步：建库建表

执行 `sql/init.sql`（项目根目录下）：

```sql
-- 创建数据库
CREATE DATABASE shardingsphere CHARACTER SET utf8mb4;
CREATE DATABASE shardingsphere_01 CHARACTER SET utf8mb4;
CREATE DATABASE shardingsphere_02 CHARACTER SET utf8mb4;

-- 单库分表（sharding.yml 使用）
USE shardingsphere;
CREATE TABLE tb_order_0 (
    id       bigint NOT NULL COMMENT '雪花ID',
    order_id int    NOT NULL COMMENT '订单ID（分片键）',
    seller_id int   NOT NULL COMMENT '卖家ID',
    buyer_id  int   NOT NULL COMMENT '买家ID',
    PRIMARY KEY (id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tb_order_1 LIKE tb_order_0;

-- 多库多表（sharding-01.yml 使用）
USE shardingsphere_01;
CREATE TABLE tb_order_0 LIKE shardingsphere.tb_order_0;
CREATE TABLE tb_order_1 LIKE shardingsphere.tb_order_0;
CREATE TABLE tb_order_2 LIKE shardingsphere.tb_order_0;

USE shardingsphere_02;
CREATE TABLE tb_order_0 LIKE shardingsphere.tb_order_0;
CREATE TABLE tb_order_1 LIKE shardingsphere.tb_order_0;
CREATE TABLE tb_order_2 LIKE shardingsphere.tb_order_0;
```

> ⚠️ **重要**：注意 id 字段类型改为 `bigint`，以支持雪花算法生成的 Long 类型 ID。

### 第三步：修改数据库连接

打开 `src/main/resources/sharding.yml`，修改数据库连接信息：

```yaml
jdbc-url: jdbc:mysql://localhost:3306/shardingsphere?useSSL=false&serverTimezone=Asia/Shanghai
username: root
password: 你的密码
```

### 第四步：选择分片模式

打开 `src/main/resources/application.yml`，修改 import 行：

```yaml
spring:
  config:
    import: classpath:sharding.yml      # 单库分表
    # import: classpath:sharding-01.yml  # 多库多表（注释掉上面，打开这行）
    # import: classpath:sharding-02.yml  # 不分片（基准对照）
```

### 第五步：启动项目

```bash
mvn spring-boot:run
```

启动成功后访问：
- 后端接口：`http://localhost:9906`
- 前端测试页面：直接用浏览器打开 `http://localhost:9906/sharding-frontend.html` 修改页面顶部的Base URL 为 `http://localhost:9906`

---

## 项目结构

```
ShardingSphere/
├── src/main/java/com/hello/
│   ├── controller/
│   │   ├── OrderController.java          # 基础 CRUD 接口
│   │   ├── ShardingDemoController.java   # 分片演示（精准路由、全路由等）
│   │   └── BenchmarkController.java      # 性能基准测试接口
│   ├── service/
│   │   └── BenchmarkService.java         # 性能测试逻辑
│   ├── mapper/
│   │   ├── OrderMapper.java              # MyBatis-Plus 基础 Mapper
│   │   └── OrderRawMapper.java           # 原生 SQL Mapper（直接查物理表）
│   └── pojo/
│       └── Order.java                    # 订单实体
│
├── src/main/resources/
│   ├── application.yml                   # 主配置（选择 import 哪个分片配置）
│   ├── sharding.yml                      # 单库分表配置
│   ├── sharding-01.yml                   # 多库多表配置（推荐生产）
│   └── sharding-02.yml                   # 不分片配置（基准对照）
│
├── static/
│   └── index.html                        # 可视化测试页面（直接浏览器打开）
│
└── sql/
    └── init.sql                          # 建库建表 SQL
```

---

## 分片策略说明

### 策略一：单库分表（sharding.yml）

```
shardingsphere 数据库
  ├── tb_order_0  ← order_id 为偶数（0, 2, 4...）
  └── tb_order_1  ← order_id 为奇数（1, 3, 5...）
```

分片算法：`order_id % 2`（MOD 取模）

适用场景：单表数据超过 500 万，但总量在 5000 万以内，不想拆多个数据库实例。

---

### 策略二：多库多表（sharding-01.yml）

```
shardingsphere_01 (db_0)         shardingsphere_02 (db_1)
  ├── tb_order_0  ← (偶数库,余0)   ├── tb_order_0  ← (奇数库,余0)
  ├── tb_order_1  ← (偶数库,余1)   ├── tb_order_1  ← (奇数库,余1)
  └── tb_order_2  ← (偶数库,余2)   └── tb_order_2  ← (奇数库,余2)

分库：order_id % 2  → db_0 或 db_1
分表：order_id % 3  → tb_order_0 / _1 / _2
```

适用场景：数据量超过 5000 万，或需要通过多个 MySQL 实例分摊写入压力。

---

### 分片键如何选择？

| 查询模式 | 推荐分片键 | 原因 |
|---------|-----------|------|
| 按用户查自己的订单 | `user_id` | 保证同一用户的订单在同一库，避免跨库查询 |
| 按时间段查流水 | `create_time`（RANGE 策略）| 时间范围查询精准路由到对应月份分表 |
| 按订单号查详情 | `order_id` | 订单 ID 均匀分布，MOD 后分布均匀 |

**黄金原则：分片键 = 最高频的查询条件**

---

## 如何整合到已有项目

只需 4 步，对已有代码**零修改**：

### Step 1：添加 Maven 依赖

```xml
<!-- ShardingSphere JDBC 核心（替换你原来的数据库配置依赖） -->
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
    <version>5.2.1</version>
</dependency>

<!-- 注意：不需要单独引 HikariCP，ShardingSphere 已内置 -->
```

### Step 2：复制分片配置文件

将本项目的 `sharding.yml` 复制到你项目的 `src/main/resources/` 目录下，修改：
- 数据库连接地址
- 分片表名（把 `tb_order` 改成你的表名）
- 分片键（把 `order_id` 改成你的分片字段）
- 分片数量（把 `sharding-count: 2` 改成你要分的数量）

### Step 3：修改 application.yml

```yaml
spring:
  config:
    import: classpath:sharding.yml  # 引入分片配置

# 删掉原来的 spring.datasource 配置！ShardingSphere 会接管数据源
# 删掉：
# spring:
#   datasource:
#     url: ...
#     username: ...
```

### Step 4：建物理表

如果原来表叫 `tb_order`，分2张表则需要建：
- `tb_order_0`
- `tb_order_1`

表结构与原表完全相同。可以这样建：

```sql
CREATE TABLE tb_order_0 LIKE tb_order;
CREATE TABLE tb_order_1 LIKE tb_order;
```

**就这4步，重启项目，代码一行不改，分片生效。**

---

## 分片后如何查询

### 方式一：带分片键查询（推荐，精准路由）

```java
// 这样写，ShardingSphere 根据 order_id 只查 tb_order_0 或 tb_order_1
// 性能与查单表完全相同
QueryWrapper<Order> qw = new QueryWrapper<>();
qw.eq("order_id", 10);
List<Order> orders = orderMapper.selectList(qw);
```

背后执行的 SQL（ShardingSphere 改写后）：
```sql
SELECT * FROM tb_order_0 WHERE order_id = 10  -- order_id=10，10%2=0，路由到_0
```

---

### 方式二：不带分片键查询（全路由，慎用）

```java
// 按非分片键查询，ShardingSphere 会广播到所有分表
QueryWrapper<Order> qw = new QueryWrapper<>();
qw.eq("buyer_id", 100);
List<Order> orders = orderMapper.selectList(qw);
```

背后执行的 SQL：
```sql
SELECT * FROM tb_order_0 WHERE buyer_id = 100  -- 查所有分表！
UNION ALL
SELECT * FROM tb_order_1 WHERE buyer_id = 100
```

> ⚠️ 分表越多，全路由查询越慢。非分片键查询频繁时，考虑：
> - 给该字段建索引
> - 引入 Elasticsearch 做二级索引
> - 或把该字段也作为分片键（复合分片）

---

### 方式三：Mapper XML 写法（完全透明）

你的 XML 仍然写逻辑表名 `tb_order`，ShardingSphere 自动路由：

```xml
<!-- OrderMapper.xml -->
<select id="getByOrderId" resultType="com.hello.pojo.Order">
    SELECT id, order_id, seller_id, buyer_id
    FROM tb_order
    WHERE order_id = #{orderId}
    <!-- ShardingSphere 自动改写为：FROM tb_order_0 WHERE order_id = ? -->
</select>
```

---

## 常见问题

### Q1：id 字段报错 Out of range value

原因：数据库 id 字段是 `int`，但雪花算法生成的是 Long（约 18位数字），超出 int 范围。

解决：建表时 id 字段改为 `bigint`：

```sql
ALTER TABLE tb_order_0 MODIFY id bigint NOT NULL COMMENT '雪花ID';
ALTER TABLE tb_order_1 MODIFY id bigint NOT NULL COMMENT '雪花ID';
```

---

### Q2：ShardingSphere 与 Druid 连接池冲突

原因：ShardingSphere 5.x 默认使用 HikariCP，与 Druid 可能有类加载冲突。

解决：在 pom.xml 中排除 Druid，或使用 HikariCP：

```xml
<!-- 移除 Druid 依赖，使用 ShardingSphere 内置的 HikariCP -->
<!-- 如果必须用 Druid，改用：com.alibaba.druid.pool.DruidDataSource -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid</artifactId>
    <!-- 改为只在测试时使用，或直接删掉 -->
</dependency>
```

---

### Q3：多个服务都要查分片表怎么办？

每个服务都需要：
1. 引入 ShardingSphere 依赖
2. 复制相同的 `sharding.yml` 配置文件
3. 修改 `application.yml` import

分片规则要保持**完全一致**，否则不同服务写入的数据路由不一致会造成数据混乱。

建议：将 `sharding.yml` 抽取到配置中心（Nacos/Apollo），所有服务统一拉取，修改分片规则只需改配置中心。

---

### Q4：分片后能做跨表 JOIN 吗？

原则上不建议。ShardingSphere 对部分跨分片 JOIN 有支持，但性能差且限制多。

推荐解决方案：
- **广播表**：数据量小、需要 JOIN 的配置表（如城市表、类目表），配置为广播表，每个分库都存一份
- **冗余字段**：将 JOIN 所需字段冗余到主表，避免 JOIN
- **应用层 JOIN**：分两次查询，在 Java 代码中做数据聚合

---

### Q5：数据迁移——原来的单表数据怎么迁到分片表？

推荐使用以下步骤：

1. 建好分片表（`tb_order_0`、`tb_order_1`）
2. 写迁移脚本，按分片键重新 INSERT 到对应物理表
3. 验证数据量一致后，切换应用配置到 ShardingSphere
4. 下线旧的单表

```sql
-- 迁移脚本示例（将旧 tb_order 数据按取模迁移）
INSERT INTO tb_order_0 SELECT * FROM tb_order WHERE order_id % 2 = 0;
INSERT INTO tb_order_1 SELECT * FROM tb_order WHERE order_id % 2 = 1;
-- 验证：SELECT COUNT(*) FROM tb_order_0; + SELECT COUNT(*) FROM tb_order_1; 之和应等于原表
```

---

## 注意事项

### 1. 分布式 ID 必须用！

分表后**禁止**使用数据库自增 ID（各分表各自自增会重复）。

本项目已配置雪花算法，只需在实体类中：

```java
@TableId(value = "id", type = IdType.ASSIGN_ID)  // MyBatis-Plus 雪花ID
private Long id;  // 必须是 Long 类型！
```

### 2. 分片键不能为 NULL

INSERT 时分片键不能为空，否则 ShardingSphere 不知道路由到哪张表会报错。

### 3. 分片键一旦确定不要轻易修改

分片键决定数据在哪张物理表，修改分片键需要全量数据迁移。选型时要深思熟虑。

### 4. ShardingSphere 不支持的 SQL

- `SELECT *` 配合 `ORDER BY` 在跨分片时内存排序（大数据量慎用）
- 跨库事务（需要配置 XA 分布式事务）
- 部分聚合函数在跨分片时结果可能不准（如 `AVG`，ShardingSphere 会尝试改写但有限制）
- `ON DUPLICATE KEY UPDATE`（分片场景下行为复杂）

### 5. 生产部署建议

```yaml
# 多实例部署时，每个实例的 worker-id 必须不同（0~1023）
key-generators:
  snowflake:
    type: SNOWFLAKE
    props:
      worker-id: ${WORKER_ID:1}  # 通过环境变量注入，不同实例传不同值
```

---

## API 接口速查

| Method | URL | 说明 |
|--------|-----|------|
| GET | `/sharding/orders` | 查询所有（全路由） |
| GET | `/sharding/orders/by-order-id?orderId=10` | 精准路由查询 |
| GET | `/sharding/orders/by-buyer?buyerId=100` | 非分片键全路由 |
| GET | `/sharding/orders/by-order-ids?orderIds=1,2,3` | IN 查询 |
| GET | `/sharding/analyze?orderId=15` | 路由分析（不执行SQL） |
| POST | `/sharding/orders` | 插入（Body: JSON） |
| PUT | `/sharding/orders` | 更新 |
| DELETE | `/sharding/orders?orderId=10` | 删除 |
| POST | `/bench/insert?count=1000` | 批量插入性能测试 |
| GET | `/bench/query?orderId=50` | 查询对比测试 |
| GET | `/bench/distribution` | 数据分布统计 |
| DELETE | `/bench/clear` | 清空测试数据 |

---

## 测试整合到正在运行的test项目中

我当前项目使用 `com.hello.test` 包，表名为 `t_device`，分片键为 `device_id`。

### 具体改造步骤（已验证）

1. 在 `pom.xml` 中加入 ShardingSphere 依赖。
2. 创建 `testSharding.yml`（单库 4 表配置），内容见上方。
3. 修改 `application.yml`，使用 `import: classpath:testSharding.yml`。
4. 在数据库中把 `id` 改为 `BIGINT`，并创建 `t_device_0` ~ `t_device_3` 四张物理表，同时重建索引。
# 当前表结构如下
```sql
CREATE TABLE t_device (
    id           INT AUTO_INCREMENT PRIMARY KEY, -- 自增主键
    device_id    VARCHAR(50) NOT NULL,           -- 设备编号（业务ID）
    device_name  VARCHAR(100),                   -- 设备名称
    room_name    VARCHAR(100),                   -- 所属机房
    status       INT DEFAULT 1,                  -- 状态 (1:正常, 0:异常)
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP -- 插入时间
);
```
```sql
-- 1. 把 id 改为 BIGINT（支持雪花算法）
ALTER TABLE t_device 
    MODIFY id BIGINT NOT NULL COMMENT '雪花ID',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id);

-- 2. 创建 4 张物理分表
CREATE TABLE t_device_0 LIKE t_device;
CREATE TABLE t_device_1 LIKE t_device;
CREATE TABLE t_device_2 LIKE t_device;
CREATE TABLE t_device_3 LIKE t_device;

-- 3. 在每张分表上重建常用索引（索引依然生效）
ALTER TABLE t_device_0 ADD INDEX idx_device_id (device_id);
ALTER TABLE t_device_0 ADD INDEX idx_room_status (room_name, status);

-- 4. 把原来的表数据迁移到新的分区表
-- 按 device_id 的 HASH 值迁移数据
INSERT INTO t_device_0
SELECT * FROM t_device
WHERE ABS(CRC32(device_id)) % 4 = 0;

INSERT INTO t_device_1
SELECT * FROM t_device
WHERE ABS(CRC32(device_id)) % 4 = 1;

INSERT INTO t_device_2
SELECT * FROM t_device
WHERE ABS(CRC32(device_id)) % 4 = 2;

INSERT INTO t_device_3
SELECT * FROM t_device
WHERE ABS(CRC32(device_id)) % 4 = 3;
```

5. 实体类 `Device` 使用 `@TableId(type = IdType.ASSIGN_ID)` 和 `Long id`。
6. Mapper 和 Controller 中 SQL 永远只写逻辑表名 `t_device`，代码几乎零修改。
7. 重启项目，打开 `sql-show: true` 观察路由日志。

**效果**：插入时自动根据 `device_id` 路由到对应分表，查询带 `device_id` 时精准路由，性能接近单表。适合千万级设备监控数据场景。

后续可轻松扩展为 8 张表或多库多表,分表后原来的 t_device 可以删除。