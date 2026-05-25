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
    <section className="rounded-lg border border-[#d9e1ec] bg-white p-5 shadow-sm">
      <div className="mb-4">
        <h2 className="text-base font-semibold text-[#172033]">{title}</h2>
        <p className="mt-1 text-sm text-[#667085]">{description}</p>
      </div>
      {isEmpty ? (
        <div className="flex h-[280px] items-center justify-center rounded-md border border-dashed border-[#cfd8e6] bg-[#f8fafc] text-sm text-[#667085]">
          No data yet
        </div>
      ) : (
        children
      )}
    </section>
  );
}
