"use client";

import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { CategorySelector } from "@/components/transactions/CategorySelector";
import { formatCurrency } from "@/components/transactions/formatters";
import type { TransactionFilters } from "@/types/transactions";

type DateFilterProps = {
  filters: TransactionFilters;
  maxTransactionAmount: number;
  onChange: (filters: TransactionFilters) => void;
};

export function DateFilter({ filters, maxTransactionAmount, onChange }: DateFilterProps) {
  const amountCeiling = Math.max(0, roundCurrency(maxTransactionAmount));
  const minimum = clampAmount(filters.minAmount, 0, amountCeiling);
  const maximum = clampAmount(filters.maxAmount, amountCeiling, amountCeiling);

  function updateMinimum(value: number) {
    onChange({ ...filters, minAmount: String(Math.min(roundCurrency(value), maximum)) });
  }

  function updateMaximum(value: number) {
    onChange({ ...filters, maxAmount: String(Math.min(Math.max(roundCurrency(value), minimum), amountCeiling)) });
  }

  return (
    <div className="border-b border-[#dfe7f1] bg-white px-4 py-5">
      <div className="grid gap-5 xl:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)_minmax(0,1.6fr)_auto]">
        <FilterGroup number="1" title="Category">
          <Field label="Transaction category">
            <CategorySelector
              includeAll
              value={filters.category ?? ""}
              onChange={(category) => onChange({ ...filters, category })}
            />
          </Field>
        </FilterGroup>

        <FilterGroup number="2" title="Date range">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="From">
              <Input
                type="date"
                value={filters.startDate ?? ""}
                max={filters.endDate || undefined}
                onChange={(event) => onChange({ ...filters, startDate: event.target.value })}
              />
            </Field>
            <Field label="To">
              <Input
                type="date"
                value={filters.endDate ?? ""}
                min={filters.startDate || undefined}
                onChange={(event) => onChange({ ...filters, endDate: event.target.value })}
              />
            </Field>
          </div>
        </FilterGroup>

        <FilterGroup number="3" title="Amount range">
          <div className="mt-1 flex items-center justify-between text-xs font-semibold text-[#667085]">
            <span>{formatCurrency(minimum)}</span>
            <span>{formatCurrency(maximum)}</span>
          </div>
          <div className="relative mt-3 h-5">
            <div className="absolute inset-x-0 top-2 h-1.5 rounded-full bg-[#e6e1e8]" />
            {amountCeiling > 0 && (
              <div
                className="absolute top-2 h-1.5 rounded-full bg-[#ff5a1f]"
                style={{
                  left: `${(minimum / amountCeiling) * 100}%`,
                  right: `${100 - (maximum / amountCeiling) * 100}%`,
                }}
              />
            )}
            <RangeInput ariaLabel="Minimum transaction amount" value={minimum} max={amountCeiling} onChange={updateMinimum} />
            <RangeInput ariaLabel="Maximum transaction amount" value={maximum} max={amountCeiling} onChange={updateMaximum} />
          </div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Field label="Minimum">
              <Input
                aria-label="Exact minimum amount"
                disabled={amountCeiling === 0}
                min="0"
                max={maximum}
                step="0.01"
                type="number"
                value={minimum}
                onChange={(event) => updateMinimum(Number(event.target.value))}
              />
            </Field>
            <Field label="Maximum">
              <Input
                aria-label="Exact maximum amount"
                disabled={amountCeiling === 0}
                min={minimum}
                max={amountCeiling}
                step="0.01"
                type="number"
                value={maximum}
                onChange={(event) => updateMaximum(Number(event.target.value))}
              />
            </Field>
          </div>
        </FilterGroup>

        <Button type="button" variant="secondary" className="xl:self-end xl:w-[92px]" onClick={() => onChange({})}>
          Reset
        </Button>
      </div>
    </div>
  );
}

function FilterGroup({ number, title, children }: { number: string; title: string; children: ReactNode }) {
  return (
    <section className="min-w-0">
      <div className="mb-3 flex items-center gap-2">
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#fff3ed] text-[11px] font-bold text-[#e64d17]">{number}</span>
        <h3 className="text-sm font-semibold text-[#344054]">{title}</h3>
      </div>
      {children}
    </section>
  );
}

function RangeInput({ ariaLabel, value, max, onChange }: { ariaLabel: string; value: number; max: number; onChange: (value: number) => void }) {
  return (
    <input
      aria-label={ariaLabel}
      className="pointer-events-none absolute inset-x-0 top-0 h-5 w-full appearance-none bg-transparent [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:h-5 [&::-moz-range-thumb]:w-5 [&::-moz-range-thumb]:cursor-pointer [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-white [&::-moz-range-thumb]:bg-[#ff5a1f] [&::-moz-range-thumb]:shadow-md [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white [&::-webkit-slider-thumb]:bg-[#ff5a1f] [&::-webkit-slider-thumb]:shadow-md"
      disabled={max === 0}
      min="0"
      max={max}
      step="any"
      type="range"
      value={value}
      onChange={(event) => onChange(Math.round(Number(event.target.value)))}
    />
  );
}

function clampAmount(value: string | undefined, fallback: number, ceiling: number) {
  const parsed = value === undefined || value === "" ? fallback : Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.min(Math.max(roundCurrency(parsed), 0), ceiling);
}

function roundCurrency(value: number) {
  return Math.round(value * 100) / 100;
}
