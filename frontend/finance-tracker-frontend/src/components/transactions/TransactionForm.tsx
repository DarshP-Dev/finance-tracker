"use client";

import { FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { CategorySelector } from "@/components/transactions/CategorySelector";
import type { Transaction, TransactionCategory, TransactionPayload, TransactionType } from "@/types/transactions";

type TransactionFormProps = {
  transaction?: Transaction | null;
  isSubmitting: boolean;
  onSubmit: (payload: TransactionPayload) => Promise<void>;
  onCancel?: () => void;
  layout?: "stacked" | "horizontal";
};

const today = new Date().toISOString().slice(0, 10);

export function TransactionForm({ transaction, isSubmitting, onSubmit, onCancel, layout = "stacked" }: TransactionFormProps) {
  const [amount, setAmount] = useState(() => transaction ? String(transaction.amount) : "");
  const [category, setCategory] = useState<TransactionCategory | "">(() => transaction?.category ?? "");
  const [type, setType] = useState<TransactionType>(() => transaction?.type ?? "EXPENSE");
  const [description, setDescription] = useState(() => transaction?.description ?? "");
  const [date, setDate] = useState(() => transaction?.date ?? today);
  const [merchant, setMerchant] = useState(() => transaction?.merchant ?? "");
  const [isMonthlyRecurring, setIsMonthlyRecurring] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!category) {
      return;
    }

    await onSubmit({
      amount: Number(amount),
      category,
      type,
      description: description.trim(),
      date,
      merchant: merchant.trim(),
    });

    if (!transaction) {
      setAmount("");
      setCategory("");
      setType("EXPENSE");
      setDescription("");
      setDate(today);
      setMerchant("");
      setIsMonthlyRecurring(false);
    }
  }

  const horizontal = layout === "horizontal";

  return (
    <form onSubmit={handleSubmit} className={horizontal ? "grid gap-4 lg:grid-cols-2 xl:grid-cols-12 xl:items-end" : "grid gap-4 sm:grid-cols-2"}>
      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label="Type">
          <Select value={type} onChange={(event) => setType(event.target.value as TransactionType)}>
            <option value="EXPENSE">Expense</option>
            <option value="INCOME">Income</option>
          </Select>
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label="Amount">
          <Input
            required
            min="0.01"
            step="0.01"
            type="number"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="0.00"
          />
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label="Category">
          <CategorySelector required value={category} onChange={setCategory} />
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label="Date">
          <Input required type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : "sm:col-span-2"}>
        <Field label="Merchant">
          <Input value={merchant} onChange={(event) => setMerchant(event.target.value)} maxLength={150} />
        </Field>
      </div>

      {horizontal && (
        <RecurringControl
          compact
          checked={isMonthlyRecurring}
          onChange={setIsMonthlyRecurring}
          className="xl:col-span-2"
        />
      )}

      <div className={horizontal ? "lg:col-span-1 xl:col-span-8" : "sm:col-span-2"}>
        <Field label="Description">
          <Input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} />
        </Field>
      </div>

      {!horizontal && (
        <RecurringControl checked={isMonthlyRecurring} onChange={setIsMonthlyRecurring} className="sm:col-span-2" />
      )}

      <div className={horizontal ? "flex flex-col gap-2 sm:flex-row lg:justify-end xl:col-span-4" : "flex flex-col gap-2 sm:col-span-2 sm:flex-row"}>
        <Button type="submit" disabled={isSubmitting} className="sm:min-w-[132px]">
          {isSubmitting ? "Saving" : transaction ? "Save changes" : "Add transaction"}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}

function RecurringControl({ checked, compact = false, className = "", onChange }: { checked: boolean; compact?: boolean; className?: string; onChange: (checked: boolean) => void }) {
  if (compact) {
    return (
      <div className={`flex flex-col gap-1.5 text-sm font-medium text-[#344054] ${className}`}>
        <span>Recurring</span>
        <label className="flex h-10 items-center justify-between rounded-md border border-[#cfd8e6] bg-white px-3 text-sm font-semibold text-[#46404b]">
          Monthly
          <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="h-5 w-5 accent-[#ff5a1f]" />
        </label>
      </div>
    );
  }

  return (
    <label className={`flex items-center justify-between rounded-xl border border-[#e4e0e7] px-3 py-3 text-sm font-semibold text-[#46404b] ${className}`}>
      <span>
        Monthly recurring
        <span className="mt-0.5 block text-xs font-normal text-[#77717d]">Marks this as a monthly transaction for planning.</span>
      </span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="h-5 w-5 accent-[#ff5a1f]" />
    </label>
  );
}
