# Feature: /profile zone + user-admin hardening

Objective: build the self-service `/profile` area and close the real gaps in the user
admin module (activate/deactivate, password reset, project assignment, delete
protection), with availability becoming automatic instead of manually marked.

Branch: `feat/profile-user-admin` (from `dev`). Delivery strategy: `stacked-to-main`
(decided by the user 2026-11-03 after crossing the 400-line budget): one PR per work
unit, each PR targeting `dev` in order. Slice boundaries are recorded in the Tasks/Log.
Test runners: backend `cd backend && ./mvnw test`; frontend `cd frontend && ng test`
(pnpm is not on PATH in this environment — use `./node_modules/.bin/ng test`).
Strict TDD: RED observed before GREEN on every behavior change.

## Specs

### S1 — `/profile` is reachable and wired from the header
The `path: 'profile'` route MUST exist inside the authenticated shell (behind `authGuard`),
and the header user menu MUST navigate to `/profile`, replacing the dead `route="/settings"`
link. Acceptance: `ng test` green; the header entry navigates to `/profile`.

### S2 — Read-only identity card
The profile MUST show, read-only: name, email, NIE, phone, roles, the automatic
availability badge, current project and creation date. Source: `GET /api/users/me`
(a new self-scoped endpoint that never serialises `password`).

### S3 — Self-service contact editing
The profile MUST let the authenticated user edit only `phone` and `nie` through
`PUT /api/users/me`. `name`, `email`, `roles`, `active` and `availability` MUST NOT be
editable from there. Acceptance: a test proves a PUT with phone+nie persists and a PUT
attempting to change roles/active leaves them untouched.

### S4 — Password change
The profile MUST offer a change-password form (current + new + confirmation) that calls
`POST /api/users/me/password`. The backend MUST verify the current password, reject a
mismatch with 400, encode the new one and revoke the user's refresh tokens. Acceptance:
tests prove wrong current password → 400, correct → 204 and subsequent refresh fails.

### S5 — Availability is automatic only
Availability MUST be derived from clock events: an `ENTRY` sets `ON_SITE`, an `EXIT` sets
`AVAILABLE`. It MUST NOT be settable by hand anywhere — not in the profile and not in the
admin user modal. The four `UserAvailability` values stay in the enum for future logic.
Acceptance: tests prove registering an ENTRY persists `ON_SITE` and an EXIT persists
`AVAILABLE`, and that `update()` no longer applies `availability` from a PUT body.

### S6 — Current project
The profile MUST show the current project. An OPERATOR MUST be able to switch its own
current project among the projects it is assigned to (`project_operators`); an ADMIN MUST
be able to assign any user's current project. Acceptance: tests prove an operator can
switch to an assigned project and gets 403/400 for a project it is not assigned to.

### S7 — My clock entries
The profile MUST show the user's recent clock entries plus hours for the current week and
month, through a self-scoped `GET /api/clock-entries/me?start&end`. Acceptance: a test
proves an OPERATOR only ever receives its own entries.

### S8 — My sessions
The profile MUST list the user's active refresh tokens (id, createdAt, expiry) and let the
user close the others through `DELETE /api/users/me/sessions` with the current refresh
token in the body. Acceptance: tests prove the kept token stays usable and the others are
revoked.

### S9 — Activate / deactivate
The admin users module MUST expose an activate/deactivate action backed by
`PATCH /api/users/{id}/active`. `login()` MUST reject an inactive user. Guards MUST prevent
deactivating yourself and prevent leaving the system without at least one active ADMIN.
`UserService.update()` MUST stop treating a missing `active` field as `false` (primitive
`boolean` bug). Acceptance: tests prove inactive login → 403, self-deactivate → 409,
last-admin deactivate → 409, and a PUT omitting `active` keeps the current value.

### S10 — Admin password reset
The admin users module MUST offer a reset-password action that generates a temporary
password, returns it once, revokes the user's sessions and forces a change on next login
(`must_change_password`, new V15 column). Acceptance: tests prove the returned password
hashes correctly, the flag is set, sessions are revoked, and login with the temp password
sets the flag in `AuthResponse`.

### S11 — Admin assigns the current project
The admin users module MUST let an ADMIN set a user's current project. Acceptance: a test
proves the persisted `current_project_id` changes.

### S12 — Delete protections
`DELETE /api/users/{id}` MUST refuse to delete a user that has history (409, pointing at
deactivation) and MUST refuse self-deletion and last-admin deletion (409). Physical delete
stays only for users with no referencing rows. Acceptance: tests prove each refusal.

### S13 — The profile section is built to grow
The profile page MUST be structured as clearly separated sections with a reserved,
documented placeholder for future modules, so new logic can be added without reworking the
page. Acceptance: structural — the sections are distinct components/areas and the reserved
slot exists.

