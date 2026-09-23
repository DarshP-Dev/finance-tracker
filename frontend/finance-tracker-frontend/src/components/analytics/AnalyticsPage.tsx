"use client";

import { useEffect, useState } from "react";
import { ArrowUp, ArrowUpRight, CalendarDays, TrendingDown, TrendingUp } from "lucide-react";
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { fetchAnalytics } from "@/lib/api";
import { formatCurrency } from "@/components/transactions/formatters";
import { Input } from "@/components/ui/input";
import type { AnalyticsData, AnalyticsPeriod } from "@/types/analytics";

const periods: { value: AnalyticsPeriod; label: string }[] = [
  { value: "THIS_MONTH", label: "This Month" },
  { value: "LAST_MONTH", label: "Last Month" },
  { value: "LAST_3_MONTHS", label: "Last 3 Months" },
  { value: "LAST_6_MONTHS", label: "Last 6 Months" },
  { value: "THIS_YEAR", label: "This Year" },
  { value: "CUSTOM", label: "Custom Range" },
];
const colors = ["#ff5a1f", "#f79009", "#15151b", "#f7b993", "#6d5e68", "#12b76a", "#d92d20"];

function monthLabel(month: string) {
  const [year, value] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", { month: "short", year: "numeric" }).format(new Date(year, value - 1, 1));
}

function categoryLabel(value: string) {
  return value.toLowerCase().split("_").map((part) => part[0].toUpperCase() + part.slice(1)).join(" ");
}

function dateLabel(date: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(new Date(`${date}T00:00:00`));
}

function getErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = error.response as { data?: { message?: string } } | undefined;
    if (response?.data?.message) return response.data.message;
  }
  return error instanceof Error ? error.message : "Could not load analytics.";
}

