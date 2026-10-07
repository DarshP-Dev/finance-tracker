# finance-tracker

## Run locally

Start PostgreSQL, then run the backend in a PowerShell terminal:

```powershell
cd backend/backend
.\start-dev.ps1
```

The first run prompts for the local PostgreSQL password and stores it with Windows
user-bound encryption in the Git-ignored `.local` directory. The script also creates
a random development JWT secret and reuses it across restarts. Existing `DB_PASSWORD`
and `JWT_SECRET` environment variables take precedence.

The backend reads deployment configuration from environment variables:

- `DB_URL` (defaults to the local `finance_tracker_db` database)
- `DB_USERNAME` (defaults to `postgres` for local development)
- `DB_PASSWORD` (required)
- `JWT_SECRET` (required when the development script is not used)
- `CORS_ALLOWED_ORIGINS` (defaults to `http://localhost:3000`)
- `DB_DDL_AUTO`, `JPA_SHOW_SQL`, and `JPA_FORMAT_SQL` (optional)

Use separately managed secrets in production and set `DB_DDL_AUTO=validate` after
introducing database migrations.

In a second terminal, start the frontend:

```powershell
cd frontend/finance-tracker-frontend
npm run dev
```

Open http://localhost:3000. Keep both terminals running: the dashboard needs the
backend on port 8080. If the backend was stopped, start it and click **Retry** on
the dashboard. Sign in again if your previous session has expired.

## Recurring transactions

Authenticated requests to `/api/recurring-transactions` can create, list, read,
update, and delete recurring definitions. `PATCH /{id}/pause` and `PATCH /{id}/resume`
control whether a definition can generate. `POST /{id}/generate` creates exactly one
ordinary transaction and returns it for debugging.

`startDate` is the first occurrence: creating a definition also records one ordinary
transaction on that date and advances `nextOccurrence`. Automatic processing and later manual
generation use the current
`nextOccurrence` as the transaction date, then advances it. Monthly and yearly dates
stay anchored to the original day (January 31 goes to February 28 and then March 31).
Generation automatically deactivates a definition when the following occurrence
would be after `endDate`. Resuming does not backfill missed dates; manual generate
calls advance one occurrence at a time. Editing the schedule moves the next
occurrence past the most recently generated date so that date is never reused.

