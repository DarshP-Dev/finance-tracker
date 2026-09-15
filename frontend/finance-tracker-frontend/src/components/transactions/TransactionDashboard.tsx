"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { DashboardHeader } from "@/components/dashboard/DashboardHeader";
import { DashboardOverview } from "@/components/dashboard/DashboardOverview";
import { BudgetPage } from "@/components/budgets/BudgetPage";
import { FeaturePlaceholder } from "@/components/features/FeaturePlaceholder";
import { SettingsPage } from "@/components/settings/SettingsPage";
import { Button } from "@/components/ui/button";
import { DateFilter } from "@/components/transactions/DateFilter";
import { formatCurrency } from "@/components/transactions/formatters";
import { TransactionForm } from "@/components/transactions/TransactionForm";
import { TransactionTable } from "@/components/transactions/TransactionTable";
import {
  clearStoredAuth,
  createTransaction,
  deleteTransaction,
  fetchTransactions,
  updateTransaction,
  type AuthResponse,
} from "@/lib/api";
import { appViewRoutes, getAppView, type AppView } from "@/types/navigation";
import type { Transaction, TransactionFilters, TransactionPayload } from "@/types/transactions";

type TransactionDashboardProps = {
  auth: AuthResponse;
  onAuthChange: (auth: AuthResponse) => void;
  onSignOut: () => void;
};

