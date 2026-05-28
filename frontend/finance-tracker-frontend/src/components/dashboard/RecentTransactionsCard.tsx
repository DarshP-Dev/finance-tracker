"use client";

import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import type { Transaction } from "@/types/transactions";

type RecentTransactionsCardProps = {
  transactions: Transaction[];
};

export function RecentTransactionsCard({ transactions }: RecentTransactionsCardProps) {
  return (
    <section className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-[#151515]">Transaction</h2>
          <p className="mt-1 text-sm text-[#77717d]">Latest account activity</p>
        </div>
        <button className="rounded-xl border border-[#e4e0e7] px-3 py-2 text-xs font-semibold">Filters</button>
      </div>

      {transactions.length === 0 ? (
        <div className="flex h-[180px] items-center justify-center rounded-xl border border-dashed border-[#e4e0e7] bg-[#fffaf7] text-sm text-[#77717d]">
          No transactions yet
        </div>
      ) : (
        <div className="divide-y divide-[#eeeaf1]">
          {transactions.map((transaction) => (
            <div key={transaction.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-[#151515]">
                  {transaction.merchant || formatCategory(transaction.category)}
                </div>
                <div className="mt-1 text-xs text-[#77717d]">
                  {formatCategory(transaction.category)} / {formatDate(transaction.date)}
                </div>
              </div>
              <div className={transaction.type === "INCOME" ? "text-sm font-semibold text-[#027a48]" : "text-sm font-semibold text-[#b42318]"}>
                {transaction.type === "INCOME" ? "+" : "-"}
                {formatCurrency(transaction.amount)}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
