"use client";

import { useCallback, useEffect, useState } from "react";
import { IncomeExpenseChart } from "@/components/dashboard/IncomeExpenseChart";
import { MonthlyTrendChart } from "@/components/dashboard/MonthlyTrendChart";
import { RecentTransactionsCard } from "@/components/dashboard/RecentTransactionsCard";
import { SpendingPieChart } from "@/components/dashboard/SpendingPieChart";
import { SummaryCard } from "@/components/dashboard/SummaryCard";
import { formatCurrency } from "@/components/transactions/formatters";
import { fetchDashboard } from "@/lib/api";
import type { DashboardData } from "@/types/dashboard";

export function DashboardOverview() {
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState("");

  const loadDashboard = useCallback(async () => {
    setIsLoading(true);
    setMessage("");

    try {
      setDashboard(await fetchDashboard());
    } catch (error) {
      setMessage(getErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadDashboard();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [loadDashboard]);

  if (isLoading) {
    return (
      <div className="grid gap-5 py-6">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
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
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-5 py-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-normal text-[#151515]">Good Morning, Vadelz</h1>
          <p className="mt-2 text-sm text-[#77717d]">This is your finance report.</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <button className="h-11 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#151515] shadow-sm">
            August 2024 - December 2024
          </button>
          <button className="h-11 rounded-xl bg-[#15151b] px-5 text-sm font-semibold text-white shadow-sm">
            Filters
          </button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard label="Income" value={formatCurrency(dashboard.summary.totalIncome)} helper="You made an extra income this month" tone="income" />
        <SummaryCard label="Expenses" value={formatCurrency(dashboard.summary.totalExpenses)} helper="You overspent against planned budget" tone="expense" />
        <SummaryCard label="My Balance" value={formatCurrency(dashboard.summary.totalSavings)} helper="Income minus expenses" tone="savings" />
        <SummaryCard label="Investments" value={formatCurrency(dashboard.summary.investmentValue)} helper="Shares at purchase value" tone="investment" />
      </div>

      {message && (
        <div className="rounded-2xl border border-[#e4e0e7] bg-white px-4 py-3 text-sm text-[#46404b] shadow-sm">
          {message}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <MonthlyTrendChart data={dashboard.monthlySpending} />
        <SpendingPieChart data={dashboard.categorySpending} />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
        <IncomeExpenseChart data={dashboard.incomeVsExpenses} />
        <RecentTransactionsCard transactions={dashboard.recentTransactions} />
      </div>
    </div>
  );
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
