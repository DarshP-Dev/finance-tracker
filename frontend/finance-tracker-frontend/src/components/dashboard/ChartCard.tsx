"use client";

import type { ReactNode } from "react";

type ChartCardProps = {
  title: string;
  description: string;
  isEmpty: boolean;
  children: ReactNode;
};

export function ChartCard({ title, description, isEmpty, children }: ChartCardProps) {
  return (
    <section className="min-w-0 rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-sm">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-[#151515]">{title}</h2>
          <p className="mt-1 text-sm text-[#77717d]">{description}</p>
        </div>
      </div>
      {isEmpty ? (
        <div className="flex h-[240px] items-center justify-center rounded-xl border border-dashed border-[#e4e0e7] bg-[#fffaf7] text-sm text-[#77717d]">
          No data yet
        </div>
      ) : (
        children
      )}
    </section>
  );
}
