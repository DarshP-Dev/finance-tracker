"use client";

import type { ReactNode } from "react";

type SummaryCardProps = {
  label: string;
  value: string;
  helper: string;
  tone: "income" | "expense" | "savings" | "investment";
  icon: ReactNode;
};

const toneStyles = {
  income: "bg-[#fff3ed] text-[#ff5a1f]",
  expense: "bg-[#fff3ed] text-[#ff5a1f]",
  savings: "bg-[#edf8f1] text-[#22935f]",
  investment: "bg-[#f4f1ff] text-[#7557d8]",
};

export function SummaryCard({ label, value, helper, tone, icon }: SummaryCardProps) {
  return (
    <div className="overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="p-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-4">
            <div className={`flex h-9 w-9 items-center justify-center rounded-xl border border-[#eee8e2] text-sm font-black ${toneStyles[tone]}`}>
              {icon}
            </div>
            <div className="text-base font-semibold text-[#151515]">{label}</div>
          </div>
        </div>
        <div className="mt-4 text-2xl font-semibold leading-none tabular-nums text-[#151515]">{value}</div>
        <div className="mt-2 text-xs text-[#77717d]">{helper}</div>
      </div>
    </div>
  );
}
