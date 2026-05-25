"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { ChartCard } from "@/components/dashboard/ChartCard";
import { formatCategory, formatCurrency } from "@/components/transactions/formatters";
import type { CategorySpending } from "@/types/dashboard";

type SpendingPieChartProps = {
  data: CategorySpending[];
};

const colors = ["#195b4d", "#2f80ed", "#f79009", "#b42318", "#7c3aed", "#0e9384", "#667085", "#d4a72c"];

export function SpendingPieChart({ data }: SpendingPieChartProps) {
  const chartData = data.map((item) => ({
    name: formatCategory(item.category),
    value: item.total,
  }));

  return (
    <ChartCard title="Spending by Category" description="Expense concentration across categories" isEmpty={chartData.length === 0}>
      <div className="grid gap-4 lg:grid-cols-[1fr_180px]">
        <div className="h-[280px]">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={chartData} dataKey="value" nameKey="name" innerRadius={58} outerRadius={98} paddingAngle={3}>
                {chartData.map((entry, index) => (
                  <Cell key={entry.name} fill={colors[index % colors.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <div className="flex flex-col justify-center gap-2">
          {chartData.slice(0, 6).map((item, index) => (
            <div key={item.name} className="flex items-center justify-between gap-3 text-sm">
              <div className="flex min-w-0 items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: colors[index % colors.length] }} />
                <span className="truncate text-[#344054]">{item.name}</span>
              </div>
              <span className="font-semibold text-[#172033]">{formatCurrency(item.value)}</span>
            </div>
          ))}
        </div>
      </div>
    </ChartCard>
  );
}
