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