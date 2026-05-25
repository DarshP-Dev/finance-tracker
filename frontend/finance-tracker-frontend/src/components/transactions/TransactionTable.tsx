"use client";

import { Button } from "@/components/ui/button";
import { formatCategory, formatCurrency, formatDate } from "@/components/transactions/formatters";
import type { Transaction } from "@/types/transactions";

type TransactionTableProps = {
  transactions: Transaction[];
  isLoading: boolean;
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
};

export function TransactionTable({ transactions, isLoading, onEdit, onDelete }: TransactionTableProps) {
  return (
    <div className="overflow-hidden bg-white">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] border-collapse text-left text-sm">
          <thead className="border-b border-[#dfe7f1] bg-[#f7f9fc] text-xs uppercase text-[#667085]">
            <tr>
              <th className="px-4 py-3 font-semibold">Date</th>
              <th className="px-4 py-3 font-semibold">Merchant</th>
              <th className="px-4 py-3 font-semibold">Category</th>
              <th className="px-4 py-3 font-semibold">Type</th>
              <th className="px-4 py-3 text-right font-semibold">Amount</th>
              <th className="px-4 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#edf1f6]">
            {isLoading && (
              <tr>
                <td className="px-4 py-8 text-center text-[#667085]" colSpan={6}>
                  Loading transactions
                </td>
              </tr>
            )}

            {!isLoading && transactions.length === 0 && (
              <tr>
                <td className="px-4 py-8 text-center text-[#667085]" colSpan={6}>
                  No transactions found
                </td>
              </tr>
            )}

            {!isLoading && transactions.map((transaction) => (
              <tr key={transaction.id} className="text-[#172033]">
                <td className="whitespace-nowrap px-4 py-3">{formatDate(transaction.date)}</td>
                <td className="px-4 py-3">
                  <div className="font-medium">{transaction.merchant || "Unassigned"}</div>
                  {transaction.description && (
                    <div className="mt-0.5 max-w-[280px] truncate text-xs text-[#667085]">
                      {transaction.description}
                    </div>
                  )}
                </td>
                <td className="whitespace-nowrap px-4 py-3">{formatCategory(transaction.category)}</td>
                <td className="px-4 py-3">
                  <span
                    className={
                      transaction.type === "INCOME"
                        ? "inline-flex rounded-md bg-[#ecfdf3] px-2 py-1 text-xs font-semibold text-[#027a48]"
                        : "inline-flex rounded-md bg-[#fff4ed] px-2 py-1 text-xs font-semibold text-[#b54708]"
                    }
                  >
                    {transaction.type === "INCOME" ? "Income" : "Expense"}
                  </span>
                </td>
                <td
                  className={
                    transaction.type === "INCOME"
                      ? "whitespace-nowrap px-4 py-3 text-right font-semibold text-[#027a48]"
                      : "whitespace-nowrap px-4 py-3 text-right font-semibold text-[#b42318]"
                  }
                >
                  {transaction.type === "INCOME" ? "+" : "-"}
                  {formatCurrency(transaction.amount)}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" className="h-8 px-3" onClick={() => onEdit(transaction)}>
                      Edit
                    </Button>
                    <Button type="button" variant="danger" className="h-8 px-3" onClick={() => onDelete(transaction)}>
                      Delete
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
