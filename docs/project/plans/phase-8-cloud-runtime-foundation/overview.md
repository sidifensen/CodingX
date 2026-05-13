# Phase 8：云端执行基础

更新时间：2026-05-12

## 一、概述

这一期先做云端环境的基础层，不一上来就追求完整云平台。

重点是先把本地和云端的执行目标抽象清楚，再建立 cloud workspace 和 cloud executor。

## 二、目标

- 建立 `executionTarget` 抽象
- 建立执行路由
- 建立 cloud workspace
- 建立 cloud executor
- 跑通最小云端执行闭环

## 三、要做的事情

- 定义 `executionTarget` 模型
- 做 `execution-router`
- 让任务创建时可配置执行目标
- 统一本地 / 云端状态流
- 定义 cloud workspace 目录模型
- 做 cloud executor
- 做 task 级云端隔离
- 跑通最小云端任务执行闭环

