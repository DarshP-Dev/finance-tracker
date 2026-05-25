"use client";

import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
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
    <ChartCard title="Monthly Spending Trend" description="Expense movement over time" isEmpty={chartData.length === 0}>
      <div className="h-[280px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="#e7edf5" vertical={false} />
            <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fill: "#667085", fontSize: 12 }} />
            <YAxis tickLine={false} axisLine={false} tick={{ fill: "#667085", fontSize: 12 }} tickFormatter={(value) => `$${value}`} />
            <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            <Line type="monotone" dataKey="spending" stroke="#195b4d" strokeWidth={3} dot={{ r: 3 }} activeDot={{ r: 5 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}
