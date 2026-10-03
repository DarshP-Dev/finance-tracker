"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import { getRecurringForecast, getUpcomingRecurringTransactions } from "@/lib/api";
import { recurringFrequencyLabels, type RecurringForecast, type RecurringForecastRange, type UpcomingRecurringTransaction } from "@/types/recurring-transactions";

type Period = "next30" | "thisMonth" | "nextMonth" | "next90";

const periods: { value: Period; label: string }[] = [
  { value: "next30", label: "Next 30 Days" },
  { value: "thisMonth", label: "This Month" },
  { value: "nextMonth", label: "Next Month" },
  { value: "next90", label: "Next 90 Days" },
];

export function UpcomingRecurringView() {
  const [period, setPeriod] = useState<Period>("next30");
  const [upcoming, setUpcoming] = useState<UpcomingRecurringTransaction[]>([]);
  const [forecast, setForecast] = useState<RecurringForecast | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const range = useMemo(() => getRange(period), [period]);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const [entries, summary] = await Promise.all([
        getUpcomingRecurringTransactions(range),
        getRecurringForecast(range),
      ]);
      setUpcoming(entries);
      setForecast(summary);
    } catch (failure) {
      setError(getErrorMessage(failure));
    } finally {
      setIsLoading(false);
    }
  }, [range]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [load]);

  return (
    <section className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[#172033]">Upcoming recurring transactions</h2>
          <p className="mt-1 text-sm text-[#667085]">Projected from active recurring schedules. These are not recorded transactions yet.</p>
        </div>
        <div role="group" aria-label="Forecast period" className="flex flex-wrap gap-2">
          {periods.map((option) => (
            <button key={option.value} type="button" aria-pressed={period === option.value} onClick={() => setPeriod(option.value)}
              className={period === option.value
                ? "rounded-lg bg-[#195b4d] px-3 py-2 text-xs font-semibold text-white"
                : "rounded-lg border border-[#e4e0e7] px-3 py-2 text-xs font-semibold text-[#475467] hover:bg-[#f5f7fb]"}>
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div role="alert" className="mt-5 rounded-xl border border-[#f1c6c1] bg-[#fff1f0] p-4 text-sm text-[#b42318]">
          <p>{error}</p>
          <Button type="button" variant="secondary" className="mt-3 h-8 px-3" onClick={() => void load()}>Try again</Button>
        </div>
      )}

      {isLoading ? (
        <p className="py-12 text-center text-sm text-[#667085]">Loading upcoming transactions</p>
      ) : !error && forecast ? (
        <>
          <p className="mt-4 text-xs text-[#667085]">{formatDate(forecast.from)} – {formatDate(forecast.to)}</p>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            <ForecastCard label="Expected Recurring Income" value={formatCurrency(forecast.expectedIncome)} tone="income" />
            <ForecastCard label="Expected Recurring Expenses" value={formatCurrency(forecast.expectedExpenses)} tone="expense" />
            <ForecastCard label="Projected Recurring Net Cash Flow" value={`${forecast.netCashFlow >= 0 ? "+" : "−"}${formatCurrency(Math.abs(forecast.netCashFlow))}`} tone={forecast.netCashFlow >= 0 ? "income" : "expense"} />
          </div>

          {upcoming.length === 0 ? (
            <p className="py-12 text-center text-sm text-[#667085]">No recurring transactions are scheduled during this period.</p>
          ) : (
            <div className="mt-5 grid gap-3 md:grid-cols-2">
              {upcoming.map((entry) => (
                <article key={`${entry.recurringTransactionId}-${entry.scheduledDate}`} className="min-w-0 rounded-xl border border-[#e4e0e7] bg-[#fcfcfd] p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-[#667085]">{formatDate(entry.scheduledDate)}</p>
                      <h3 className="mt-1 break-words font-semibold text-[#172033]">{entry.description}</h3>
                      {entry.merchant && <p className="break-words text-sm text-[#667085]">{entry.merchant}</p>}
                    </div>
                    <p className={entry.type === "INCOME" ? "font-semibold text-[#027a48]" : "font-semibold text-[#b42318]"}>
                      {entry.type === "INCOME" ? "+" : "−"}{formatCurrency(entry.amount)}
                    </p>
                  </div>
                  <p className="mt-3 text-xs text-[#667085]">{formatCategory(entry.category)} · {recurringFrequencyLabels[entry.frequency]} · {entry.type === "INCOME" ? "Income" : "Expense"}</p>
                </article>
              ))}
            </div>
          )}
        </>
      ) : null}
    </section>
  );
}

function ForecastCard({ label, value, tone }: { label: string; value: string; tone: "income" | "expense" }) {
  return (
    <div className="min-w-0 rounded-xl border border-[#e4e0e7] bg-[#fcfcfd] p-4">
      <p className="text-xs font-medium text-[#667085]">{label}</p>
      <p className={tone === "income" ? "mt-2 break-words text-xl font-semibold text-[#027a48]" : "mt-2 break-words text-xl font-semibold text-[#b42318]"}>{value}</p>
    </div>
  );
}

function getRange(period: Period): RecurringForecastRange {
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth();
  const day = today.getDate();
  if (period === "thisMonth") {
    return { from: toDate(new Date(year, month, 1)), to: toDate(new Date(year, month + 1, 0)) };
  }
  if (period === "nextMonth") {
    return { from: toDate(new Date(year, month + 1, 1)), to: toDate(new Date(year, month + 2, 0)) };
  }
  return { from: toDate(today), to: toDate(new Date(year, month, day + (period === "next30" ? 30 : 89))) };
}

function toDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function getErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = error.response as { data?: { message?: string; detail?: string } } | undefined;
    if (response?.data?.message || response?.data?.detail) return response.data.message || response.data.detail || "Request failed.";
  }
  return error instanceof Error ? error.message : "Request failed.";
}
