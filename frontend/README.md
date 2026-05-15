<div align="center">
  <img alt="logo" width="120" height="120" src="./src/common/assets/images/layouts/logo.png">
  <h1>Intelligent Conference Management Platform Frontend</h1>
</div>

<p align="center">
  Frontend project for an intelligent conference and meeting room management platform.
</p>

<p align="center">
  <a href="./README.zh-CN.md">Chinese Version</a>
</p>

## Introduction

Intelligent Conference Management Platform Frontend is a Vue 3 based web application for meeting room reservation, schedule coordination, room and device management, and operational analysis. It is built around a real conference management workflow rather than a generic admin template.

The current frontend covers both regular user flows and admin flows, including reservation lookup, calendar scheduling, personal reservation management, AI-assisted querying, and room/device administration.

## Core Features

- Overview dashboard with daily meeting summary, room status, peak time window, and to-do information
- AI assistant page for reservation-oriented question answering and quick prompts
- Meeting room directory with search, filter, capacity classification, room details, and quick reservation entry
- Reservation calendar with room-based filtering, personal reservation view, and drag-to-adjust scheduling for editable reservations
- My reservations page with active and ended reservation management, edit, cancel, and post-meeting review
- Admin workspace for meeting room management, device management, device binding statistics, and operational analytics
- Role-based routing for admin pages
- Multiple runtime environments: development, staging, and production

## Tech Stack

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

## Main Pages

- `Overview`: operational summary and daily status
- `AI Assistant`: natural-language reservation helper
- `Rooms`: meeting room discovery and booking entry
- `Calendar`: reservation calendar and schedule adjustment
- `My Reservations`: personal reservation lifecycle management
- `Admin / Rooms`: room administration
- `Admin / Devices`: device administration
- `Admin / Device Stats`: room-device binding statistics
- `Admin / Stats`: reservation and usage analytics

## Project Structure

```text
src/
  common/
    apis/          API modules grouped by business domain
    assets/        icons, images, and shared styles
    components/    reusable shared components
    composables/   shared composables
  components/      business components such as reservation dialog
  http/            axios request wrapper
  layouts/         application layout system
  pages/           route pages
  pinia/           state stores
  router/          route definitions and guards
tests/             unit and page-level tests
types/             global and auto-generated type declarations
```

## Getting Started

### Recommended Environment

- Node.js 20.19+ or 22.12+
- pnpm 10+

### Install Dependencies

```bash
pnpm install
```

### Start Development Server

```bash
pnpm dev
```

### Build

```bash
pnpm build:staging
pnpm build
```

### Preview Production Build

```bash
pnpm preview
```

### Lint and Test

```bash
pnpm lint
pnpm test
```

## Environment Configuration

The project currently uses these environment files:

- `.env.development`
- `.env.staging`
- `.env.production`

The unified request wrapper is located in `src/http/axios.ts`. The development environment currently uses `VITE_BASE_URL=/api/v1`.

## Preview

![preview](./src/common/assets/images/docs/preview.png)

## License

[MIT](./LICENSE)
