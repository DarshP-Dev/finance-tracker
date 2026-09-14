"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Pencil, PieChart, Plus, Trash2, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { formatCategory, formatCurrency } from "@/components/transactions/formatters";
import { createBudget, deleteBudget, fetchBudgets, updateBudget } from "@/lib/api";
import { budgetCategories, type Budget, type BudgetCategory, type BudgetPayload } from "@/types/budgets";

export function BudgetPage() {
  const [selectedMonth, setSelectedMonth] = useState(getCurrentMonth());
  const [budgets, setBudgets] = useState<Budget[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState("");
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingBudget, setEditingBudget] = useState<Budget | null>(null);

  const loadBudgets = useCallback(async () => {
    setIsLoading(true);
    setPageError("");
    try {
      setBudgets(await fetchBudgets(selectedMonth));
    } catch (error) {
      setPageError(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, [selectedMonth]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadBudgets(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [loadBudgets]);

  const summary = useMemo(
    () => budgets.reduce(
      (totals, budget) => ({
        budgeted: totals.budgeted + budget.monthlyLimit,
        spent: totals.spent + budget.amountSpent,
        remaining: totals.remaining + budget.remaining,
      }),
      { budgeted: 0, spent: 0, remaining: 0 },
    ),
    [budgets],
  );

  function openCreateForm() {
    setEditingBudget(null);
    setIsFormOpen(true);
  }

  function openEditForm(budget: Budget) {
    setEditingBudget(budget);
    setIsFormOpen(true);
  }

  function closeForm() {
    setIsFormOpen(false);
    setEditingBudget(null);
  }

  async function handleSave(payload: BudgetPayload) {
    if (editingBudget) {
      await updateBudget(editingBudget.id, payload);
    } else {
      await createBudget(payload);
    }
    closeForm();
    if (payload.month !== selectedMonth) {
      setSelectedMonth(payload.month);
    } else {
      await loadBudgets();
    }
  }

  async function handleDelete(budget: Budget) {
    const confirmed = window.confirm(`Delete ${formatCategory(budget.category)} budget for ${formatMonth(budget.month)}?`);
    if (!confirmed) {
      return;
    }

    setPageError("");
    try {
      await deleteBudget(budget.id);
      await loadBudgets();
    } catch (error) {
      setPageError(getApiErrorMessage(error));
    }
  }

  return (
    <div className="grid gap-5 py-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-normal text-[#151515]">Budgets</h1>
          <p className="mt-2 text-sm text-[#77717d]">Set monthly spending limits and track your progress.</p>
        </div>
        <button type="button" onClick={openCreateForm} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#ff5a1f] px-5 text-sm font-semibold text-white transition hover:bg-[#e64d17]">
          <Plus size={17} />
          Add Budget
        </button>
      </div>

      <section className="rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm">
        <div className="flex max-w-sm items-center gap-2">
          <CalendarDays size={18} className="shrink-0 text-[#ff5a1f]" />
          <button type="button" onClick={() => setSelectedMonth(shiftMonth(selectedMonth, -1))} aria-label="Previous month" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-[#e4e0e7] text-[#77717d] hover:border-[#ff5a1f] hover:text-[#ff5a1f]"><ChevronLeft size={17} /></button>
          <Input type="month" value={selectedMonth} onChange={(event) => setSelectedMonth(event.target.value)} aria-label="Budget month" />
          <button type="button" onClick={() => setSelectedMonth(shiftMonth(selectedMonth, 1))} aria-label="Next month" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-[#e4e0e7] text-[#77717d] hover:border-[#ff5a1f] hover:text-[#ff5a1f]"><ChevronRight size={17} /></button>
        </div>
      </section>

      {!isLoading && !pageError && (
        <section aria-label={`${formatMonth(selectedMonth)} budget summary`} className="grid overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm sm:grid-cols-3">
          <SummaryItem label="Total Budgeted" value={formatCurrency(summary.budgeted)} />
          <SummaryItem label="Total Spent" value={formatCurrency(summary.spent)} />
          <SummaryItem label="Remaining" value={formatCurrency(summary.remaining)} tone={summary.remaining < 0 ? "danger" : "positive"} />
        </section>
      )}

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Loading budgets">
          {Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-56 animate-pulse rounded-2xl border border-[#e4e0e7] bg-white shadow-sm" />)}
        </div>
      ) : pageError ? (
        <section className="rounded-2xl border border-[#f1c6c1] bg-white p-6 shadow-sm">
          <p role="alert" className="text-sm font-medium text-[#b42318]">{pageError}</p>
          <button type="button" onClick={() => void loadBudgets()} className="mt-4 rounded-xl bg-[#15151b] px-5 py-2.5 text-sm font-semibold text-white">Try again</button>
        </section>
      ) : budgets.length === 0 ? (
        <section className="rounded-2xl border border-[#e4e0e7] bg-white px-6 py-12 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#fff3ed] text-[#ff5a1f]"><PieChart size={22} /></div>
          <h2 className="mt-5 text-lg font-semibold text-[#151515]">No budgets for {formatMonth(selectedMonth)} yet.</h2>
          <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-[#77717d]">Create a budget to start tracking your monthly spending.</p>
          <button type="button" onClick={openCreateForm} className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-[#ff5a1f] px-4 text-sm font-semibold text-white hover:bg-[#e64d17]"><Plus size={16} /> Add Budget</button>
        </section>
      ) : (
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label={`${formatMonth(selectedMonth)} budgets`}>
          {budgets.map((budget) => <BudgetCard key={budget.id} budget={budget} onEdit={openEditForm} onDelete={handleDelete} />)}
        </section>
      )}

      {isFormOpen && (
        <BudgetForm
          key={editingBudget?.id ?? `new-${selectedMonth}`}
          budget={editingBudget}
          defaultMonth={selectedMonth}
          onClose={closeForm}
          onSave={handleSave}
        />
      )}
    </div>
  );
}

function SummaryItem({ label, value, tone = "default" }: { label: string; value: string; tone?: "default" | "positive" | "danger" }) {
  const valueClass = tone === "danger" ? "text-[#b42318]" : tone === "positive" ? "text-[#027a48]" : "text-[#151515]";
  return (
    <div className="border-b border-[#eeeaf1] px-5 py-5 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0">
      <p className="text-xs font-semibold uppercase tracking-wide text-[#77717d]">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${valueClass}`}>{value}</p>
    </div>
  );
}

function BudgetCard({ budget, onEdit, onDelete }: { budget: Budget; onEdit: (budget: Budget) => void; onDelete: (budget: Budget) => void }) {
  const isWarning = !budget.overBudget && budget.percentUsed >= 80;
  const progressColor = budget.overBudget ? "bg-[#d92d20]" : isWarning ? "bg-[#f79009]" : "bg-[#12b76a]";
  const statusColor = budget.overBudget ? "text-[#b42318]" : isWarning ? "text-[#b54708]" : "text-[#027a48]";
  const status = budget.overBudget ? "Over budget" : isWarning ? "Approaching limit" : "Within budget";

  return (
    <article className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="font-semibold text-[#151515]">{formatCategory(budget.category)}</h2>
          <p className={`mt-1 text-xs font-semibold ${statusColor}`}>{status}</p>
        </div>
        <div className="flex gap-1">
          <button type="button" onClick={() => onEdit(budget)} aria-label={`Edit ${formatCategory(budget.category)} budget`} className="flex h-8 w-8 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#fff3ed] hover:text-[#ff5a1f]"><Pencil size={15} /></button>
          <button type="button" onClick={() => onDelete(budget)} aria-label={`Delete ${formatCategory(budget.category)} budget`} className="flex h-8 w-8 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#fff1f0] hover:text-[#b42318]"><Trash2 size={15} /></button>
        </div>
      </div>
      <p className="mt-6 text-sm text-[#77717d]"><span className="text-xl font-semibold text-[#151515]">{formatCurrency(budget.amountSpent)}</span> / {formatCurrency(budget.monthlyLimit)}</p>
      <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-[#f0edf2]" aria-hidden="true">
        <div className={`h-full rounded-full ${progressColor}`} style={{ width: `${Math.min(Math.max(budget.percentUsed, 0), 100)}%` }} />
      </div>
      <div className="mt-3 flex items-center justify-between gap-3 text-sm">
        <span className={`font-semibold ${statusColor}`}>{formatPercent(budget.percentUsed)} used</span>
        <span className="text-right text-[#77717d]">{budget.overBudget ? `${formatCurrency(Math.abs(budget.remaining))} over budget` : `${formatCurrency(budget.remaining)} remaining`}</span>
      </div>
    </article>
  );
}

function BudgetForm({ budget, defaultMonth, onClose, onSave }: { budget: Budget | null; defaultMonth: string; onClose: () => void; onSave: (payload: BudgetPayload) => Promise<void> }) {
  const [category, setCategory] = useState<BudgetCategory | "">(budget?.category ?? "");
  const [monthlyLimit, setMonthlyLimit] = useState(budget ? String(budget.monthlyLimit) : "");
  const [month, setMonth] = useState(budget?.month ?? defaultMonth);
  const [formError, setFormError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError("");
    const parsedLimit = Number(monthlyLimit);
    if (!category || !month || !Number.isFinite(parsedLimit) || parsedLimit <= 0) {
      setFormError("Choose an expense category, a valid month, and a monthly limit greater than zero.");
      return;
    }

    setIsSubmitting(true);
    try {
      await onSave({ category, monthlyLimit: parsedLimit, month });
    } catch (error) {
      setFormError(getApiErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#101828]/45 p-4" role="presentation">
      <section role="dialog" aria-modal="true" aria-labelledby="budget-form-title" className="w-full max-w-lg rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-xl">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 id="budget-form-title" className="text-lg font-semibold text-[#151515]">{budget ? "Edit Budget" : "Add Budget"}</h2>
            <p className="mt-1 text-sm text-[#77717d]">Set a monthly limit for one expense category.</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close budget form" className="flex h-9 w-9 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#f5f3f6]"><X size={18} /></button>
        </div>
        <form onSubmit={handleSubmit} className="mt-5 grid gap-4">
          <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
            Category
            <Select value={category} onChange={(event) => setCategory(event.target.value as BudgetCategory | "")} required>
              <option value="">Select expense category</option>
              {budgetCategories.map((item) => <option key={item} value={item}>{formatCategory(item)}</option>)}
            </Select>
          </label>
          <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
            Monthly limit
            <Input type="number" min="0.01" step="0.01" value={monthlyLimit} onChange={(event) => setMonthlyLimit(event.target.value)} placeholder="500.00" required />
          </label>
          <label className="grid gap-2 text-sm font-semibold text-[#46404b]">
            Month
            <Input type="month" value={month} onChange={(event) => setMonth(event.target.value)} required />
          </label>
          {formError && <p role="alert" className="rounded-xl bg-[#fff1f0] px-3 py-2 text-sm font-medium text-[#b42318]">{formError}</p>}
          <div className="mt-1 flex justify-end gap-3">
            <button type="button" onClick={onClose} className="h-10 rounded-xl border border-[#e4e0e7] px-4 text-sm font-semibold text-[#46404b] hover:border-[#ff5a1f]">Cancel</button>
            <button type="submit" disabled={isSubmitting} className="h-10 rounded-xl bg-[#ff5a1f] px-5 text-sm font-semibold text-white hover:bg-[#e64d17] disabled:bg-[#f4a384]">{isSubmitting ? "Saving…" : budget ? "Save Changes" : "Create Budget"}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function getCurrentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function formatMonth(month: string) {
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(new Date(`${month}-01T00:00:00`));
}

function shiftMonth(month: string, offset: number) {
  const [year, monthNumber] = month.split("-").map(Number);
  const shifted = new Date(year, monthNumber - 1 + offset, 1);
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, "0")}`;
}

function formatPercent(value: number) {
  return `${new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(value)}%`;
}

function getApiErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = error.response as { data?: { message?: string; fieldErrors?: Record<string, string> } };
    const fieldError = response.data?.fieldErrors ? Object.values(response.data.fieldErrors)[0] : undefined;
    return fieldError ?? response.data?.message ?? "Budget request failed.";
  }
  return error instanceof Error ? error.message : "Budget request failed.";
}
