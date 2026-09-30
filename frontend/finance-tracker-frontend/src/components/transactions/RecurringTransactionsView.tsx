"use client";

import { Button } from "@/components/ui/button";
import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import { recurringFrequencyLabels, type RecurringTransaction } from "@/types/recurring-transactions";

type RecurringTransactionsViewProps = {
  transactions: RecurringTransaction[];
  isLoading: boolean;
  error: string;
  message: string;
  pendingId: number | null;
  onRetry: () => Promise<void>;
  onAdd: () => void;
  onEdit: (transaction: RecurringTransaction) => void;
  onToggle: (transaction: RecurringTransaction) => Promise<void>;
  onDelete: (transaction: RecurringTransaction) => Promise<void>;
};

export function RecurringTransactionsView({
  transactions,
  isLoading,
  error,
  message,
  pendingId,
  onRetry,
  onAdd,
  onEdit,
  onToggle,
  onDelete,
}: RecurringTransactionsViewProps) {
  return (
    <section className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[#172033]">Recurring transactions</h2>
          <p className="mt-1 text-sm text-[#667085]">Manage scheduled income and expenses. Transactions appear in history when generated.</p>
        </div>
        {transactions.length > 0 && <Button type="button" onClick={onAdd}>Add recurring transaction</Button>}
      </div>

      {message && <p role="status" className="mt-4 rounded-xl bg-[#ecfdf3] px-4 py-3 text-sm font-medium text-[#027a48]">{message}</p>}
      {error && (
        <div role="alert" className="mt-4 rounded-xl border border-[#f1c6c1] bg-[#fff1f0] px-4 py-3 text-sm text-[#b42318]">
          <p>{error}</p>
          <Button type="button" variant="secondary" className="mt-3 h-8 px-3" onClick={() => void onRetry()}>Try again</Button>
        </div>
      )}

      {isLoading ? (
        <p className="py-10 text-center text-sm text-[#667085]">Loading recurring transactions</p>
      ) : transactions.length === 0 && !error ? (
        <div className="py-12 text-center">
          <p className="text-sm text-[#667085]">No recurring transactions yet.</p>
          <Button type="button" variant="secondary" className="mt-4" onClick={onAdd}>Add recurring transaction</Button>
        </div>
      ) : (
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          {transactions.map((transaction) => {
            const ended = !transaction.active && Boolean(transaction.endDate && transaction.nextOccurrence > transaction.endDate);
            const status = ended ? "Ended" : transaction.active ? "Active" : "Paused";
            return (
              <article key={transaction.id} className="min-w-0 rounded-xl border border-[#e4e0e7] bg-[#fcfcfd] p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="break-words font-semibold text-[#172033]">{transaction.description}</h3>
                    {transaction.merchant && <p className="mt-0.5 break-words text-sm text-[#667085]">{transaction.merchant}</p>}
                  </div>
                  <div className={transaction.type === "INCOME" ? "font-semibold text-[#027a48]" : "font-semibold text-[#b42318]"}>
                    {transaction.type === "INCOME" ? "+" : "-"}{formatCurrency(transaction.amount)}
                  </div>
                </div>

                <div className="mt-3 flex flex-wrap gap-2 text-xs font-semibold">
                  <span className={transaction.type === "INCOME" ? "rounded-md bg-[#ecfdf3] px-2 py-1 text-[#027a48]" : "rounded-md bg-[#fff4ed] px-2 py-1 text-[#b54708]"}>
                    {transaction.type === "INCOME" ? "Income" : "Expense"}
                  </span>
                  <span className={transaction.active ? "rounded-md bg-[#ecfdf3] px-2 py-1 text-[#027a48]" : "rounded-md bg-[#f2f4f7] px-2 py-1 text-[#475467]"}>
                    {status}
                  </span>
                </div>

                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                  <div><dt className="text-[#667085]">Category</dt><dd className="font-medium text-[#172033]">{formatCategory(transaction.category)}</dd></div>
                  <div><dt className="text-[#667085]">Frequency</dt><dd className="font-medium text-[#172033]">{recurringFrequencyLabels[transaction.frequency]}</dd></div>
                  <div><dt className="text-[#667085]">Next occurrence</dt><dd className="font-medium text-[#172033]">{formatDate(transaction.nextOccurrence)}</dd></div>
                  {transaction.endDate && <div><dt className="text-[#667085]">End date</dt><dd className="font-medium text-[#172033]">{formatDate(transaction.endDate)}</dd></div>}
                </dl>

                <div className="mt-4 flex flex-wrap gap-2 border-t border-[#e4e0e7] pt-4">
                  <Button type="button" variant="secondary" className="h-8 px-3" disabled={pendingId === transaction.id} onClick={() => onEdit(transaction)}>Edit</Button>
                  {!ended && (
                    <Button type="button" variant="secondary" className="h-8 px-3" disabled={pendingId === transaction.id} onClick={() => void onToggle(transaction)}>
                      {transaction.active ? "Pause" : "Resume"}
                    </Button>
                  )}
                  <Button type="button" variant="danger" className="h-8 px-3" disabled={pendingId === transaction.id} onClick={() => void onDelete(transaction)}>Delete</Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
