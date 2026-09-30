"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { CategorySelector } from "@/components/transactions/CategorySelector";
import type { RecurringFrequency, RecurringTransaction, RecurringTransactionPayload } from "@/types/recurring-transactions";
import type { Transaction, TransactionCategory, TransactionPayload, TransactionType } from "@/types/transactions";

export type TransactionFormSubmission =
  | { recurring: false; payload: TransactionPayload }
  | { recurring: true; payload: RecurringTransactionPayload };

type TransactionFormProps = {
  transaction?: Transaction | null;
  recurringTransaction?: RecurringTransaction | null;
  initialRecurring?: boolean;
  isSubmitting: boolean;
  submitError?: string;
  onSubmit: (submission: TransactionFormSubmission) => Promise<boolean>;
  onCancel?: () => void;
  layout?: "stacked" | "horizontal";
};

const today = new Date().toISOString().slice(0, 10);

export function TransactionForm({
  transaction,
  recurringTransaction,
  initialRecurring = false,
  isSubmitting,
  submitError,
  onSubmit,
  onCancel,
  layout = "stacked",
}: TransactionFormProps) {
  const editing = Boolean(transaction || recurringTransaction);
  const [amount, setAmount] = useState(() => String(transaction?.amount ?? recurringTransaction?.amount ?? ""));
  const [category, setCategory] = useState<TransactionCategory | "">(() => transaction?.category ?? recurringTransaction?.category ?? "");
  const [type, setType] = useState<TransactionType>(() => transaction?.type ?? recurringTransaction?.type ?? "EXPENSE");
  const [description, setDescription] = useState(() => transaction?.description ?? recurringTransaction?.description ?? "");
  const [date, setDate] = useState(() => transaction?.date ?? recurringTransaction?.startDate ?? today);
  const [merchant, setMerchant] = useState(() => transaction?.merchant ?? recurringTransaction?.merchant ?? "");
  const [isRecurring, setIsRecurring] = useState(Boolean(recurringTransaction || initialRecurring));
  const [frequency, setFrequency] = useState<RecurringFrequency>(() => recurringTransaction?.frequency ?? "MONTHLY");
  const [endDate, setEndDate] = useState(() => recurringTransaction?.endDate ?? "");
  const [dateError, setDateError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!category) return;

    if (isRecurring && endDate && endDate < date) {
      setDateError("End date must be on or after the start date.");
      return;
    }
    setDateError("");

    const common = {
      amount: Number(amount),
      category,
      type,
      description: description.trim(),
      merchant: merchant.trim(),
    };
    const submission: TransactionFormSubmission = isRecurring
      ? { recurring: true, payload: { ...common, frequency, startDate: date, endDate: endDate || null } }
      : { recurring: false, payload: { ...common, date } };

    const saved = await onSubmit(submission);
    if (saved && !editing) {
      setAmount("");
      setCategory("");
      setType("EXPENSE");
      setDescription("");
      setDate(today);
      setMerchant("");
      setIsRecurring(initialRecurring);
      setFrequency("MONTHLY");
      setEndDate("");
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
          <Input required min="0.01" step="0.01" type="number" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0.00" />
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label="Category">
          <CategorySelector required value={category} onChange={setCategory} />
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : undefined}>
        <Field label={isRecurring ? "Start Date" : "Date"}>
          <Input required type="date" value={date} onChange={(event) => { setDate(event.target.value); setDateError(""); }} />
          {isRecurring && <span className="text-xs font-normal text-[#667085]">First scheduled transaction. Creating a schedule does not add a transaction yet.</span>}
        </Field>
      </div>

      <div className={horizontal ? "xl:col-span-2" : "sm:col-span-2"}>
        <Field label="Merchant">
          <Input value={merchant} onChange={(event) => setMerchant(event.target.value)} maxLength={150} />
        </Field>
      </div>

      {horizontal && !editing && !initialRecurring && (
        <RecurringControl checked={isRecurring} onChange={setIsRecurring} className="xl:col-span-2" />
      )}

      <div className={horizontal ? "lg:col-span-1 xl:col-span-8" : "sm:col-span-2"}>
        <Field label="Description">
          <Input required={isRecurring} value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} />
        </Field>
      </div>

      {!horizontal && !editing && !initialRecurring && (
        <RecurringControl checked={isRecurring} onChange={setIsRecurring} className="sm:col-span-2" />
      )}

      {isRecurring && (
        <>
          <div className={horizontal ? "xl:col-span-2" : undefined}>
            <Field label="Frequency">
              <Select value={frequency} onChange={(event) => setFrequency(event.target.value as RecurringFrequency)}>
                <option value="WEEKLY">Weekly</option>
                <option value="BIWEEKLY">Biweekly</option>
                <option value="MONTHLY">Monthly</option>
                <option value="YEARLY">Yearly</option>
              </Select>
            </Field>
          </div>
          <div className={horizontal ? "xl:col-span-2" : undefined}>
            <Field label="End Date (Optional)">
              <Input type="date" min={date} value={endDate} onChange={(event) => { setEndDate(event.target.value); setDateError(""); }} />
            </Field>
          </div>
        </>
      )}

      {(dateError || submitError) && (
        <p role="alert" className="rounded-md bg-[#fff1f0] px-3 py-2 text-sm font-medium text-[#b42318] sm:col-span-2 xl:col-span-12">
          {dateError || submitError}
        </p>
      )}

      <div className={horizontal ? "flex flex-col gap-2 sm:flex-row lg:justify-end xl:col-span-4" : "flex flex-col gap-2 sm:col-span-2 sm:flex-row"}>
        <Button type="submit" disabled={isSubmitting} className="sm:min-w-[132px]">
          {isSubmitting ? "Saving" : editing ? "Save changes" : isRecurring ? "Add recurring" : "Add transaction"}
        </Button>
        {onCancel && <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  );
}

function RecurringControl({ checked, className = "", onChange }: { checked: boolean; className?: string; onChange: (checked: boolean) => void }) {
  return (
    <label className={`flex items-center justify-between gap-3 rounded-xl border border-[#e4e0e7] px-3 py-3 text-sm font-semibold text-[#46404b] ${className}`}>
      <span>
        Recurring
        <span className="mt-0.5 block text-xs font-normal text-[#77717d]">Create a schedule instead of a one-time transaction.</span>
      </span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="h-5 w-5 shrink-0 accent-[#195b4d]" />
    </label>
  );
}
