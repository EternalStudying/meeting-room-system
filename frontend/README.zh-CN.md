<div align="center">
  <img alt="logo" width="120" height="120" src="./src/common/assets/images/layouts/logo.png">
  <h1>智能会议管理平台前端</h1>
</div>

<p align="center">
  面向会议室预约、会议协同和后台管理场景的前端项目。
</p>

<p align="center">
  <a href="./README.md">English</a>
</p>

## 项目简介

智能会议管理平台前端是一个基于 Vue 3 的会议管理系统前端应用，围绕真实的会议室预约与管理流程构建，覆盖普通用户端和管理端的核心操作。

当前项目已经实现会议室查询、预约日历、我的预约、AI 辅助问答、会议室与设备管理、设备绑定统计、运营分析等模块，不再只是一个通用后台模板。

## 核心功能

- 概览看板：展示当日会议、空间使用率、高峰时段、待办事项和房间状态
- AI 助手：提供面向预约场景的自然语言问答和快捷提示
- 会议室查询：支持搜索、筛选、容量分类、房间详情和快捷预约
- 预约日历：支持按会议室或“只看我的预约”查看日历，并对可编辑预约进行拖拽调整
- 我的预约：支持查看进行中和已结束预约，并进行修改、取消、会后评价
- 管理端：支持会议室管理、设备管理、设备绑定统计和统计分析
- 权限控制：基于角色动态加载管理端路由
- 多环境配置：支持开发、预发、生产环境

## 技术栈

- Vue 3
- Vite
- TypeScript
- Element Plus
- Pinia
- Vue Router
- Axios
- FullCalendar
- ECharts
- UnoCSS
- SCSS
- Vitest

## 主要页面

- `概览`：会议运营总览与每日状态
- `AI 助手`：预约场景智能问答
- `会议空间`：会议室检索与预约入口
- `预约日历`：日历排期与预约调整
- `我的预约`：个人预约全生命周期管理
- `管理端 / 会议室管理`
- `管理端 / 设备管理`
- `管理端 / 设备绑定统计`
- `管理端 / 统计分析`

## 目录结构

```text
src/
  common/
    apis/          按业务划分的接口模块
    assets/        图标、图片和公共样式
    components/    通用组件
    composables/   通用组合式函数
  components/      业务组件，例如预约弹窗
  http/            axios 请求封装
  layouts/         页面布局系统
  pages/           路由页面
  pinia/           状态管理
  router/          路由配置与守卫
tests/             单元测试和页面测试
types/             全局类型与自动生成类型声明
```

## 本地开发

### 推荐环境

- Node.js 20.19+ 或 22.12+
- pnpm 10+

### 安装依赖

```bash
pnpm install
```

### 启动开发环境

```bash
pnpm dev
```

### 构建

```bash
pnpm build:staging
pnpm build
```

### 本地预览

```bash
pnpm preview
```

### 代码检查与测试

```bash
pnpm lint
pnpm test
```

## 环境配置

项目当前使用以下环境文件：

- `.env.development`
- `.env.staging`
- `.env.production`

统一请求封装位于 `src/http/axios.ts`。当前开发环境接口基地址为 `VITE_BASE_URL=/api/v1`。

## 项目预览

![preview](./src/common/assets/images/docs/preview.png)

## License

[MIT](./LICENSE)
