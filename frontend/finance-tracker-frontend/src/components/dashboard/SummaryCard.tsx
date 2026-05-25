"use client";

type SummaryCardProps = {
  label: string;
  value: string;
  helper: string;
  tone: "income" | "expense" | "savings" | "investment";
};

const toneStyles = {
  income: "border-[#b7e4c7] bg-[#f0fdf4] text-[#027a48]",
  expense: "border-[#fed7aa] bg-[#fff7ed] text-[#c2410c]",
  savings: "border-[#bfdbfe] bg-[#eff6ff] text-[#1d4ed8]",
  investment: "border-[#ddd6fe] bg-[#f5f3ff] text-[#6d28d9]",
};

export function SummaryCard({ label, value, helper, tone }: SummaryCardProps) {
  return (
    <div className="rounded-lg border border-[#d9e1ec] bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className={`inline-flex rounded-md border px-2.5 py-1 text-xs font-semibold ${toneStyles[tone]}`}>
        {label}
      </div>
      <div className="mt-4 text-2xl font-semibold text-[#172033]">{value}</div>
      <div className="mt-1 text-sm text-[#667085]">{helper}</div>
    </div>
  );
}
