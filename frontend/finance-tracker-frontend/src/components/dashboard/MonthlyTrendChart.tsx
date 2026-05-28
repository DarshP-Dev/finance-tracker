"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartCard } from "@/components/dashboard/ChartCard";
import { formatCurrency } from "@/components/transactions/formatters";
import type { MonthlySpending } from "@/types/dashboard";

type MonthlyTrendChartProps = {
  data: MonthlySpending[];
};

export function MonthlyTrendChart({ data }: MonthlyTrendChartProps) {
  const chartData = data.map((item) => ({
    month: new Intl.DateTimeFormat("en-US", { month: "short", year: "2-digit" }).format(new Date(`${item.month}T00:00:00`)),
    spending: item.total,
  }));

  return (
    <ChartCard title="Analytics" description="Expense movement over time" isEmpty={chartData.length === 0}>
      <div className="h-[280px]">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="spendingGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#ff5a1f" stopOpacity={0.34} />
                <stop offset="95%" stopColor="#ff5a1f" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#eee8e2" strokeDasharray="2 4" vertical={false} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fill: "#77717d", fontSize: 12 }} />
            <YAxis hide tickFormatter={(value) => `$${value}`} />
            <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            <Area type="monotone" dataKey="spending" stroke="#ff5a1f" strokeWidth={3} fill="url(#spendingGradient)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}