The backend checks active, due definitions daily at 3:00 a.m. in the configured zone
and catches up missed occurrences from `nextOccurrence`. Each definition is locked and
processed in its own database transaction. A failed definition does not stop the rest.
At most 100 occurrences per definition are generated per run by default; any remaining
due dates stay queued for the next run. Configure local or continuously running servers
with `RECURRING_SCHEDULER_ENABLED`, `RECURRING_SCHEDULER_CRON` (Spring's six-field cron),
`RECURRING_SCHEDULER_ZONE` (default `America/Toronto`), and
`RECURRING_SCHEDULER_MAX_CATCH_UP` (default `100`).

Vercel containers can stop between requests, so the in-process timer alone is not a
reliable production trigger. `vercel.json` configures a daily request at 08:00 UTC to
`/api/internal/recurring/process-due`. Set a long random `CRON_SECRET` in Vercel; the
endpoint refuses requests without its matching `Authorization: Bearer` header. Set
`RECURRING_SCHEDULER_ENABLED=false` on Vercel to use only that external trigger.
Vercel runs configured cron jobs only on production deployments. Verify the cron appears
in the Vercel dashboard after deployment and that its first invocation succeeds.

### Upcoming recurring transactions

The Transactions page has an **Upcoming** tab with period filters and projected
income, expenses, and net cash flow. The Dashboard shows the next five projected
occurrences, with a link to the full Upcoming view. These amounts cover known active
recurring schedules only; they are not an account balance or recorded transactions.

Authenticated `GET /api/recurring-transactions/upcoming` returns individual projected
occurrences, and `GET /api/recurring-transactions/forecast` returns period totals and
counts. Both accept optional ISO `from` and `to` dates. The default is today through
30 days later, inclusive. Ranges must be ordered and no longer than 12 months.
Projection begins at each schedule's `nextOccurrence`, uses the same calendar rules
as actual generation, includes occurrences on `endDate`, and excludes paused schedules.
These GET endpoints never generate transactions or update recurring definitions.

## Investments Phase 2: latest-price portfolio tracking

The existing `investments` table stores individual purchase lots: ticker, fractional
shares (six decimals), purchase price (four decimals), and purchase date. Multiple
lots per ticker are supported. Phase 2 preserves every lot and reuses
`InvestmentService.getHoldings()` for total shares, cost basis, purchase count, and
weighted average purchase price. No database migration or stored price column is required.

Authenticated `GET /api/investments/portfolio` returns `{ summary, holdings }`.
Existing purchase CRUD and `/api/investments/holdings` remain compatible. The user
is always resolved from JWT authentication; no frontend user ID selects holdings.

`InvestmentPortfolioService` reads aggregated lots, finishes the database read,
then uses `MarketQuoteService` and the `MarketDataProvider` abstraction. The
`TwelveDataMarketDataProvider` implementation batches unique tickers via `/quote`,
mapping `close`, currency, optional previous close, and provider timestamp into an
internal `MarketQuote`. Authorization uses a backend-only header; the key never
appears in browser code, API responses, logs, or provider URLs.

### Configure Twelve Data

Create a key in your [Twelve Data dashboard](https://twelvedata.com/account/api-keys)
after registering an account. Set these in the same PowerShell terminal used to
launch the backend, or in your hosting environment's secret settings:

```powershell
$env:MARKET_DATA_ENABLED = 'true'
$env:TWELVE_DATA_API_KEY = 'your-private-key'
.\start-dev.ps1
```

Run this from `backend/backend`. `MARKET_DATA_API_KEY` is an alternative key variable.
Spring does not automatically load a root `.env` file. Do not put the key in
`NEXT_PUBLIC_*` variables or checked-in configuration. `.env`, `.env.*`, `.local/`,
and private Spring `application-local.*` files are ignored by Git.

Optional configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `MARKET_DATA_ENABLED` | `false` | Optional integration; missing keys never prevent startup |
| `MARKET_DATA_PROVIDER` | `twelve-data` | Selected adapter |
| `MARKET_DATA_CACHE_DURATION` | `10m` | Quote freshness TTL, bounded to 1–60 minutes |
| `MARKET_DATA_STALE_DURATION` | `24h` | Maximum cached age after a transient failure, bounded to 48 hours |
| `MARKET_DATA_TIMEOUT` | `6s` | Full-response deadline, bounded to 0.5–15 seconds |
| `MARKET_DATA_CREDITS_PER_MINUTE` | `8` | Process-local symbol-credit guard |
| `MARKET_DATA_CREDITS_PER_DAY` | `800` | Process-local daily guard (UTC) |
| `MARKET_DATA_MAX_CACHE_SYMBOLS` | `512` | Bounded shared symbol cache |

Twelve Data documents [one credit per symbol, even in batches](https://support.twelvedata.com/en/articles/5203360-batch-api-requests).
Its [current Basic plan](https://twelvedata.com/pricing) lists 8 credits/minute and
800/day; exchange access, display licensing, and higher quotas depend on your plan.
Basic is listed for internal non-display usage; check the provider's display-data
entitlements for your intended deployment. Configure matching quotas if your plan
differs. The app never polls prices or automatically retries a failed provider call.
Uncached symbols exceeding the guard remain rate limited until a later refresh.
Limits/cache are process-local; multiple hosting instances share the provider's
account quota but do not share this local guard. Failed lookups are cached for 60 seconds.

Prices are described as **latest available**, not guaranteed real-time. They may
be delayed or the last close depending on the instrument, entitlement, and market
session. The current purchase model has no currency field and the app displays USD:
only USD stock/ETF listings are valued. Non-USD quotes and unsupported instruments
stay unpriced; there is no FX conversion. When quote responses omit asset type, the
adapter requires a recognized US equity listing MIC. Symbols are trimmed,
uppercased, validated, and URL-encoded; an unresolved ticker never gets a fake zero price.

### Calculations, availability, and integration

All arithmetic uses backend BigDecimal values. Cost basis is the sum of shares ×
purchase price across lots; weighted average cost is total cost ÷ total shares.
Market value is aggregated shares × latest price. Money is rounded to cents at the
holding boundary; portfolio totals sum those holdings. Unrealized gain/loss is
market value − cost basis; return is gain/loss ÷ cost basis × 100, omitted for zero
cost. Market allocation is holding market value ÷ complete portfolio value × 100,
omitted for a zero or incomplete valuation. Percentage values have two backend
decimals and one display decimal. This does not include sales, dividends, fees,
stock-split adjustments, or realized returns: accuracy depends on recorded open lots.

Quotes are shared by normalized symbol across users/pages, with concurrent misses
coalesced into one batch. Private positions are never shared or sent to the provider.
After a transient refresh failure, cached prices up to the stale-age limit may be
displayed as `STALE`, preserving their original retrieval timestamp. Market timestamps
are used only when supplied by the provider. Partial quotes remain visible per
holding, but complete portfolio totals, return, and allocation are null until every
holding has a quote. Stored shares, averages, and cost basis always remain visible.

Investments displays summary cards, enriched holdings, a market-value allocation
chart using existing Recharts, and unchanged purchase-history create/edit/delete
controls. Manual Refresh respects the backend cache. Dashboard and Analytics reuse
the same backend portfolio response. Dashboard's `summary.investmentValue` is market
value when complete, otherwise recorded cost, with availability labels in the UI.
Analytics preserves selected-period purchase contributions and clearly separates
them from the current all-holdings snapshot and market allocation.

Financial Insights adds a single curated `investment-performance` insight only for
a complete, fresh portfolio. Historical selected periods never imply past performance
from current quotes. Stale, partial, missing, or disabled prices retain the recorded-cost
insight instead. Gemini continues receiving only deterministic insight templates,
never raw holdings; valuation wording is allowed only for the canonical `Tracked portfolio`
source. Invented numbers, realized-profit claims, and recommendations remain rejected.

Automated tests use mocks or loopback providers, never real market-data requests.
Real PostgreSQL/JWT integration tests verify purchase CRUD, ownership, aggregation,
read-only portfolio requests, cross-page quote reuse, and absence of an open DB
transaction during provider retrieval. Manual provider-account verification still
requires your own key and exchange entitlements. Test valid/unknown tickers, positive
and negative returns, provider outages, mobile cards, Dashboard, Analytics, and the
Gemini summary after enabling your configuration.

## Financial Insights Phase 1

Authenticated `GET /api/financial-insights` returns `{ generatedAt, insights }`.
The server resolves the user from JWT authentication; callers cannot choose a user ID.
Insights are computed dynamically using existing read-only Analytics
and recurring forecast services. No insights are saved and no schedules are advanced.
The Phase 1 rules remain deterministic; later optional Gemini and market-data
integrations are described below. No financial recommendations are generated.

Each insight has a stable `key`, `type`, `severity` (`INFO`, `POSITIVE`, `WARNING`),
`title`, `message`, optional numeric `metricValue` / `comparisonValue`, optional
`category`, inclusive `from` / `to` dates, and `generatedAt`. All-time investment
insights have null date bounds. Numeric units depend on the key:

| Key | metricValue | comparisonValue |
| --- | --- | --- |
| `spending-change`, `category-*` | Signed percentage change | Previous-month expense amount |
| `budget-*` | Budget percentage used | Remaining money (negative when exceeded) |
| `savings-rate` | Current savings percentage | Previous savings percentage, if income existed |
| `income-expenses` | Income minus expenses | Current savings percentage, if income exists |
| `recurring-summary` | Forecast recurring income minus expenses | Forecast recurring expenses |
| `upcoming-largest-expense` | Scheduled expense amount | null |
| `recorded-investments` | All-time recorded purchase cost | null |
| `investment-performance` | Unrealized portfolio gain/loss from complete fresh quotes | Recorded cost basis |

Rules and limits:

- Spending and savings compare the **current month to date** with the **full previous
  calendar month**, matching existing Analytics periods. Messages say this explicitly;
  a lower total early in the month does not establish a lower daily spending pace.
  Future-dated recorded transactions are excluded from month-to-date totals.
- Overall spending changes need at least 10%. Category changes need a previous
  amount of at least $50, an absolute change of at least $25, and at least 10% change.
  Up to three categories are selected by absolute monetary change, with stable ties.
- Budget values reuse Analytics' full-calendar-month recorded spending (including
  future-dated records). At least 80% usage warns; 100% is fully used; greater than
  100% reports the amount exceeded. Pace warns at at least 40% usage and at least
  20 percentage points ahead of elapsed calendar days. It reports recorded usage,
  not a prediction. At most three budget warnings are selected.
- Savings rate is `(income - expenses) / income * 100`, only when income is positive.
  A change of at least five percentage points gets a comparison. Negative cash flow
  and negative savings share a warning instead of repeating the same shortfall.
- Recurring projections cover exactly 30 inclusive dates: today through today + 29.
  Known recurring income, expenses and net cash flow share one insight. The largest
  upcoming expense within today through today + 6 may also be surfaced; ties use
  date then schedule ID. Paused definitions are excluded by the existing forecast
  logic. The engine does not estimate unscheduled future spending. A recurring
  expense ratio is deliberately omitted because no total expected-expense
  denominator is available.
- Investments describe distinct recorded tickers and purchase cost only, never
  current market values, returns or recommendations.
- At most eight insights: exceeded / near-limit / pace budget warnings, negative
  cash flow, category increases, overall spending increases, savings changes,
  upcoming expenses, known recurring forecast, positive spending changes, then
  general savings and investment information. Budget warnings suppress spending
  change messages for that category. A category change also suppresses the overall
  change when that category accounts for all spending in both periods.
  Equal-priority ties use descending metric
  magnitude then stable key. No health score or randomness is used.
- Money uses BigDecimal with two display decimals, percentages use one display
  decimal, and dates use the existing application Clock (America/Toronto by default).
  Generated timestamps naturally change across requests; rule output remains
  deterministic for the same data and date. Missing baselines, income, budgets,
  investments or recurring schedules are skipped; a new user can receive `[]`.

Backend tests include deterministic rule tests and real PostgreSQL / JWT HTTP
tests that compare all financial fields before and after GET requests. Integration
fixtures use unique test users and remove only their own records afterward.
### Selected reporting periods

Analytics and the Dashboard preview pass their selected reporting period to insights.
Both `GET /api/financial-insights` and `GET /api/financial-insights/summary` accept
the same `period`, `startDate`, and `endDate` parameters as Analytics. Dates are
required only for `CUSTOM`; requests without parameters preserve the original
current-month overview and next-30-days recurring forecast described above.

- `THIS_MONTH` compares month to date with the full previous calendar month.
- `LAST_MONTH` compares that complete month with the preceding calendar month.
- `THIS_YEAR` compares year to date with the same date range in the previous year.
- Rolling and custom ranges compare with the immediately preceding range of the
  same inclusive length. Cards explicitly identify both compared ranges.
- Savings, cash flow, category totals, and recorded investment purchases use the
  selected range. Investment purchase totals are not market valuations.
- Budgets remain monthly and identify the calendar month at the end of the range.
  Partial historical month windows omit whole-month budget insights. Historical
  budgets do not use today's pace; multi-month spending changes are not suppressed
  by a warning about only one month's budget.
- Historical reports omit future recurring forecasts. Ranges including future
  dates can show known recurring activity within their future portion, capped at
  30 inclusive dates. Actual transaction totals and scheduled forecasts stay distinct.
- AI summaries use exactly these selected-period insights. The cache fingerprint
  includes the period and custom dates, and a period change never displays a cached
  narrative for another report. Switching during the per-user cooldown can require
  using Retry after the countdown expires.

The frontend clears old cards and summary state when the selected range changes
and cancels obsolete requests. Financial calculations remain entirely on the backend.

## Deploy with Vercel and Neon

Financial Insights Phase 3 is optional; its configuration is described below.

The repository is configured as one Vercel Services project. Vercel serves the
Next.js application from `frontend/finance-tracker-frontend` and runs the Spring
Boot API from `backend/backend` as a Java 21 container. Requests under `/api/*`
are routed to Spring Boot; all other requests are routed to Next.js.

The production database should be a Neon PostgreSQL database using Neon's pooled
endpoint. Configure these variables in Vercel without committing their values:

- `DB_URL`: pooled JDBC URL in the form
  `jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require&channelBinding=require`
- `DB_USERNAME`: Neon database role
- `DB_PASSWORD`: Neon database password
- `JWT_SECRET`: stable random JWT signing secret of at least 32 bytes
- `DB_DDL_AUTO`: use `update` for the initial beta deployment; move to `validate`
  after adding managed database migrations

Vercel supplies `PORT`; the backend continues to use port 8080 when `PORT` is not
set. The frontend uses same-origin `/api/...` requests in production and continues
to use `http://localhost:8080` during local development. Set
`NEXT_PUBLIC_API_BASE_URL` only when an explicit API origin override is needed.

The datasource pool defaults are conservative for beta traffic and can be
overridden through Spring's standard environment variables:

- `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` (default `5`)
- `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` (default `0`)
- `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` (default `30000` ms)
- `SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT` (default `60000` ms)
- `SPRING_DATASOURCE_HIKARI_MAX_LIFETIME` (default `600000` ms)

`CORS_ALLOWED_ORIGINS` continues to default to `http://localhost:3000` for local
development. Production and preview deployments use the shared Vercel origin.

## Financial Insights Phase 3: optional Gemini summary

`GET /api/financial-insights/summary` uses the existing JWT identity and canonical
`FinancialInsightsService`. It never accepts a user ID or writes financial records.
`GET /api/financial-insights` remains the canonical deterministic calculation layer.
The Analytics page adds an independently loaded summary above the source cards;
the Dashboard uses deterministic cards only. There is no chatbot or AI history table.

Gemini sits behind `InsightSummaryClient`; the implementation uses Java's HTTP
client and the [Gemini generateContent API](https://ai.google.dev/api/generate-content)
with [structured JSON output](https://ai.google.dev/gemini-api/docs/generate-content/structured-output).
The configurable default is `gemini-3.5-flash-lite`. No provider SDK is required.
The only user financial data sent is the curated list of **type, severity, title,
and message**. Upcoming recurring descriptions are replaced with generic wording.
Raw transaction history, account identity, email, IDs, JWTs, repositories, metrics
not already present in messages, and investment tickers are not sent.

System instructions are separate from the serialized insight data, which is
explicitly untrusted. The prompt asks for 2–4 sentences, approximately 50–120 words,
without recalculation, invented numbers, recommendations, bank-access claims,
market-price claims absent from a canonical portfolio insight, or predictions beyond known recurring schedules. The server
requires one JSON `summary` string, rejects truncation/refusals/malformed output,
caps length, checks numerical values including signs and units against source
messages, and rejects common advice/markup/link patterns. These checks reduce
risk; they cannot prove semantic accuracy. The source cards remain authoritative,
and the UI explicitly labels AI-generated wording and its limitations.

Configure **backend-only environment variables**, then restart the backend:

| Variable | Default / purpose |
| --- | --- |
| `AI_INSIGHTS_ENABLED` | `false`; enable explicitly with `true` |
| `GEMINI_API_KEY` | Required when enabled; obtain from Google AI Studio |
| `AI_INSIGHTS_API_KEY` | Optional explicit override for `GEMINI_API_KEY` |
| `AI_INSIGHTS_MODEL` | `gemini-3.5-flash-lite`; use a compatible available text model |
| `AI_INSIGHTS_TIMEOUT` | `8s`; allowed range 100ms–30s |
| `AI_INSIGHTS_MAX_OUTPUT_LENGTH` | `1000` characters; allowed range 100–2000 |
| `AI_INSIGHTS_MAX_OUTPUT_TOKENS` | `384`; allowed range 128–1024 |
| `AI_INSIGHTS_CACHE_TTL` | `10m`; bounded to 1s–1h |
| `AI_INSIGHTS_MIN_REQUEST_INTERVAL` | `30s`; bounded to 1–300s |
| `AI_INSIGHTS_MAX_CACHE_USERS` | `256`; bounded to 1–10000 |
| `AI_INSIGHTS_ENDPOINT` | `https://generativelanguage.googleapis.com/v1beta/models`; server-controlled base URL, normally unchanged |

Never put credentials in `NEXT_PUBLIC_*`, source files, committed `.env` files or
frontend code. No real key is included in this repository. The endpoint requires
HTTPS except loopback HTTP for local provider tests; redirects are not followed.
Review Google's data handling and billing terms before enabling it for real users.

Responses include `summary`, `generatedAt`, `sourceInsightCount`, `aiGenerated`,
`status` and `retryAfterSeconds`. Statuses:

- `DISABLED`: no provider call; Analytics hides the summary.
- `EMPTY`: insufficient-data message, without a provider call.
- `DETERMINISTIC`: a single source insight displayed directly, without AI labeling or a provider call.
- `AVAILABLE`: validated AI summary.
- `UNAVAILABLE`: missing/invalid configuration, timeout, provider/network error,
  refusal or invalid output; source insights remain usable.
- `COOLDOWN`: data changed while requests are throttled; stale text is withheld.

There is one provider request for the whole curated set, never one per card, and
no automatic retries. Identical current insight content/date reuses a bounded,
per-user in-memory cache. Each GET re-reads canonical insights; changed content
invalidates the old summary, subject to the short cooldown. Generated timestamps
do not invalidate the cache. Concurrent requests for one user share a call.
Failures are cached for the cooldown. Analytics fetches once per mount/explicit
refresh, never on ordinary rerenders or tab focus, and has a retry countdown.
Refresh rechecks data but does not bypass server caching or throttling. Browser
responses use `Cache-Control: no-store`. No financial text, prompts, credentials
or provider error bodies are logged by this integration.

Cache/throttle state is process-local and disappears on restart; separate Vercel
instances have separate limits. This is a Phase 3 cost guard, not a distributed
billing quota. Set provider account quotas appropriate to your deployment.
No database connection is held while waiting for provider HTTP.

Backend verification uses mocked providers plus real loopback HTTP and real
PostgreSQL/JWT integration tests, never paid requests. After setting your own key,
manually log in, open Analytics, compare the summary with its source cards, test
Refresh after changing financial data, and check provider failure/disabled behavior.
Only enabling your own configuration verifies your real Gemini account/model access.
