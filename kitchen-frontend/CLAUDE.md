# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

智慧厨房后厨终端 (Smart Kitchen Terminal Frontend) — A Vue 3.4 + TypeScript 5.3 SPA designed for kitchen display screens. Port 5174. Proxies `/api` to gateway (8080).

## Commands

```bash
npm install
npm run dev           # Start dev server (http://localhost:5174)
npm run build         # Type-check + production build
npx vite build        # Production build without type-checking
npm run type-check    # TypeScript validation only
npm run test          # Node.js built-in test runner (kitchen-frontend-tests/**/*.test.mjs)
npm run preview       # Preview production build
node --test kitchen-frontend-tests/offline-queue.test.mjs   # Run a single test file
```

No separate lint script; `npm run build` runs `vue-tsc && vite build`.
Current state verified in this repository: `npm run type-check` and `npm run build` both pass.

## Architecture

### Tech Stack
- Vue 3.4 + TypeScript 5.3 + Vite 5.0
- Element Plus 2.5 (Chinese locale, icons registered globally)
- Pinia 2.1 (composition API style)
- Vue Router 4.2 (role-based default route)
- vue-i18n 9.14 (zh-CN / en-US)
- Axios 1.6

### Role-Based Routing
The app has two roles that determine the default view after login:
- **supervisor** → `/dashboard` (后厨主管总控台)
- **chef** → `/kds` (KDS 后厨操作台)

Role is resolved from the session's `roles` array in `useAppStore`. Supervisor codes: `supervisor`, `manager`, `admin`, `后厨主管`, `SUPER_ADMIN`, `ADMIN`.

All routes use `hiddenLayout: true` — there is no shared AppLayout wrapper like the main frontend.

### Login Fallback
`loginWithPassword()` tries real backend login first. If the backend is unavailable, falls back to `mockLogin()` which returns a mock session. This allows terminal testing without a running backend.

### i18n
Locale stored in localStorage via `storageKeys.locale`. Supported: `zh-CN` (default), `en-US`. Locale files at `src/locales/zh-CN.ts` and `src/locales/en-US.ts`.

### Views
- `/login` — Login page
- `/dashboard` — Supervisor dashboard (temperature charts, ECharts)
- `/kds` — Kitchen Display System (cook task management)

### Path Alias & SCSS
`@` maps to `src/`. SCSS variables in `src/assets/styles/kds-theme.scss` are the primary design tokens — all kitchen terminal components use `$kds-*` variables (e.g., `$kds-bg`, `$kds-text`, `$kds-surface`, `$kds-border`, `$kds-green`, `$kds-amber`, `$kds-red`, `$kds-indigo` and their `-dim` variants). `variables.scss` is globally injected via Vite `additionalData`.

### Dual-Prefix BEM Pattern (BEM 双前缀模式)

KDS (chef) and supervisor views share detail components via a dual-prefix BEM pattern:
- KDS uses `chef-` prefix (e.g., `.chef-detail__alert-ack-btn`, `.chef-task`, `.chef-left`)
- Supervisor uses `sup-` prefix (e.g., `.sup-detail__alert-ack-btn`, `.sup-task`, `.sup-left`)
- SCSS mixin `detail-styles($pfx)` generates styles for both prefixes
- Shared components (`PaginationBar`, `OfflineIndicator`) accept a `prefix` prop (`'chef'` or `'sup'`)

When modifying task detail UI, always update styles for both prefixes.

### Store Architecture (Pinia)

