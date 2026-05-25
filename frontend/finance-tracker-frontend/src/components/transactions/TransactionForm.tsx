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
};

const today = new Date().toISOString().slice(0, 10);

export function TransactionForm({ transaction, isSubmitting, onSubmit, onCancel }: TransactionFormProps) {
  const [amount, setAmount] = useState(() => transaction ? String(transaction.amount) : "");
  const [category, setCategory] = useState<TransactionCategory | "">(() => transaction?.category ?? "");
  const [type, setType] = useState<TransactionType>(() => transaction?.type ?? "EXPENSE");
  const [description, setDescription] = useState(() => transaction?.description ?? "");
  const [date, setDate] = useState(() => transaction?.date ?? today);
  const [merchant, setMerchant] = useState(() => transaction?.merchant ?? "");

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
    }
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Type">
          <Select value={type} onChange={(event) => setType(event.target.value as TransactionType)}>
            <option value="EXPENSE">Expense</option>
            <option value="INCOME">Income</option>
          </Select>
        </Field>
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

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Category">
          <CategorySelector required value={category} onChange={setCategory} />
        </Field>
        <Field label="Date">
          <Input required type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </Field>
      </div>

      <Field label="Merchant">
        <Input value={merchant} onChange={(event) => setMerchant(event.target.value)} maxLength={150} />
      </Field>

      <Field label="Description">
        <Input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} />
      </Field>

      <div className="flex flex-col gap-2 sm:flex-row">
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
