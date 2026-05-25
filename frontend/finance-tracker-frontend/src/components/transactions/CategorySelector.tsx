"use client";

import { Select } from "@/components/ui/select";
import { formatCategory } from "@/components/transactions/formatters";
import { transactionCategories, type TransactionCategory } from "@/types/transactions";

type CategorySelectorProps = {
  value: TransactionCategory | "";
  onChange: (value: TransactionCategory | "") => void;
  includeAll?: boolean;
  required?: boolean;
};

export function CategorySelector({ value, onChange, includeAll = false, required = false }: CategorySelectorProps) {
  return (
    <Select
      value={value}
      required={required}
      onChange={(event) => onChange(event.target.value as TransactionCategory | "")}
    >
      {includeAll && <option value="">All categories</option>}
      {!includeAll && <option value="">Select category</option>}
      {transactionCategories.map((category) => (
        <option key={category} value={category}>
          {formatCategory(category)}
        </option>
      ))}
    </Select>
  );
}
