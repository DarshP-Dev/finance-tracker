"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { formatCurrency, formatDate } from "@/components/transactions/formatters";
import { getUpcomingRecurringTransactions } from "@/lib/api";
import type { UpcomingRecurringTransaction } from "@/types/recurring-transactions";

export function UpcomingTransactionsCard() {
  const [entries, setEntries] = useState<UpcomingRecurringTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      setEntries((await getUpcomingRecurringTransactions()).slice(0, 5));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to load upcoming transactions.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeoutId);
  }, [load]);

  return (
    <section className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-[#151515]">Upcoming</h2>
          <p className="mt-1 text-xs text-[#77717d]">Next 30 days · recurring schedules only</p>
        </div>
        <Link href="/transactions?tab=upcoming" className="text-sm font-semibold text-[#195b4d] hover:underline">View all →</Link>
      </div>
      {isLoading ? (
        <p className="py-6 text-sm text-[#667085]">Loading upcoming transactions</p>
      ) : error ? (
        <div role="alert" className="py-5 text-sm text-[#b42318]">
          <p>{error}</p>
          <button type="button" onClick={() => void load()} className="mt-2 font-semibold underline">Try again</button>
        </div>
      ) : entries.length === 0 ? (
        <p className="py-6 text-sm text-[#667085]">No recurring transactions are scheduled in the next 30 days.</p>
      ) : (
        <ul className="mt-4 divide-y divide-[#e4e0e7]">
          {entries.map((entry) => (
            <li key={`${entry.recurringTransactionId}-${entry.scheduledDate}`} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-3 text-sm">
              <div className="min-w-0">
                <p className="break-words font-medium text-[#151515]">{entry.description}</p>
                <p className="text-xs text-[#77717d]">{formatDate(entry.scheduledDate)}</p>
              </div>
              <p className={entry.type === "INCOME" ? "font-semibold text-[#027a48]" : "font-semibold text-[#b42318]"}>
                {entry.type === "INCOME" ? "+" : "−"}{formatCurrency(entry.amount)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
