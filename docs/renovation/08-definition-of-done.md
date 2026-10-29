# 08 — Definition of Done

## 8.1 Shippable v1

QueryForge v1 is shippable when ALL of the following are true:

### Backend
- [ ] All endpoints return RFC 7807 ProblemDetail on error.
- [ ] Jakarta Validation on every write DTO; field-level 400 on invalid input.
- [ ] Experiment templates are allow-listed; no command injection path exists.
- [ ] Runner has timeout watchdog and bounded stdout/stderr capture.
- [ ] Artifacts are written atomically with checksums.
- [ ] OpenAPI schema served at `/v3/api-docs` and matches this plan.
- [ ] Actuator health and metrics endpoints exposed and secured to localhost.
- [ ] CORS configured for allowed origins.
- [ ] All backend unit and integration tests pass.
- [ ] `mvn test` passes on clean checkout with Testcontainers.

### Frontend
- [ ] Design tokens implemented in `tokens.css`; no hard-coded colors or spacing in component CSS.
- [ ] Dark mode toggle works and persists across reloads.
- [ ] `ng2-charts` renders correct percentile data with axes, tooltips, and units.
- [ ] PlanExplorer renders tree and raw view; unknown nodes do not crash.
- [ ] All primary flows keyboard-operable.
- [ ] WCAG AA contrast verified.
- [ ] Playwright smoke tests pass (8/8).
- [ ] Responsive at 375px, 768px, 1024px, 1440px.
- [ ] No console errors in production build.

### Evidence
- [ ] README headline values map to stored artifacts.
- [ ] One baseline and one optimized measurement reproducible from fresh checkout.
- [ ] Run manifest contains all fields needed to judge comparability.
- [ ] Export report includes method, hypothesis, results, limitations, trade-offs.

### Process
- [ ] All deliverables in this plan have passing acceptance criteria.
- [ ] No scope beyond documented boundaries implemented without written change request.
- [ ] Code reviewed and merged to `main`.
- [ ] CHANGELOG updated with renovation summary.

## 8.2 Phase Exit Checklist

Each phase exit requires:
1. All tasks in phase marked complete.
2. All acceptance criteria verified and evidence recorded (test output, screenshots, metrics).
3. User review passed with score ≥ 9.5/10 on phase deliverables.
4. No regressions in prior phases.
5. Next phase dependencies satisfied.