export function TransactionDashboard({ auth, onAuthChange, onSignOut }: TransactionDashboardProps) {
  const pathname = usePathname();
  const router = useRouter();
  const activeView = getAppView(pathname);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [filters, setFilters] = useState<TransactionFilters>({});
  const [maxTransactionAmount, setMaxTransactionAmount] = useState(0);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const hasLoadedTransactions = useRef(false);

  const loadTransactions = useCallback(async () => {
    setIsLoading(true);
    setMessage("");

    try {
      setTransactions(await fetchTransactions(filters));
    } catch (error) {
      setMessage(getErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, [filters]);

  const loadMaxTransactionAmount = useCallback(async () => {
    try {
      const allTransactions = await fetchTransactions({});
      setMaxTransactionAmount(Math.max(0, ...allTransactions.map((transaction) => transaction.amount)));
    } catch {
      setMaxTransactionAmount(0);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      hasLoadedTransactions.current = true;
      void loadTransactions();
    }, hasLoadedTransactions.current ? 350 : 0);

    return () => window.clearTimeout(timeoutId);
  }, [loadTransactions]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadMaxTransactionAmount();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [loadMaxTransactionAmount]);

  useEffect(() => {
    function handleUnauthorized() {
      onSignOut();
    }

    window.addEventListener("finance-tracker:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("finance-tracker:unauthorized", handleUnauthorized);
  }, [onSignOut]);

  const summary = useMemo(() => {
    return transactions.reduce(
      (totals, transaction) => {
        if (transaction.type === "INCOME") {
          totals.income += transaction.amount;
        } else {
          totals.expenses += transaction.amount;
        }

        return totals;
      },
      { income: 0, expenses: 0 },
    );
  }, [transactions]);

  const net = summary.income - summary.expenses;
  const totalFlow = summary.income + summary.expenses;
  const expenseShare = totalFlow === 0 ? 0 : Math.round((summary.expenses / totalFlow) * 100);

  async function handleCreate(payload: TransactionPayload) {
    setIsSubmitting(true);
    setMessage("");

    try {
      await createTransaction(payload);
      await Promise.all([loadTransactions(), loadMaxTransactionAmount()]);
      setMessage("Transaction added.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleUpdate(payload: TransactionPayload) {
    if (!editingTransaction) {
      return;
    }

    setIsSubmitting(true);
    setMessage("");

    try {
      await updateTransaction(editingTransaction.id, payload);
      setEditingTransaction(null);
      await Promise.all([loadTransactions(), loadMaxTransactionAmount()]);
      setMessage("Transaction updated.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete(transaction: Transaction) {
    const confirmed = window.confirm(`Delete ${transaction.merchant || "this transaction"}?`);
    if (!confirmed) {
      return;
    }

    setMessage("");

    try {
      await deleteTransaction(transaction.id);
      await Promise.all([loadTransactions(), loadMaxTransactionAmount()]);
      setMessage("Transaction deleted.");
    } catch (error) {
      setMessage(getErrorMessage(error));
    }
  }

  function handleSignOut() {
    clearStoredAuth();
    onSignOut();
    router.replace(appViewRoutes.dashboard);
  }

  function handleViewChange(view: AppView) {
    router.push(appViewRoutes[view]);
  }

  return (
    <DashboardHeader
      auth={auth}
      activeView={activeView}
      isDarkMode={isDarkMode}
      onViewChange={handleViewChange}
      onSignOut={handleSignOut}
    >
      {activeView === "dashboard" ? (
        <DashboardOverview auth={auth} />
      ) : activeView === "settings" ? (
        <SettingsPage auth={auth} isDarkMode={isDarkMode} onAuthChange={onAuthChange} onDarkModeChange={setIsDarkMode} />
      ) : activeView === "transactions" ? (
        <div className="grid gap-5 py-6">
          <section className="rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm">
            <div className="mb-4">
              <h2 className="text-lg font-semibold">Add Transaction</h2>
              <p className="mt-1 text-sm text-[#667085]">Record income or expenses without leaving your transaction history.</p>
            </div>
            <TransactionForm layout="horizontal" isSubmitting={isSubmitting} onSubmit={handleCreate} />
          </section>

          <section className="min-w-0 overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm">
            <div className="grid border-b border-[#eeeaf1] md:grid-cols-3">
              <SummaryCell label="Income" value={formatCurrency(summary.income)} tone="income" />
              <SummaryCell label="Expenses" value={formatCurrency(summary.expenses)} tone="expense" />
              <SummaryCell label="Net" value={formatCurrency(net)} tone={net >= 0 ? "income" : "expense"} />
            </div>

            <div className="border-b border-[#dfe7f1] px-4 py-4">
              <div className="mb-2 flex items-center justify-between text-sm">
                <span className="font-medium text-[#344054]">Expense ratio</span>
                <span className="font-semibold text-[#172033]">{expenseShare}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-[#ecfdf3]">
                <div className="h-full bg-[#f79009]" style={{ width: `${expenseShare}%` }} />
              </div>
            </div>

            <DateFilter filters={filters} maxTransactionAmount={maxTransactionAmount} onChange={setFilters} />

            {message && (
              <div className="border-b border-[#dfe7f1] bg-[#f8fafc] px-4 py-3 text-sm text-[#344054]">
                {message}
              </div>
            )}

            <TransactionTable
              transactions={transactions}
              isLoading={isLoading}
              onEdit={setEditingTransaction}
              onDelete={handleDelete}
            />
          </section>
        </div>
      ) : activeView === "budgets" ? (
        <BudgetPage />
      ) : (
        <FeaturePlaceholder view={activeView} />
      )}

      {editingTransaction && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#101828]/45 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between gap-4">
              <h2 className="text-lg font-semibold">Edit Transaction</h2>
              <Button type="button" variant="ghost" className="h-8 px-3" onClick={() => setEditingTransaction(null)}>
                Close
              </Button>
            </div>
            <TransactionForm
              key={editingTransaction.id}
              transaction={editingTransaction}
              isSubmitting={isSubmitting}
              onSubmit={handleUpdate}
              onCancel={() => setEditingTransaction(null)}
            />
          </div>
        </div>
      )}
    </DashboardHeader>
  );
}

type SummaryCellProps = {
  label: string;
  value: string;
  tone: "income" | "expense";
};

function SummaryCell({ label, value, tone }: SummaryCellProps) {
  return (
    <div className="border-b border-[#dfe7f1] px-4 py-4 md:border-b-0 md:border-r md:last:border-r-0">
      <div className="text-xs font-semibold uppercase text-[#667085]">{label}</div>
      <div className={tone === "income" ? "mt-1 text-2xl font-semibold text-[#027a48]" : "mt-1 text-2xl font-semibold text-[#b42318]"}>
        {value}
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
