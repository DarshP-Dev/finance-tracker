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
    <div className="rounded-2xl border border-[#e4e0e7] bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <div className={`flex h-10 w-10 items-center justify-center rounded-xl text-sm font-black ${toneStyles[tone]}`}>
          {icon}
        </div>
        <button className="text-lg leading-none text-[#19171c]">...</button>
      </div>
      <div className="mt-3 text-sm font-semibold text-[#151515]">{label}</div>
      <div className="mt-4 text-[26px] font-semibold leading-none text-[#151515]">{value}</div>
      <div className="mt-3 text-xs text-[#77717d]">{helper}</div>
    </div>
  );
}
