"use client";

import { useCallback, useEffect, useState } from "react";
import { CalendarDays, PiggyBank, TrendingDown, TrendingUp } from "lucide-react";
import { MonthlyTrendChart } from "@/components/dashboard/MonthlyTrendChart";
import { RecentTransactionsCard } from "@/components/dashboard/RecentTransactionsCard";
import { SpendingPieChart } from "@/components/dashboard/SpendingPieChart";
import { SummaryCard } from "@/components/dashboard/SummaryCard";
import { Input } from "@/components/ui/input";
import { formatCurrency } from "@/components/transactions/formatters";
import { fetchDashboard, fetchTransactions, type AuthResponse } from "@/lib/api";
import type { Transaction } from "@/types/transactions";
import type { DashboardData } from "@/types/dashboard";

type DashboardOverviewProps = {
  auth: AuthResponse;
};

export function DashboardOverview({ auth }: DashboardOverviewProps) {
  const defaultRange = getDefaultDateRange();
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [isDatePanelOpen, setIsDatePanelOpen] = useState(false);
  const [startDate, setStartDate] = useState(defaultRange.startDate);
  const [endDate, setEndDate] = useState(defaultRange.endDate);

  const loadDashboard = useCallback(async () => {
    setIsLoading(true);
    setMessage("");

    try {
      const [report, activity] = await Promise.all([
        fetchDashboard({ startDate, endDate }),
        fetchTransactions({ startDate, endDate }),
      ]);
      setDashboard(report);
      setTransactions(activity);
    } catch (error) {
      setMessage(getErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, [endDate, startDate]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadDashboard();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [loadDashboard]);

  if (isLoading) {
    return (
      <div className="grid gap-5 py-6">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="h-[160px] animate-pulse rounded-2xl border border-[#e4e0e7] bg-white shadow-sm" />
          ))}
        </div>
        <div className="grid gap-5 xl:grid-cols-2">
          <div className="h-[360px] animate-pulse rounded-2xl border border-[#e4e0e7] bg-white shadow-sm" />
          <div className="h-[360px] animate-pulse rounded-2xl border border-[#e4e0e7] bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  if (!dashboard) {
    return (
      <div className="py-6">
        <div className="rounded-2xl border border-[#e4e0e7] bg-white p-6 text-sm text-[#77717d] shadow-sm">
          {message || "Dashboard data is unavailable."}
          <button
            type="button"
            onClick={() => void loadDashboard()}
            className="mt-4 block rounded-xl bg-[#15151b] px-5 py-2.5 text-sm font-semibold text-white"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-5 py-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-[#151515]">Welcome back, {auth.username}</h1>
          <p className="mt-2 text-sm text-[#77717d]">This is your finance report.</p>
        </div>
        <div className="relative flex flex-col gap-2 sm:flex-row">
          <button
            type="button"
            onClick={() => setIsDatePanelOpen((current) => !current)}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#151515] shadow-sm transition hover:border-[#ff5a1f]"
          >
            <CalendarDays size={16} />
            {formatDateRange(startDate, endDate)}
          </button>

          {isDatePanelOpen && (
            <div className="absolute right-0 top-[calc(100%+10px)] z-40 w-full rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-xl sm:w-[340px]">
              <div className="mb-4">
                <h2 className="text-sm font-semibold text-[#151515]">Date range</h2>
                <p className="mt-1 text-xs text-[#77717d]">Choose the reporting window shown on the dashboard.</p>
              </div>
              <div className="grid gap-3">
                <label className="grid gap-1.5 text-sm font-semibold text-[#46404b]">
                  Start date
                  <Input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold text-[#46404b]">
                  End date
                  <Input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                </label>
                <button
                  type="button"
                  onClick={() => setIsDatePanelOpen(false)}
                  className="mt-1 h-10 rounded-xl bg-[#ff5a1f] text-sm font-semibold text-white transition hover:bg-[#e64d17]"
                >
                  Apply range
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <SummaryCard label="Income" value={formatCurrency(dashboard.summary.totalIncome)} helper="Total income in the selected period" tone="income" icon={<TrendingUp size={19} />} />
        <SummaryCard label="Expenses" value={formatCurrency(dashboard.summary.totalExpenses)} helper="Total expenses in the selected period" tone="expense" icon={<TrendingDown size={19} />} />
        <SummaryCard label="My Balance" value={formatCurrency(dashboard.summary.totalSavings)} helper="Income minus expenses" tone="savings" icon={<PiggyBank size={19} />} />
      </div>

      {message && (
        <div className="rounded-2xl border border-[#e4e0e7] bg-white px-4 py-3 text-sm text-[#46404b] shadow-sm">
          {message}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <MonthlyTrendChart data={dashboard.cashFlowTrend ?? []} />
        <SpendingPieChart data={dashboard.categorySpending} />
      </div>

      <RecentTransactionsCard transactions={transactions} />
    </div>
  );
}

function formatDateRange(startDate: string, endDate: string) {
  if (!startDate || !endDate) {
    return "Select date range";
  }

  const formatter = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return `${formatter.format(new Date(`${startDate}T00:00:00`))} - ${formatter.format(new Date(`${endDate}T00:00:00`))}`;
}

function getDefaultDateRange() {
  const currentYear = new Date().getFullYear();
  const startDate = new Date(currentYear, 0, 1);
  const endDate = new Date(currentYear, 11, 31);

  return {
    startDate: toDateInputValue(startDate),
    endDate: toDateInputValue(endDate),
  };
}

function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getErrorMessage(error: unknown) {
  if (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    typeof error.response === "object" &&
    error.response !== null &&
    "data" in error.response
  ) {
    const data = error.response.data as { message?: string };
    return data.message ?? "Request failed.";
  }

  return error instanceof Error ? error.message : "Request failed.";
}