**`cook` store** (`src/stores/modules/cook.ts`):
- `searchParams` ref holds query filters: `taskDate` (primary), `planDate` (fallback), `mealType`, `status`, `chefName`, `keyword`
- `search(params)` merges params and calls `refresh()` — always resets `pageNum` to 1
- `fetchList(silent)` — `silent=true` for auto-refresh (no loading state, uses `mergeList()` for in-place updates); `silent=false` for user-initiated searches (replaces entire list)
- `mergeList(newList)` performs differential updates: updates existing items via `Object.assign()`, adds new ones, removes stale ones. Prevents DOM flickering during auto-refresh.
- `loadTaskDetail(id)` loads full detail + timeline events
- `chefList` / `fetchChefList()` — employee list for chef assignment dropdown; parses both `records` and `list` from backend response
- `assignChefAction(id, payload)` — supervisor assigns chef to task; guard checks role + status (pending/in_progress only)
- `canAssignChef(task)` — guard: supervisor role + (pending or in_progress) + not historical
- `getTaskStartActionState(task)` — computes button state for start cooking with multi-layer validation: historical check → time window → material prep → chef assignment → device/sensor online. Returns `{ visible, enabled, reason, code }`.
- `confirmTempAbnormalAction(id)` — supervisor confirms temperature abnormality on a task
- `syncOfflineQueue()` replays `start` / `temperature` / `complete` actions in sequence, suppresses duplicate error toasts during replay, auto-retries failed items up to 3 times, and escalates recognized conflicts into `offlineStore.setConflict()`
- `resumeSyncAfterConflict()` clears the active conflict then resumes queue replay
- `resetSessionState()` clears list/detail/timeline/runtime cache and stops auto-refresh; use this instead of manually nulling pieces of cook state
- `currentTaskActionState` — computed combining start/complete action states for current task
- All action methods follow pattern: guard check → loading lock (`actionLoadingId`) → API call → success message → refresh → clear lock

**`offline` store** (`src/stores/modules/offline.ts`):
- Queue persists to localStorage for offline resilience, but is encrypted via `encrypt()` / `decrypt()` in `src/utils/crypto.ts`
- Records are bucketed into `pendingItems`, `syncingItems`, `failedItems`, and `conflictItems`
- `setConflict()` promotes a failed record into `conflictPending` state and drives `ConflictDialog`
- `resetSessionState()` clears queue + conflict state and removes `kitchen-terminal:offline-queue`
- `retryFailed(id)`/`removeFailed(id)`/`clearAllFailed()` for manual queue management

**`app` store** (`src/stores/modules/app.ts`):
- `online` ref tracks `navigator.onLine` status, updated by `TerminalLayout.vue` event listeners
- Session stored at localStorage key `kitchen-terminal:session` with `accessToken`, `refreshToken`, `userId`, `roles`
- `resetSessionState()` is the unified logout/session-expiry/account-switch cleanup entry; it clears session storage, offline queue state, and cook-store in-memory state before redirect/login

### API Layer

`src/api/modules/cook.ts` maps backend responses to frontend types via `mapTaskFromBackend()` and `mapTaskDetailFromBackend()`. Key mappings:
- Backend `assignedChefId` → Frontend `chefId` + `assignedChefId`
- Backend `assignedChefName` → Frontend `chefName` + `assignedChefName`
- Backend `materialPrepStatus` → Frontend `materialPrepStatus` (null/'pending_prep'/'prepared')
- Detail ingredients map `batchNo`, `traceBatchId` from outbound order linkage
- `outboundOrderNo` mapped at both task and detail level

`src/api/modules/system.ts` provides:
- `getOrgList()` — organization list for filter dropdowns
- `getEmployeeList()` — employee list for chef assignment; returns `records` array from paginated API

### Composables

- `useTempChart.ts` — ECharts-based temperature visualization with real-time updates
- `useTempSimulation.ts` — Simulated temperature data for testing without real sensors
- `useTempDeviation.ts` — Temperature deviation calculation (current vs target)
- `useAlertNotification.ts` — Browser notification + sound alert for abnormal tasks (temperature, AI violations, device/sensor offline)
- `useHlsVideo.ts` — HLS video stream playback via hls.js

### Shared Exception Flow