function Panel({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <section className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
    <h2 className="text-lg font-semibold text-[#151515]">{title}</h2>
    <p className="mt-1 text-sm text-[#77717d]">{description}</p>
    <div className="mt-5">{children}</div>
  </section>;
}

function Empty({ message }: { message: string }) {
  return <div className="flex min-h-48 items-center justify-center rounded-xl bg-[#faf9fa] px-5 text-center text-sm text-[#77717d]">{message}</div>;
}

function Metric({ label, value, note, icon, tone = "orange" }: { label: string; value: string; note?: string; icon?: React.ReactNode; tone?: "green" | "red" | "orange" }) {
  const iconStyle = tone === "green" ? "bg-[#ecfdf3] text-[#027a48]" : tone === "red" ? "bg-[#fef3f2] text-[#b42318]" : "bg-[#fff3ed] text-[#ff5a1f]";
  return <div className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
    <p className="text-xs font-semibold uppercase tracking-wide text-[#77717d]">{label}</p>
    <div className="mt-2 flex items-center gap-2"><p className="text-2xl font-semibold text-[#151515]">{value}</p>{icon && <span aria-hidden="true" className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${iconStyle}`}>{icon}</span>}</div>
    {note && <p className="mt-1 text-xs text-[#77717d]">{note}</p>}
  </div>;
}

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>("THIS_MONTH");
  const [customRange, setCustomRange] = useState<{ startDate: string; endDate: string } | null>(null);
  const [isDatePanelOpen, setIsDatePanelOpen] = useState(false);
  const [draftStartDate, setDraftStartDate] = useState("");
  const [draftEndDate, setDraftEndDate] = useState("");
  const [dateError, setDateError] = useState("");
  const [data, setData] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    const timeout = window.setTimeout(() => {
      void fetchAnalytics(period, period === "CUSTOM" ? customRange ?? undefined : undefined).then((result) => {
        if (active) { setData(result); setError(""); setLoading(false); }
      }).catch((cause: unknown) => {
        if (active) { setData(null); setError(getErrorMessage(cause)); setLoading(false); }
      });
    }, 0);
    return () => { active = false; window.clearTimeout(timeout); };
  }, [period, customRange, retry]);

  const selectPeriod = (value: AnalyticsPeriod) => {
    if (value === "CUSTOM") {
      openDatePanel();
      return;
    }
    setLoading(true);
    setData(null);
    setError("");
    setCustomRange(null);
    setIsDatePanelOpen(false);
    setPeriod(value);
  };

  const openDatePanel = () => {
    setDraftStartDate(customRange?.startDate ?? data?.startDate ?? "");
    setDraftEndDate(customRange?.endDate ?? data?.endDate ?? "");
    setDateError("");
    setIsDatePanelOpen(true);
  };

  const applyDateRange = () => {
    if (!draftStartDate || !draftEndDate || draftStartDate > draftEndDate) {
      setDateError("Enter a start date on or before the end date.");
      return;
    }
    setCustomRange({ startDate: draftStartDate, endDate: draftEndDate });
    setPeriod("CUSTOM");
    setLoading(true);
    setData(null);
    setError("");
    setDateError("");
    setIsDatePanelOpen(false);
  };

  const range = data ? `${dateLabel(data.startDate)} – ${dateLabel(data.endDate)}` : "";
  const hasCashFlow = data?.incomeVsExpenses.some((item) => item.income !== 0 || item.expenses !== 0);
  const hasTrend = data?.spendingTrend.months.some((item) => item.amount !== 0);
  const hasContributions = data?.investments.contributions.some((item) => item.amount !== 0);

  return <div id="analytics-top" className="grid gap-5 py-6">
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-3xl font-semibold text-[#151515]">Analytics</h1>
        <p className="mt-2 text-sm text-[#77717d]">Your transactions, budgets, and investment purchases in one place.</p>
      </div>
      <div className="relative flex flex-wrap items-end gap-2 sm:flex-nowrap">
        <label className="grid gap-1 text-xs font-semibold uppercase tracking-wide text-[#77717d]">
          Period
          <select aria-label="Analytics period" value={period} onChange={(event) => selectPeriod(event.target.value as AnalyticsPeriod)} className="h-11 min-w-44 rounded-xl border border-[#e4e0e7] bg-white px-3 text-sm font-medium text-[#151515]">
            {periods.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button type="button" aria-expanded={isDatePanelOpen} onClick={() => isDatePanelOpen ? setIsDatePanelOpen(false) : openDatePanel()} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#151515] shadow-sm transition hover:border-[#ff5a1f]">
          <CalendarDays size={16} />
          {data ? `${dateLabel(data.startDate)} – ${dateLabel(data.endDate)}` : "Date range"}
        </button>
        {isDatePanelOpen && <div className="absolute right-0 top-[calc(100%+10px)] z-40 w-full rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-xl sm:w-[340px]">
          <h2 className="text-sm font-semibold text-[#151515]">Date range</h2>
          <p className="mt-1 text-xs text-[#77717d]">Choose a custom analytics window.</p>
          <div className="mt-4 grid gap-3">
            <label className="grid gap-1.5 text-sm font-semibold text-[#46404b]">Start date<Input type="date" value={draftStartDate} onChange={(event) => { setDraftStartDate(event.target.value); setDateError(""); }} /></label>
            <label className="grid gap-1.5 text-sm font-semibold text-[#46404b]">End date<Input type="date" value={draftEndDate} onChange={(event) => { setDraftEndDate(event.target.value); setDateError(""); }} /></label>
            {dateError && <p role="alert" className="text-xs font-medium text-[#b42318]">{dateError}</p>}
            <button type="button" onClick={applyDateRange} className="mt-1 h-10 rounded-xl bg-[#ff5a1f] text-sm font-semibold text-white transition hover:bg-[#e64d17]">Apply range</button>
          </div>
        </div>}
      </div>
    </div>

    {loading ? <div role="status" aria-label="Loading analytics" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: 4 }).map((_, index) => <div key={index} className="h-28 animate-pulse rounded-2xl border border-[#e4e0e7] bg-white" />)}
    </div> : error ? <section className="rounded-2xl border border-[#f1c6c1] bg-white p-6">
      <p role="alert" className="text-sm text-[#b42318]">{error}</p>
      <button type="button" onClick={() => { setLoading(true); setRetry((value) => value + 1); }} className="mt-4 rounded-xl bg-[#15151b] px-4 py-2 text-sm font-semibold text-white">Try again</button>
    </section> : data && <>
      <p className="text-sm font-medium text-[#77717d]">Selected period: {range}</p>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Total Income" value={formatCurrency(data.overview.totalIncome)} note={range} icon={<TrendingUp size={18} />} tone="green" />
        <Metric label="Total Expenses" value={formatCurrency(data.overview.totalExpenses)} note={range} icon={<TrendingDown size={18} />} tone="red" />
        <Metric label="Net Cash Flow" value={formatCurrency(data.overview.netCashFlow)} note={range} icon={data.overview.netCashFlow >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />} tone={data.overview.netCashFlow >= 0 ? "green" : "red"} />
        <Metric label="Total Invested" value={formatCurrency(data.overview.totalInvested)} note={`Purchase cost · ${range}`} icon={<ArrowUpRight size={18} />} />
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Spending by Category" description={`Expense transactions · ${range}`}>
          {data.spendingByCategory.length === 0 ? <Empty message="No expenses in this period." /> : <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] sm:items-center">
            <div className="h-64 min-w-0"><ResponsiveContainer width="100%" height="100%"><PieChart>
              <Pie data={data.spendingByCategory} dataKey="amount" nameKey="category" innerRadius={55} outerRadius={90}>
                {data.spendingByCategory.map((item, index) => <Cell key={item.category} fill={colors[index % colors.length]} />)}
              </Pie>
              <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            </PieChart></ResponsiveContainer></div>
            <div className="grid max-h-64 gap-2 overflow-y-auto text-sm">
              {data.spendingByCategory.map((item, index) => <div key={item.category} className="flex items-center justify-between gap-2">
                <span className="flex min-w-0 items-center gap-2"><span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: colors[index % colors.length] }} /><span className="truncate">{categoryLabel(item.category)}</span></span>
                <span className="shrink-0 font-semibold">{formatCurrency(item.amount)} · {item.percentage}%</span>
              </div>)}
            </div>
          </div>}
        </Panel>
        <Panel title="Income vs Expenses" description={`Monthly totals · ${range}`}>
          {!hasCashFlow ? <Empty message="No income or expense transactions in this period." /> : <div className="h-64 min-w-0"><ResponsiveContainer width="100%" height="100%"><BarChart data={data.incomeVsExpenses} margin={{ left: 0, right: 8 }}>
            <CartesianGrid stroke="#eee8e2" vertical={false} />
            <XAxis dataKey="month" tickFormatter={monthLabel} tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={(value) => `$${value}`} tick={{ fontSize: 11 }} width={55} />
            <Tooltip labelFormatter={(label) => monthLabel(String(label))} formatter={(value) => formatCurrency(Number(value))} />
            <Legend /><Bar name="Income" dataKey="income" fill="#12b76a" radius={[5, 5, 0, 0]} /><Bar name="Expenses" dataKey="expenses" fill="#ff5a1f" radius={[5, 5, 0, 0]} />
          </BarChart></ResponsiveContainer></div>}
        </Panel>
      </div>

      <Panel title="Monthly Spending Trend" description={`Expenses · ${monthLabel(data.spendingTrend.startMonth)} – ${monthLabel(data.spendingTrend.endMonth)} · independent of selected period`}>
        {!hasTrend ? <Empty message="No expense history in the last six months." /> : <div className="h-64 min-w-0"><ResponsiveContainer width="100%" height="100%"><LineChart data={data.spendingTrend.months} margin={{ left: 0, right: 8 }}>
          <CartesianGrid stroke="#eee8e2" vertical={false} /><XAxis dataKey="month" tickFormatter={monthLabel} tick={{ fontSize: 11 }} /><YAxis tickFormatter={(value) => `$${value}`} tick={{ fontSize: 11 }} width={55} />
          <Tooltip labelFormatter={(label) => monthLabel(String(label))} formatter={(value) => formatCurrency(Number(value))} />
          <Line type="monotone" dataKey="amount" name="Expenses" stroke="#ff5a1f" strokeWidth={3} dot={{ r: 4 }} />
        </LineChart></ResponsiveContainer></div>}
      </Panel>

      <div className="flex flex-col gap-1"><h2 className="text-xl font-semibold text-[#151515]">Budget Performance</h2><p className="text-sm text-[#77717d]">{monthLabel(data.budgets.month)} · monthly limits and spending in budgeted categories</p></div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Total Budgeted" value={formatCurrency(data.budgets.totalBudgeted)} />
        <Metric label="Budgeted Spending" value={formatCurrency(data.budgets.budgetedSpending)} />
        <Metric label="Remaining Budget" value={formatCurrency(data.budgets.remainingBudget)} />
        <Metric label="Over-Budget Categories" value={String(data.budgets.overBudgetCategories)} />
      </div>
      {data.budgets.categories.length === 0 ? <Panel title="Budget vs Actual" description={monthLabel(data.budgets.month)}><Empty message="No budgets for this month yet." /></Panel> : <>
        <Panel title="Budget vs Actual" description={`Budgeted categories only · ${monthLabel(data.budgets.month)}`}>
          <div className="h-72 min-w-0"><ResponsiveContainer width="100%" height="100%"><BarChart data={data.budgets.categories} margin={{ left: 0, right: 8 }}>
            <CartesianGrid stroke="#eee8e2" vertical={false} /><XAxis dataKey="category" tickFormatter={categoryLabel} tick={{ fontSize: 10 }} /><YAxis tickFormatter={(value) => `$${value}`} tick={{ fontSize: 11 }} width={55} />
            <Tooltip labelFormatter={(label) => categoryLabel(String(label))} formatter={(value) => formatCurrency(Number(value))} /><Legend />
            <Bar name="Budget" dataKey="monthlyBudget" fill="#15151b" radius={[5, 5, 0, 0]} /><Bar name="Actual" dataKey="actualSpending" fill="#ff5a1f" radius={[5, 5, 0, 0]} />
          </BarChart></ResponsiveContainer></div>
        </Panel>
        <Panel title="Budget Details" description={`Remaining and status · ${monthLabel(data.budgets.month)}`}>
          <div className="overflow-x-auto"><table className="w-full min-w-[610px] text-left text-sm"><thead className="border-b border-[#eeeaf1] text-xs uppercase text-[#77717d]"><tr><th className="py-3">Category</th><th>Budget</th><th>Spent</th><th>Remaining / Over</th><th>Used</th><th>Status</th></tr></thead><tbody>
            {data.budgets.categories.map((item) => <tr key={item.category} className="border-b border-[#f0edf1] last:border-0"><td className="py-3 font-medium">{categoryLabel(item.category)}</td><td>{formatCurrency(item.monthlyBudget)}</td><td>{formatCurrency(item.actualSpending)}</td><td>{item.overBudget ? `Over by ${formatCurrency(-item.remaining)}` : formatCurrency(item.remaining)}</td><td>{item.percentUsed}%</td><td className={item.overBudget ? "font-medium text-[#b42318]" : "font-medium text-[#027a48]"}>{item.overBudget ? "Over Budget" : "Within Budget"}</td></tr>)}
          </tbody></table></div>
        </Panel>
      </>}

      <div className="flex flex-col gap-1"><h2 className="text-xl font-semibold text-[#151515]">Investment Analytics</h2><p className="text-sm text-[#77717d]">Purchase cost basis only · no market valuation</p></div>
      <div className="grid gap-4 sm:grid-cols-2"><Metric label="Total Invested · All Time" value={formatCurrency(data.investments.totalInvestedAllTime)} note="Historical purchase cost" /><Metric label="Unique Holdings" value={String(data.investments.uniqueHoldings)} note="Distinct tickers" /></div>
      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Investment Contributions" description={`Monthly purchase cost · ${range}`}>
          {!hasContributions ? <Empty message="No investment purchases in this period." /> : <div className="h-64 min-w-0"><ResponsiveContainer width="100%" height="100%"><BarChart data={data.investments.contributions} margin={{ left: 0, right: 8 }}>
            <CartesianGrid stroke="#eee8e2" vertical={false} /><XAxis dataKey="month" tickFormatter={monthLabel} tick={{ fontSize: 11 }} /><YAxis tickFormatter={(value) => `$${value}`} tick={{ fontSize: 11 }} width={55} /><Tooltip labelFormatter={(label) => monthLabel(String(label))} formatter={(value) => formatCurrency(Number(value))} /><Bar dataKey="amount" name="Invested" fill="#ff5a1f" radius={[5, 5, 0, 0]} />
          </BarChart></ResponsiveContainer></div>}
        </Panel>
        <Panel title="Investment Allocation by Cost" description="All-time share of amount invested, not current market value">
          {data.investments.allocation.length === 0 ? <Empty message="No investments recorded yet." /> : <div className="grid gap-3">
            {data.investments.allocation.map((item) => <div key={item.ticker} className="grid gap-1"><div className="flex justify-between gap-3 text-sm"><span className="font-semibold text-[#151515]">{item.ticker}</span><span>{formatCurrency(item.amountInvested)} · {item.percentage}%</span></div><div className="h-2 overflow-hidden rounded-full bg-[#f3edf0]"><div className="h-full rounded-full bg-[#ff5a1f]" style={{ width: `${Math.min(100, item.percentage)}%` }} /></div></div>)}
          </div>}
        </Panel>
      </div>
    </>}
    <div className="flex justify-center pt-4"><button type="button" onClick={() => document.getElementById("analytics-top")?.scrollIntoView({ behavior: "smooth", block: "start" })} className="flex h-14 w-14 flex-col items-center justify-center rounded-full border border-[#e4e0e7] bg-white text-[#ff5a1f] shadow-sm transition hover:border-[#ff5a1f] hover:bg-[#fff3ed]" aria-label="Back to top"><ArrowUp size={19} /><span className="text-xs font-semibold">Top</span></button></div>
  </div>;
}
