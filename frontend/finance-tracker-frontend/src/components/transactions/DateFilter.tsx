"use client";

import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { CategorySelector } from "@/components/transactions/CategorySelector";
import type { TransactionFilters } from "@/types/transactions";

type DateFilterProps = {
  filters: TransactionFilters;
  onChange: (filters: TransactionFilters) => void;
};

export function DateFilter({ filters, onChange }: DateFilterProps) {
  return (
    <div className="grid gap-3 border-b border-[#dfe7f1] bg-white px-4 py-4 md:grid-cols-[repeat(6,minmax(0,1fr))_auto] md:items-end">
      <Field label="Exact date">
        <Input
          type="date"
          value={filters.date ?? ""}
          onChange={(event) => onChange({ ...filters, date: event.target.value, startDate: "", endDate: "" })}
        />
      </Field>
      <Field label="From">
        <Input
          type="date"
          value={filters.startDate ?? ""}
          onChange={(event) => onChange({ ...filters, startDate: event.target.value, date: "" })}
        />
      </Field>
      <Field label="To">
        <Input
          type="date"
          value={filters.endDate ?? ""}
          onChange={(event) => onChange({ ...filters, endDate: event.target.value, date: "" })}
        />
      </Field>
      <Field label="Category">
        <CategorySelector
          includeAll
          value={filters.category ?? ""}
          onChange={(category) => onChange({ ...filters, category })}
        />
      </Field>
      <Field label="Min amount">
        <Input
          min="0"
          step="0.01"
          type="number"
          value={filters.minAmount ?? ""}
          onChange={(event) => onChange({ ...filters, minAmount: event.target.value })}
        />
      </Field>
      <Field label="Max amount">
        <Input
          min="0"
          step="0.01"
          type="number"
          value={filters.maxAmount ?? ""}
          onChange={(event) => onChange({ ...filters, maxAmount: event.target.value })}
        />
      </Field>
      <Button
        type="button"
        variant="secondary"
        className="md:w-[92px]"
        onClick={() => onChange({})}
      >
        Reset
      </Button>
    </div>
  );
}