Offline and conflict handling now form a single shared UX path:
- `OfflineIndicator.vue` is the only sync-exception entry surface for both chef and supervisor views
- The banner distinguishes `待同步`, `同步中`, `同步异常`, and `冲突待处理`
- Expanded panel sections are driven from `offline` store selectors, not ad-hoc component filtering
- `ConflictDialog.vue` now shows `userAction`, derived server status, optional `conflictField`, optional `serverLatestValue`, and preserves `discard` / `force retry` actions
- If you change conflict behavior, update `src/utils/conflict.ts`, `src/stores/modules/offline.ts`, and `src/components/business/shared/ConflictDialog.vue` together

### Session Cleanup Contract

Session-scoped data is intentionally broader than auth tokens:
- `src/utils/storage.ts` defines `clearSessionScopedStorage()` for storage-level cleanup
- `src/stores/modules/app.ts` `resetSessionState()` is the canonical entry for logout/account-switch/session-expiry handling
- `src/api/index.ts` calls that same reset path before redirecting on refresh-token failure, response-code auth failures, or HTTP 401
- Do not reintroduce one-off `removeStorage(storageKeys.session)` logout paths; they leave stale offline/conflict/task state behind

### Auto-Refresh Pattern

Cook store uses `startAutoRefresh()` / `stopAutoRefresh()` with a 5-second interval. Always use `refresh(true)` (silent mode) during auto-refresh to avoid:
1. Setting `loading = true` which causes DOM destruction
2. Replacing the entire list array which causes visual flickering

### Real-Time Elapsed Timer (实时计时)

Both `KdsTaskDetail.vue` and `SupTaskDetail.vue` implement a 1-second interval timer for elapsed time display during `in_progress` status:

- `tickElapsed()` computes total seconds from `task.startTime` to `Date.now()`
- `formatElapsed()` renders as `M:SS` (under 1 hour) or `H:MM:SS` (1+ hour)
- Timer starts on `status === 'in_progress'` (via watch on `task.status`) and on `task.id` change
- Timer stops on `completed`/`archived` status, showing `actualDuration` converted to `M:SS` format
- `onUnmounted` cleans up the interval; SupTaskDetail also cleans up `document click` listener

### Vite Proxy Configuration

```typescript
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/hls': { target: 'http://localhost:8088', changeOrigin: true }
}
```

The gateway target can be overridden via `GATEWAY_TARGET` env variable (defaults to `http://localhost:8080`).

### Key Type Definitions (`src/types/cook.ts`)

- `CookTask` — list-level task with `taskDate` (primary date field), `planDate`, `mealType`, `materialPrepStatus`, `assignedChefId`, `assignedChefName`, temperature/device fields. `taskDate` is the actual execution date for each task; `planDate` is kept as fallback for backward compat.
- `CookTaskDetail` — extends with ingredients, steps, AI monitor records, outbound trace info
- `CookTaskAssignPayload` — `{ chefId, chefName }` for assignment API
- `ChefOption` — `{ id, name, position?, orgId?, orgName? }` for employee dropdown
- `CookAIMonitorRecord` — AI violation records parsed from `remark` JSON, keyed by `alertIndex`

### Material Prep Status Flow

`materialPrepStatus` on cook tasks follows: `null` → `'pending_prep'` → `'prepared'`.
- Set to `'pending_prep'` when recipe plan is approved (generates outbound order)
- Set to `'prepared'` when WMS approves the requisition outbound order
- Frontend blocks "Start Cooking" button when status is `'pending_prep'` (shows "食材尚未备齐")
- Backend also rejects start task if `pending_prep`
- UI displays ⏳ "待备料" (amber) or ✅ "已备料" (green) in task detail

### Token Expiry Warning

When the JWT token expires, the backend returns empty data (`total: 0`) instead of a 401 error, causing silent data loss. If task lists appear empty unexpectedly, check `kitchen-terminal:session` in localStorage and re-login. Mock tokens also expire after 2 hours.

### Current Validation Notes

- `npm run type-check` and `npm run build` currently pass.
- `npm run build` emits non-blocking warnings about large chunks and Dart Sass legacy JS API deprecation.
- Logging in as `admin / admin` currently resolves to the supervisor route (`/dashboard`); use the top-bar role switch to reach the chef view when validating shared components from the chef side.
