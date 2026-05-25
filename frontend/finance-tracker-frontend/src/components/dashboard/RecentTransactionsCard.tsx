"use client";

import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import type { Transaction } from "@/types/transactions";

type RecentTransactionsCardProps = {
  transactions: Transaction[];
};

export function RecentTransactionsCard({ transactions }: RecentTransactionsCardProps) {
  return (
    <section className="rounded-lg border border-[#d9e1ec] bg-white p-5 shadow-sm">
      <div className="mb-4">
        <h2 className="text-base font-semibold text-[#172033]">Recent Transactions</h2>
        <p className="mt-1 text-sm text-[#667085]">Latest account activity</p>
      </div>

      {transactions.length === 0 ? (
        <div className="flex h-[180px] items-center justify-center rounded-md border border-dashed border-[#cfd8e6] bg-[#f8fafc] text-sm text-[#667085]">
          No transactions yet
        </div>
      ) : (
        <div className="divide-y divide-[#edf1f6]">
          {transactions.map((transaction) => (
            <div key={transaction.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-[#172033]">
                  {transaction.merchant || formatCategory(transaction.category)}
                </div>
                <div className="mt-1 text-xs text-[#667085]">
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
