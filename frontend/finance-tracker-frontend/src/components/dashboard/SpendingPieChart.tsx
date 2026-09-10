"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { ChartCard } from "@/components/dashboard/ChartCard";
import { formatCategory, formatCurrency } from "@/components/transactions/formatters";
import type { CategorySpending } from "@/types/dashboard";

type SpendingPieChartProps = {
  data: CategorySpending[];
};

const colors = ["#ff5a1f", "#ff986f", "#ffd7c8", "#15151b", "#f2c3aa", "#8f3f17", "#f79009", "#77717d"];

export function SpendingPieChart({ data }: SpendingPieChartProps) {
  const chartData = data.map((item) => ({
    name: formatCategory(item.category),
    value: item.total,
  }));

  return (
    <ChartCard title="Summary" description="Expense concentration" isEmpty={chartData.length === 0}>
      <div className="grid gap-4">
        <div className="h-[170px]">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={chartData} dataKey="value" nameKey="name" innerRadius={52} outerRadius={72} paddingAngle={4}>
                {chartData.map((entry, index) => (
                  <Cell key={entry.name} fill={colors[index % colors.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <div className="grid gap-2">
          {chartData.slice(0, 3).map((item, index) => (
            <div key={item.name} className="flex items-center justify-between gap-3 text-xs">
              <div className="flex min-w-0 items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: colors[index % colors.length] }} />
                <span className="truncate text-[#46404b]">{item.name}</span>
              </div>
              <span className="font-semibold text-[#151515]">{formatCurrency(item.value)}</span>
            </div>
          ))}
        </div>
      </div>
    </ChartCard>
  );
}
