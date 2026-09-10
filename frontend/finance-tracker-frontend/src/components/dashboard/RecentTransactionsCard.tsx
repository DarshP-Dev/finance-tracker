"use client";

import { useState } from "react";
import { Search, SlidersHorizontal } from "lucide-react";
import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import { transactionCategories, type Transaction } from "@/types/transactions";

const PAGE_SIZE = 8;

export function RecentTransactionsCard({ transactions }: { transactions: Transaction[] }) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [category, setCategory] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [page, setPage] = useState(1);
  const activeFilters = Number(Boolean(type)) + Number(Boolean(category));
  const search = query.trim().toLowerCase();
  const filtered = transactions.filter((transaction) =>
    (!type || transaction.type === type) &&
    (!category || transaction.category === category) &&
    (!search || [transaction.merchant, transaction.description, formatCategory(transaction.category)]
      .some((value) => value?.toLowerCase().includes(search))),
  ).sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pages);
  const offset = (currentPage - 1) * PAGE_SIZE;

  function clearFilters() {
    setQuery("");
    setType("");
    setCategory("");
    setPage(1);
  }

  return (
    <section aria-labelledby="dashboard-transactions-title" className="min-w-0 overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm">
      <div className="flex flex-col justify-between gap-4 p-5 sm:flex-row sm:items-center">
        <div>
          <h2 id="dashboard-transactions-title" className="text-base font-semibold text-[#151515]">Transactions</h2>
          <p className="mt-1 text-xs text-[#77717d]">Account activity in your selected date range</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-0 flex-1 sm:w-64">
            <Search size={16} aria-hidden="true" className="pointer-events-none absolute left-3 top-3 text-[#77717d]" />
            <input type="search" aria-label="Search transactions" placeholder="Search transactions…"
              value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }}
              className="h-10 w-full rounded-lg border border-[#e4e0e7] bg-white pl-9 pr-3 text-sm text-[#151515] outline-none focus:border-[#ff5a1f] focus:ring-2 focus:ring-[#ff5a1f]/20" />
          </div>
          <button type="button" aria-expanded={filtersOpen} aria-controls="transaction-filters" onClick={() => setFiltersOpen(!filtersOpen)} className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#e4e0e7] px-3 text-sm font-medium text-[#151515] hover:bg-[#fff7f2]">
            <SlidersHorizontal size={15} aria-hidden="true" />
            Filters{activeFilters > 0 ? ` (${activeFilters})` : ""}
          </button>
        </div>
      </div>

      {filtersOpen && (
        <div id="transaction-filters" className="flex flex-wrap items-end gap-4 border-t border-[#e4e0e7] bg-[#f8fafc] px-5 py-4">
          <label className="grid gap-1.5 text-xs font-medium text-[#46404b]">
            Type
            <select value={type} onChange={(event) => { setType(event.target.value); setPage(1); }} className="h-10 rounded-lg border border-[#e4e0e7] bg-white px-3 text-sm">
              <option value="">All types</option><option value="INCOME">Income</option><option value="EXPENSE">Expense</option>
            </select>
          </label>
          <label className="grid gap-1.5 text-xs font-medium text-[#46404b]">
            Category
            <select value={category} onChange={(event) => { setCategory(event.target.value); setPage(1); }} className="h-10 rounded-lg border border-[#e4e0e7] bg-white px-3 text-sm">
              <option value="">All categories</option>
              {transactionCategories.map((value) => <option key={value} value={value}>{formatCategory(value)}</option>)}
            </select>
          </label>
          <button type="button" onClick={clearFilters} className="h-10 px-2 text-sm text-[#77717d] underline underline-offset-4">Clear all</button>
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] border-collapse text-left text-sm">
          <caption className="sr-only">Transactions for the selected reporting period, newest first</caption>
          <thead className="border-y border-[#e4e0e7] bg-[#f8fafc] text-xs uppercase tracking-wide text-[#77717d]">
            <tr>
              <th scope="col" className="px-5 py-3 font-medium">Merchant</th>
              <th scope="col" className="px-5 py-3 font-medium">Date</th>
              <th scope="col" className="px-5 py-3 text-right font-medium">Amount</th>
              <th scope="col" className="px-5 py-3 font-medium">Type</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e4e0e7]">
            {filtered.slice(offset, offset + PAGE_SIZE).map((transaction) => (
              <tr key={transaction.id} className="text-[#151515] hover:bg-[#fff7f2]">
                <td className="px-5 py-3">
                  <div className="max-w-[320px] truncate font-medium" title={transaction.merchant || "Unassigned"}>{transaction.merchant || "Unassigned"}</div>
                  <div className="mt-0.5 text-xs text-[#77717d]">{formatCategory(transaction.category)}</div>
                </td>
                <td className="whitespace-nowrap px-5 py-3 text-[#77717d]">{formatDate(transaction.date)}</td>
                <td className={`whitespace-nowrap px-5 py-3 text-right font-medium tabular-nums ${transaction.type === "INCOME" ? "text-[#027a48]" : "text-[#151515]"}`}>
                  {transaction.type === "INCOME" ? "+" : "−"}{formatCurrency(transaction.amount)}
                </td>
                <td className="px-5 py-3">
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs ${transaction.type === "INCOME" ? "bg-[#edf8f1] text-[#027a48]" : "bg-[#fff3ed] text-[#b54708]"}`}>
                    <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                    {transaction.type === "INCOME" ? "Income" : "Expense"}
                  </span>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && <tr><td colSpan={4} className="px-5 py-10 text-center text-[#77717d]">
              <p>{transactions.length === 0 ? "No transactions in this date range." : "No transactions match your search or filters."}</p>
              {(query || activeFilters > 0) && <button type="button" onClick={clearFilters} className="mt-2 underline underline-offset-4">Clear search and filters</button>}
            </td></tr>}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#e4e0e7] px-5 py-3 text-xs text-[#77717d]">
        <p role="status">{filtered.length ? `${offset + 1}–${Math.min(offset + PAGE_SIZE, filtered.length)}` : "0"} of {filtered.length} transactions</p>
        <div className="flex items-center gap-3">
          <button type="button" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)} className="rounded-lg border border-[#e4e0e7] px-3 py-2 disabled:opacity-40">Previous</button>
          <span>Page {currentPage} of {pages}</span>
          <button type="button" disabled={currentPage === pages} onClick={() => setPage(currentPage + 1)} className="rounded-lg border border-[#e4e0e7] px-3 py-2 disabled:opacity-40">Next</button>
        </div>
      </div>
    </section>
  );
}
