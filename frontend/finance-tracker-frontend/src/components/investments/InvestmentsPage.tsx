"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Pencil, Plus, Search, Trash2, TrendingUp, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  createInvestment,
  deleteInvestment,
  fetchInvestmentHoldings,
  fetchInvestments,
  updateInvestment,
} from "@/lib/api";
import type { Investment, InvestmentHolding, InvestmentPayload } from "@/types/investments";

export function InvestmentsPage() {
  const [investments, setInvestments] = useState<Investment[]>([]);
  const [holdings, setHoldings] = useState<InvestmentHolding[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingInvestment, setEditingInvestment] = useState<Investment | null>(null);

  const loadInvestments = useCallback(async () => {
    setIsLoading(true);
    setPageError("");
    try {
      const [purchaseLots, groupedHoldings] = await Promise.all([
        fetchInvestments(),
        fetchInvestmentHoldings(),
      ]);
      setInvestments(purchaseLots);
      setHoldings(groupedHoldings);
    } catch (error) {
      setPageError(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadInvestments(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [loadInvestments]);

  const normalizedSearch = searchQuery.trim().toUpperCase();
  const filteredHoldings = useMemo(
    () => holdings.filter((holding) => holding.ticker.includes(normalizedSearch)),
    [holdings, normalizedSearch],
  );
  const filteredInvestments = useMemo(
    () => investments.filter((investment) => investment.ticker.includes(normalizedSearch)),
    [investments, normalizedSearch],
  );
  const totalInvested = useMemo(
    () => holdings.reduce((total, holding) => total + holding.totalInvested, 0),
    [holdings],
  );

  function openCreateForm() {
    setEditingInvestment(null);
    setIsFormOpen(true);
  }

  function openEditForm(investment: Investment) {
    setEditingInvestment(investment);
    setIsFormOpen(true);
  }

  function closeForm() {
    setIsFormOpen(false);
    setEditingInvestment(null);
  }

  async function handleSave(payload: InvestmentPayload) {
    if (editingInvestment) {
      await updateInvestment(editingInvestment.id, payload);
    } else {
      await createInvestment(payload);
    }
    closeForm();
    await loadInvestments();
  }

  async function handleDelete(investment: Investment) {
    const confirmed = window.confirm(
      `Delete this ${investment.ticker} purchase?\n\n${formatShares(investment.shares)} shares purchased at ${formatCurrency(investment.purchasePrice)} on ${formatDate(investment.purchaseDate)}.`,
    );
    if (!confirmed) {
      return;
    }

    setPageError("");
    try {
      await deleteInvestment(investment.id);
      await loadInvestments();
    } catch (error) {
      setPageError(getApiErrorMessage(error));
    }
  }

  return (
    <div className="grid gap-5 py-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-normal text-[#151515]">Investments</h1>
          <p className="mt-2 text-sm text-[#77717d]">Track your investment purchases and total amount invested.</p>
        </div>
        <button type="button" onClick={openCreateForm} className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#ff5a1f] px-5 text-sm font-semibold text-white transition hover:bg-[#e64d17]">
          <Plus size={17} />
          Add Investment
        </button>
      </div>

      {isLoading ? (
        <LoadingState />
      ) : pageError && investments.length === 0 ? (
        <ErrorState message={pageError} onRetry={loadInvestments} />
      ) : investments.length === 0 ? (
        <EmptyState onAdd={openCreateForm} />
      ) : (
        <>
          <section aria-label="Investment summary" className="grid overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm sm:grid-cols-3">
            <SummaryItem label="Total Invested" value={formatCurrency(totalInvested)} />
            <SummaryItem label="Holdings" value={String(holdings.length)} />
            <SummaryItem label="Purchase Records" value={String(investments.length)} />
          </section>

          <section className="rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm">
            <label className="relative block max-w-md">
              <span className="sr-only">Filter investments by ticker</span>
              <Search size={17} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8d8792]" />
              <Input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value.toUpperCase())}
                placeholder="Filter by ticker"
                className="pl-10 uppercase"
              />
            </label>
          </section>

          {pageError && (
            <p role="alert" className="rounded-xl border border-[#f1c6c1] bg-[#fff1f0] px-4 py-3 text-sm font-medium text-[#b42318]">{pageError}</p>
          )}

          <section aria-labelledby="holdings-heading">
            <div className="mb-3 flex items-end justify-between gap-3">
              <div>
                <h2 id="holdings-heading" className="text-lg font-semibold text-[#151515]">Holdings</h2>
                <p className="mt-1 text-sm text-[#77717d]">Grouped purchase cost by ticker.</p>
              </div>
            </div>
            {filteredHoldings.length === 0 ? (
              <NoSearchResults query={searchQuery} />
            ) : (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {filteredHoldings.map((holding) => <HoldingCard key={holding.ticker} holding={holding} />)}
              </div>
            )}
          </section>

          <section aria-labelledby="purchase-history-heading" className="overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm">
            <div className="border-b border-[#eeeaf1] px-5 py-4">
              <h2 id="purchase-history-heading" className="text-lg font-semibold text-[#151515]">Purchase History</h2>
              <p className="mt-1 text-sm text-[#77717d]">Each row is an individual recorded purchase lot.</p>
            </div>
            {filteredInvestments.length === 0 ? (
              <NoSearchResults query={searchQuery} />
            ) : (
              <PurchaseHistory investments={filteredInvestments} onEdit={openEditForm} onDelete={handleDelete} />
            )}
          </section>
        </>
      )}

      {isFormOpen && (
        <InvestmentForm
          key={editingInvestment?.id ?? "new-investment"}
          investment={editingInvestment}
          onClose={closeForm}
          onSave={handleSave}
        />
      )}
    </div>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-b border-[#eeeaf1] px-5 py-5 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0">
      <p className="text-xs font-semibold uppercase tracking-wide text-[#77717d]">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-[#151515]">{value}</p>
    </div>
  );
}

function HoldingCard({ holding }: { holding: InvestmentHolding }) {
  return (
    <article className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="flex h-11 min-w-16 items-center justify-center rounded-xl bg-[#fff3ed] px-3 font-semibold text-[#ff5a1f]">{holding.ticker}</div>
        <span className="rounded-full bg-[#f5f3f6] px-3 py-1 text-xs font-semibold text-[#77717d]">
          {holding.purchaseCount} {holding.purchaseCount === 1 ? "purchase" : "purchases"}
        </span>
      </div>
      <dl className="mt-5 grid gap-4 sm:grid-cols-2">
        <Metric label="Total Shares" value={formatShares(holding.totalShares)} />
        <Metric label="Total Invested" value={formatCurrency(holding.totalInvested)} />
        <div className="sm:col-span-2"><Metric label="Average Purchase Price" value={`${formatCurrency(holding.averagePurchasePrice)} / share`} /></div>
      </dl>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-[#8d8792]">{label}</dt>
      <dd className="mt-1 text-base font-semibold text-[#151515]">{value}</dd>
    </div>
  );
}

function PurchaseHistory({ investments, onEdit, onDelete }: { investments: Investment[]; onEdit: (investment: Investment) => void; onDelete: (investment: Investment) => void }) {
  return (
    <>
      <div className="grid gap-3 p-4 sm:hidden">
        {investments.map((investment) => (
          <article key={investment.id} className="rounded-xl border border-[#eeeaf1] p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold text-[#151515]">{investment.ticker}</p>
                <p className="mt-1 text-sm text-[#77717d]">{formatDate(investment.purchaseDate)}</p>
              </div>
              <PurchaseActions investment={investment} onEdit={onEdit} onDelete={onDelete} />
            </div>
            <p className="mt-4 text-sm text-[#77717d]">{formatShares(investment.shares)} shares × {formatCurrency(investment.purchasePrice)}</p>
            <p className="mt-1 font-semibold text-[#151515]">{formatCurrency(investment.amountInvested)}</p>
          </article>
        ))}
      </div>
      <div className="hidden overflow-x-auto sm:block">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-[#faf9fb] text-xs font-semibold uppercase tracking-wide text-[#77717d]">
            <tr><th className="px-5 py-3">Ticker</th><th className="px-5 py-3">Purchase Date</th><th className="px-5 py-3">Shares</th><th className="px-5 py-3">Price / Share</th><th className="px-5 py-3">Amount Invested</th><th className="px-5 py-3 text-right">Actions</th></tr>
          </thead>
          <tbody className="divide-y divide-[#eeeaf1]">
            {investments.map((investment) => (
              <tr key={investment.id} className="text-[#46404b]">
                <td className="px-5 py-4 font-semibold text-[#151515]">{investment.ticker}</td>
                <td className="px-5 py-4">{formatDate(investment.purchaseDate)}</td>
                <td className="px-5 py-4">{formatShares(investment.shares)}</td>
                <td className="px-5 py-4">{formatCurrency(investment.purchasePrice)}</td>
                <td className="px-5 py-4 font-semibold text-[#151515]">{formatCurrency(investment.amountInvested)}</td>
                <td className="px-5 py-4"><div className="flex justify-end"><PurchaseActions investment={investment} onEdit={onEdit} onDelete={onDelete} /></div></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function PurchaseActions({ investment, onEdit, onDelete }: { investment: Investment; onEdit: (investment: Investment) => void; onDelete: (investment: Investment) => void }) {
  return (
    <div className="flex gap-1">
      <button type="button" onClick={() => onEdit(investment)} aria-label={`Edit ${investment.ticker} purchase`} className="flex h-8 w-8 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#fff3ed] hover:text-[#ff5a1f]"><Pencil size={15} /></button>
      <button type="button" onClick={() => onDelete(investment)} aria-label={`Delete ${investment.ticker} purchase`} className="flex h-8 w-8 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#fff1f0] hover:text-[#b42318]"><Trash2 size={15} /></button>
    </div>
  );
}

function InvestmentForm({ investment, onClose, onSave }: { investment: Investment | null; onClose: () => void; onSave: (payload: InvestmentPayload) => Promise<void> }) {
  const [ticker, setTicker] = useState(investment?.ticker ?? "");
  const [shares, setShares] = useState(investment ? String(investment.shares) : "");
  const [purchasePrice, setPurchasePrice] = useState(investment ? String(investment.purchasePrice) : "");
  const [purchaseDate, setPurchaseDate] = useState(investment?.purchaseDate ?? getToday());
  const [formError, setFormError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError("");
    const normalizedTicker = ticker.trim().toUpperCase();
    const parsedShares = Number(shares);
    const parsedPrice = Number(purchasePrice);

    if (!normalizedTicker) {
      setFormError("Ticker cannot be empty.");
      return;
    }
    if (!/^[A-Z0-9][A-Z0-9.-]*$/.test(normalizedTicker)) {
      setFormError("Ticker may contain letters, numbers, periods, and hyphens.");
      return;
    }
    if (!Number.isFinite(parsedShares) || parsedShares <= 0) {
      setFormError("Shares must be greater than 0.");
      return;
    }
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      setFormError("Purchase price must be greater than 0.");
      return;
    }
    if (!purchaseDate || purchaseDate > getToday()) {
      setFormError("Purchase date is required and cannot be in the future.");
      return;
    }

    setIsSubmitting(true);
    try {
      await onSave({ ticker: normalizedTicker, shares: parsedShares, purchasePrice: parsedPrice, purchaseDate });
    } catch (error) {
      setFormError(getApiErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#101828]/45 p-4" role="presentation">
      <section role="dialog" aria-modal="true" aria-labelledby="investment-form-title" className="w-full max-w-lg rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-xl">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 id="investment-form-title" className="text-lg font-semibold text-[#151515]">{investment ? "Edit Investment" : "Add Investment"}</h2>
            <p className="mt-1 text-sm text-[#77717d]">Record one completed investment purchase.</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close investment form" className="flex h-9 w-9 items-center justify-center rounded-lg text-[#77717d] hover:bg-[#f5f3f6]"><X size={18} /></button>
        </div>
        <form onSubmit={handleSubmit} className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="grid gap-2 text-sm font-semibold text-[#46404b] sm:col-span-2">Ticker<Input value={ticker} onChange={(event) => setTicker(event.target.value.toUpperCase())} maxLength={20} placeholder="AAPL" autoComplete="off" required className="uppercase" /></label>
          <label className="grid gap-2 text-sm font-semibold text-[#46404b]">Shares<Input type="number" min="0.000001" step="0.000001" value={shares} onChange={(event) => setShares(event.target.value)} placeholder="10 or 0.125" required /></label>
          <label className="grid gap-2 text-sm font-semibold text-[#46404b]">Purchase Price<Input type="number" min="0.0001" step="0.0001" value={purchasePrice} onChange={(event) => setPurchasePrice(event.target.value)} placeholder="180.00" required /></label>
          <label className="grid gap-2 text-sm font-semibold text-[#46404b] sm:col-span-2">Purchase Date<Input type="date" max={getToday()} value={purchaseDate} onChange={(event) => setPurchaseDate(event.target.value)} required /></label>
          {formError && <p role="alert" className="rounded-xl bg-[#fff1f0] px-3 py-2 text-sm font-medium text-[#b42318] sm:col-span-2">{formError}</p>}
          <div className="mt-1 flex justify-end gap-3 sm:col-span-2">
            <button type="button" onClick={onClose} className="h-10 rounded-xl border border-[#e4e0e7] px-4 text-sm font-semibold text-[#46404b] hover:border-[#ff5a1f]">Cancel</button>
            <button type="submit" disabled={isSubmitting} className="h-10 rounded-xl bg-[#ff5a1f] px-5 text-sm font-semibold text-white hover:bg-[#e64d17] disabled:bg-[#f4a384]">{isSubmitting ? "Saving…" : investment ? "Save Changes" : "Add Purchase"}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

function LoadingState() {
  return <div className="grid gap-4" aria-label="Loading investments"><div className="h-24 animate-pulse rounded-2xl border border-[#e4e0e7] bg-white" /><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-52 animate-pulse rounded-2xl border border-[#e4e0e7] bg-white" />)}</div></div>;
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => Promise<void> }) {
  return <section className="rounded-2xl border border-[#f1c6c1] bg-white p-6 shadow-sm"><p role="alert" className="text-sm font-medium text-[#b42318]">{message}</p><button type="button" onClick={() => void onRetry()} className="mt-4 rounded-xl bg-[#15151b] px-5 py-2.5 text-sm font-semibold text-white">Try again</button></section>;
}

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return <section className="rounded-2xl border border-[#e4e0e7] bg-white px-6 py-12 text-center shadow-sm"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#fff3ed] text-[#ff5a1f]"><TrendingUp size={22} /></div><h2 className="mt-5 text-lg font-semibold text-[#151515]">No investments recorded yet.</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-[#77717d]">Add your first investment purchase to start tracking how much you have invested.</p><button type="button" onClick={onAdd} className="mt-5 inline-flex h-10 items-center gap-2 rounded-xl bg-[#ff5a1f] px-4 text-sm font-semibold text-white hover:bg-[#e64d17]"><Plus size={16} /> Add Investment</button></section>;
}

function NoSearchResults({ query }: { query: string }) {
  return <div className="rounded-xl border border-dashed border-[#d8d3dc] bg-white px-5 py-8 text-center text-sm text-[#77717d]">No investments match “{query.trim()}”.</div>;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
}

function formatShares(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 6 }).format(value);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(new Date(`${value}T00:00:00`));
}

function getToday() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function getApiErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = error.response as { data?: { message?: string; fieldErrors?: Record<string, string> } };
    const fieldError = response.data?.fieldErrors ? Object.values(response.data.fieldErrors)[0] : undefined;
    return fieldError ?? response.data?.message ?? "Investment request failed.";
  }
  return error instanceof Error ? error.message : "Investment request failed.";
}