### S14 — Verification
Both full suites pass (`cd backend && ./mvnw test`, `cd frontend && ng test`) and
`ng build` succeeds.

## Assumptions (flagged, not silent)
- A1 — `must_change_password` is a new `BOOLEAN NOT NULL DEFAULT FALSE` column (V15).
  It is a migration, therefore high-risk item 1: it gets its own work unit and its own
  verification.
- A2 — Physical delete is kept only for users with no referencing rows; every user with
  history is deactivated instead. `clock_entries`, `budgets`, `invoices`, `payments`,
  `supplier_invoices` and `projects` reference `users` without cascade.
- A3 — Only `AVAILABLE` and `ON_SITE` are ever written for now; `PARTIALLY_AVAILABLE` and
  `UNAVAILABLE` remain dormant until the availability logic grows.
- A4 — Sessions list shows id/createdAt/expiry only; no device/user-agent data exists yet.
- A5 — `/profile` is available to every authenticated user, not just admins.
- A6 — The sidebar access fix applied during the diagnostic pass (bare `ADMIN` role names)
  rides along as the first work unit of this branch.
- A7 — A successful self-service password change clears `must_change_password`, so
  "force a change on next login" is a one-shot nudge and not a permanent lock. Flagged:
  the spec forces the change but never says who clears it.

## Tasks
- [x] T0 — Commit the sidebar access fix + `adminGuard` (diagnostic pass, already GREEN):
      RED `SidebarComponent > Usuarios entry > shows the entry to an ADMIN` failed with
      `expected false to be true`; fixed `roles.includes('ROLE_ADMIN')` → `hasRole('ADMIN')`.
      Full suite 191 passed / 2 pre-existing failures in `budget-editor.component.spec.ts`.
      Commit `d988a56`.
- [x] T1 — Backend self-profile (S2, S3, S7): `GET/PUT /api/users/me`,
      `GET /api/clock-entries/me` + RED/GREEN tests. Commit `920fa1e`.
      RED: compile failures on missing `UserProfileDTO`/`UserProfileUpdateRequest`
      (observed), then GREEN: full backend suite `Tests run: 280, Failures: 0,
      Errors: 0` (baseline 266 + 14 new), `BUILD SUCCESS`. Route: inline (small).
- [x] T2 — Backend password + sessions (S4, S8, S9 login part): `POST /api/users/me/password`,
      `GET/DELETE /api/users/me/sessions`, V15 `must_change_password`, login `active` check,
      `AuthResponse.mustChangePassword` + RED/GREEN tests. Split in two commits, the
      migration as its own high-risk unit: `e0b004b` (V15 + entity flag + inactive login
      403 + AuthResponse flag) and `5eddef7` (password change + sessions).
      RED: compile failures on missing `ChangePasswordRequest`/`SessionDTO` (observed),
      GREEN: focused 59 tests, full suite `Tests run: 292, Failures: 0, Errors: 0`.
      Migration verified outside the suite (tests run `flyway.enabled: false`): V1–V15
      applied on a throwaway local PostgreSQL, column `boolean NOT NULL DEFAULT false`,
      database dropped afterwards. Route: inline (small).
- [x] T3 — Backend automatic availability (S5): `ClockEntryService.register` sets
      `ON_SITE`/`AVAILABLE`; `update()` stops applying `availability` + RED/GREEN tests.
      Commit `20d6c71` (4 files, 69 lines). RED observed: 3 failures
      (`registerEntryShouldSetOnSite`, `registerExitShouldSetAvailable`,
      `shouldApplyEmailAndRolesButIgnoreAvailabilityOnUpdate`), GREEN: full suite
      `Tests run: 294, Failures: 0, Errors: 0`. Route: inline (small).
- [x] T4 — Backend admin (S9, S10, S11, S12): `PATCH /{id}/active`,
      `POST /{id}/password-reset`, `PUT /{id}/current-project`, delete guards, fix the
      `update()` primitive `active` bug + RED/GREEN tests.
      Commits `9fdc3c2` (S9+S10+S11+S6 actions, 640 lines, 316 green) and `f9d0fa5`
      (S12 delete guards, 350 lines). RED observed: compile failure
      `cannot find symbol: class UserUpdateRequest`, then GREEN full suite
      `Tests run: 328, Failures: 0, Errors: 0` (baseline 294 → +34). Route: inline
      (small, split into 2 reviewable slices).
- [ ] T5 — Frontend `/profile` (S1, S2, S3, S4, S6, S7, S8, S13): page + `ProfileService`
      + header link + tests.
- [ ] T6 — Frontend admin module (S9, S10, S11, S12): active toggle, reset-password modal,
      project assign, remove the availability select, deactivate instead of delete + tests.
