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
      <div className="mx-auto grid max-w-7xl gap-5 px-4 py-5 sm:px-6">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-[138px] animate-pulse rounded-lg border border-[#d9e1ec] bg-white shadow-sm" />
          ))}
        </div>
        <div className="grid gap-5 xl:grid-cols-2">
          <div className="h-[360px] animate-pulse rounded-lg border border-[#d9e1ec] bg-white shadow-sm" />
          <div className="h-[360px] animate-pulse rounded-lg border border-[#d9e1ec] bg-white shadow-sm" />
        </div>
      </div>
    );
  }

  if (!dashboard) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-5 sm:px-6">
        <div className="rounded-lg border border-[#d9e1ec] bg-white p-6 text-sm text-[#667085] shadow-sm">
          {message || "Dashboard data is unavailable."}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto grid max-w-7xl gap-5 px-4 py-5 sm:px-6">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SummaryCard label="Total Income" value={formatCurrency(dashboard.summary.totalIncome)} helper="All recorded income" tone="income" />
        <SummaryCard label="Total Expenses" value={formatCurrency(dashboard.summary.totalExpenses)} helper="All recorded expenses" tone="expense" />
        <SummaryCard label="Total Savings" value={formatCurrency(dashboard.summary.totalSavings)} helper="Income minus expenses" tone="savings" />
        <SummaryCard label="Investment Value" value={formatCurrency(dashboard.summary.investmentValue)} helper="Shares at purchase value" tone="investment" />
      </div>

      {message && (
        <div className="rounded-lg border border-[#d9e1ec] bg-white px-4 py-3 text-sm text-[#344054] shadow-sm">
          {message}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-2">
        <SpendingPieChart data={dashboard.categorySpending} />
        <MonthlyTrendChart data={dashboard.monthlySpending} />
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
