"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { Portfolio } from "@/types/investments";
import { formatCurrency } from "@/components/transactions/formatters";

export function signedMoney(value: number | null | undefined) {
  if (value == null) return "Unavailable";
  return `${value > 0 ? "+" : value < 0 ? "−" : ""}${formatCurrency(Math.abs(value))}`;
}
export function signedReturn(value: number | null | undefined) {
  return value == null ? "Unavailable" : `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
}
export function priceStatus(status: Portfolio["holdings"][number]["quoteStatus"]) {
  switch (status) {
    case "AVAILABLE": return "Latest available quote";
    case "STALE": return "Stale cached quote";
    case "DISABLED": return "Market data disabled";
    case "UNKNOWN_SYMBOL": case "INVALID_SYMBOL": return "Ticker could not be resolved";
    case "RATE_LIMITED": return "Provider request limit reached";
    case "UNSUPPORTED_CURRENCY": return "Quote currency is unsupported (USD only)";
    case "UNSUPPORTED_ASSET": return "This asset type is unsupported";
    default: return "Quote unavailable";
  }
}
export function quoteTime(value: string | null | undefined) {
  return value ? new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : null;
}

export function PortfolioOverview({ portfolio, allocation = false }: { portfolio: Portfolio | null | undefined; allocation?: boolean }) {
  if (!portfolio) return <p className="rounded-xl border border-[#e4e0e7] bg-white p-5 text-sm text-[#77717d]">Portfolio quotes could not be loaded. Your purchase records remain available.</p>;
  const summary = portfolio.summary;
  const complete = summary.totalMarketValue != null;
  const colors = ["#ff5a1f", "#12b76a", "#f79009", "#6d5e68", "#15151b", "#f7b993"];
  const available = portfolio.holdings.filter((h) => h.allocationPercentage != null);
  return <section aria-label="Current portfolio" className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
    <h2 className="text-lg font-semibold text-[#151515]">Current portfolio</h2>
    <p className="mt-1 text-sm leading-6 text-[#77717d]">All tracked holdings · USD · latest available provider prices, which may be delayed or the last close.</p>
    <dl className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Value label="Portfolio value" value={complete ? formatCurrency(summary.totalMarketValue!) : "Unavailable"} />
      <Value label="Recorded cost basis" value={formatCurrency(summary.totalCostBasis)} />
      <Value label="Unrealized gain / loss" value={signedMoney(summary.totalGainLoss)} tone={summary.totalGainLoss} />
      <Value label="Return on recorded cost" value={signedReturn(summary.totalReturnPercentage)} tone={summary.totalReturnPercentage} />
    </dl>
    <p role="status" className="mt-4 text-xs leading-5 text-[#77717d]">
      {summary.status === "EMPTY" ? "No holdings recorded yet." : summary.status === "STALE" ? "Stale cached prices: refresh failed. Values retain their original quote retrieval time." : summary.status === "PARTIAL" ? `Prices are available for ${summary.quotedHoldingCount} of ${summary.holdingCount} holdings. Complete portfolio totals and allocation are unavailable.` : summary.status === "DISABLED" ? "Market data is disabled. Recorded cost remains available." : summary.status === "UNAVAILABLE" ? "Market prices are unavailable. Recorded cost remains available." : "Latest available prices; values are unrealized, not realized profit."}
      {summary.lastUpdated && ` Retrieved ${quoteTime(summary.lastUpdated)}.`}
    </p>
    {allocation && <div className="mt-6 border-t border-[#eeeaf1] pt-5">
      <h3 className="font-semibold text-[#151515]">Portfolio allocation by market value</h3>
      {available.length === 0 ? <p className="mt-3 text-sm text-[#77717d]">Allocation requires prices for every holding and a positive portfolio value.</p> : <div className="grid min-w-0 items-center gap-4 md:grid-cols-2">
        <div className="h-56 min-w-0" aria-hidden="true"><ResponsiveContainer width="100%" height={224}><PieChart><Pie data={available} dataKey="marketValue" nameKey="ticker" innerRadius={55} outerRadius={85}>{available.map((h, i) => <Cell key={h.ticker} fill={colors[i % colors.length]} />)}</Pie><Tooltip formatter={(value) => formatCurrency(Number(value))} /></PieChart></ResponsiveContainer></div>
        <ul className="grid gap-3 text-sm">{available.map((h, i) => <li key={h.ticker} className="flex flex-wrap items-center justify-between gap-2"><span className="inline-flex items-center gap-2 font-semibold"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ background: colors[i % colors.length] }} />{h.ticker}</span><span>{formatCurrency(h.marketValue!)} · {h.allocationPercentage!.toFixed(1)}%</span></li>)}</ul>
      </div>}
    </div>}
  </section>;
}
function Value({ label, value, tone }: { label: string; value: string; tone?: number | null }) {
  return <div className="min-w-0"><dt className="text-xs font-semibold uppercase tracking-wide text-[#77717d]">{label}</dt><dd className={`mt-2 break-words text-xl font-semibold ${tone == null || tone === 0 ? "text-[#151515]" : tone > 0 ? "text-[#027a48]" : "text-[#b42318]"}`}>{value}</dd></div>;
}
