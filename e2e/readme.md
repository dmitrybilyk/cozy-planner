Playwright e2e tests

7 spec files covering all meaningful scenarios:

┌──────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│         File         │                                                                        What it tests                                                                         │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ auth.spec.ts         │ Sign-in page, demo login redirect, unauthenticated redirect, Google OAuth link                                                                               │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ planner.spec.ts      │ All nav buttons, mentor name, view toggle (День/План), calendar, history tab                                                                                 │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ sessions.spec.ts     │ 4 demo sessions today, compact/expanded toggle, confirmed status color, new session modal, copy button, history, past-date disables add                      │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ trainees.spec.ts     │ Demo trainees listed, detail expand, site link button, add form                                                                                              │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ locations.spec.ts    │ 3 demo locations with colors, 8 color swatches, no black swatch                                                                                              │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ availability.spec.ts │ Tab loads, demo intervals visible, share link section, tab switching                                                                                         │
├──────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ tour.spec.ts         │ Tour starts/shows overlay, demo data created via API, demo session appears, data deleted on end, next-step advances, skip dismisses, idempotent double-start │
└──────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  ---
How to run

cd /home/dik81/IdeaProjects/cozy-planner/e2e

# First time only — install dependencies and browser
npm install
npx playwright install chromium

# Run all tests (headless, requires app running on :8080)
npm test

# Run with visible browser
npm run test:headed

# Interactive UI mode (great for debugging)
npm run test:ui

# Run a single file
npx playwright test tests/sessions.spec.ts

# Run with HTML report
npm test -- --reporter=html

The app must be running at http://localhost:8080 before running tests. Override the URL with BASE_URL=http://other:port npm test.