- [ ] T7 — Verification (S14): backend suite, frontend suite, `ng build`.

## Log (running)
- L1 — user, verbatim: `vamos a crear la zona de /profile y al admin de usuarios primero vamos a planificarlo, entonces veme haciendo preguntas no quiero que crees nada aun`
- L2 — user, verbatim: `en el frontend no tengo acceso a ninguno de estos quiero que implementes el frontend y dime que ideas tienes para el profile y yo te dire cuales quiero y cuales no`
- L3 — user, verbatim: `me gustan las ideas 2,3,4(que tambien se haga automaticamente),5,6,7,8,9,10,11(pero antes me explicas un poco)`
- L4 — user, verbatim: `pon todas en el plan, con automaticamente me referia justo a la 1 mejor no marcarlo manualmente sino que solo sea automatico y deja esta seccion reservada por planeo hacerla crecer despues con mas logica`
- 2026-11-02 — mapping evidence (source-verified): the `/users` module already exists and
  works; the reported "no access" was the sidebar checking `ROLE_ADMIN` while the API sends
  bare `ADMIN` (V9 seeds `ADMIN`/`OPERATOR`, `AuthResponse` copies them, `JwtAuthFilter`
  adds `ROLE_` only for Spring authorities). Fixed in the diagnostic pass (T0).
- 2026-11-02 — deletion audit: only `refresh_tokens`, `user_roles` and `project_operators`
  cascade on user delete; `clock_entries`, `clock_corrections`, `budgets.created_by`,
  `invoices.created_by`, `payments.created_by`, `supplier_invoices.created_by` and
  `projects.uploaded_by/reported_by` do not, so a hard delete of a user with history raises
  a `DataIntegrityViolationException` that `GlobalExceptionHandler` maps to 500.
- 2026-11-02 — `AuthService.login()` never checks `user.active`, and `UserService.update()`
  applies `existing.setActive(updated.isActive())` on a primitive `boolean`, so a PUT that
  omits `active` silently deactivates the user. Both are in scope (S9).
- 2026-11-02 — `UserAvailability` is stored and displayed but no business logic consumes
  it; "automatic" availability therefore means hooking `ClockEntryService.register`.
- 2026-11-03 — T0 committed `d988a56`; T1 committed `920fa1e` (338 authored lines).
  Running total: 368 lines of the ~400 ask-on-risk budget → the chain-strategy question is
  due before the next commit. Full backend suite after T1: 280 tests green.
- 2026-11-03 — user decision (ask-on-risk): delivery is `stacked-to-main` — one PR per
  work unit, each PR targeting `dev` in order. Header updated accordingly.
- 2026-11-04 — T2 committed as `e0b004b` (high-risk unit: V15 migration + inactive login
  403 + AuthResponse flag) and `5eddef7` (password change + sessions), 401 authored lines.
  Full suite 292 green. Tests disable Flyway (`flyway.enabled: false`, `ddl-auto:
  create-drop`), so V15 was verified separately: V1–V15 applied on a throwaway local
  PostgreSQL database, `must_change_password boolean NOT NULL DEFAULT false` confirmed via
  information_schema, database dropped. The `e0b004b` tree is verified standalone in a
  scratch worktree.
- 2026-11-04 — known gap, deliberately out of T2 scope: `AuthService.refreshToken()` still
  does not check `user.active`, so a user deactivated mid-session can keep refreshing until
  its sessions are revoked (T4's reset revokes them). S9 only requires `login()` to reject;
  raising it here as a follow-up candidate, not as silent scope.
- 2026-11-04 — T3 committed `20d6c71` (69 authored lines). RED: 3 observed failures, then
  GREEN at 294. Interpretation of S5's "not settable by hand anywhere": the spec clarifies
  itself with its two named surfaces (the profile and the admin user modal), both UI, so
  the admin REST override `PUT /api/users/{id}/availability` stays for now and the UI
  select disappears in T6. If the endpoint should die too, that is a one-line follow-up —
  flagged rather than silently decided.
- 2026-11-05 — T4 committed as `9fdc3c2` (S9 activation guards, S10 password reset,
  S11 current-project assignment, S6 self-service switch, plus the `update()` boxed-Boolean
  fix) and `f9d0fa5` (S12 delete guards), 990 authored lines total across the two slices.
  RED observed: compile failure `cannot find symbol: class UserUpdateRequest`; GREEN full
  suite 328 tests green. `hasHistory()` covers all 15 non-cascading `users(id)` foreign
  keys and is proven against the H2 schema by `UserAdminIntegrationTest`. Scratch-backup
  lesson: a `/tmp` copy was wiped by the overnight cleaner mid-task — slice 1 was safe in
  its commit, and the slice-2 delta was reconstructed; future scratch backups stay inside
  the workspace (git-excluded), never `/tmp`.
