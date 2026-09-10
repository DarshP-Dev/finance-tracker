"use client";

import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartCard } from "@/components/dashboard/ChartCard";
import { formatCurrency } from "@/components/transactions/formatters";
import type { CashFlowTrend } from "@/types/dashboard";

type MonthlyTrendChartProps = {
  data: CashFlowTrend[];
};

export function MonthlyTrendChart({ data }: MonthlyTrendChartProps) {
  const chartData = data.map((item) => ({
    date: new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" }).format(new Date(`${item.date}T00:00:00`)),
    income: item.income,
    expenses: item.expenses,
  }));

  return (
    <ChartCard title="Analytics" description="Income and expense movement over time" isEmpty={chartData.length === 0}>
      <div className="h-[240px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="#eee8e2" strokeDasharray="2 4" vertical={false} />
            <XAxis
              dataKey="date"
              interval="preserveStartEnd"
              minTickGap={32}
              tickLine={false}
              axisLine={false}
              tick={{ fill: "#77717d", fontSize: 12 }}
            />
            <YAxis hide tickFormatter={(value) => `$${value}`} />
            <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            <Line type="monotone" dataKey="income" name="Income" stroke="#16a34a" strokeWidth={3} dot={{ r: 4, fill: "#16a34a" }} activeDot={{ r: 6 }} />
            <Line type="monotone" dataKey="expenses" name="Expenses" stroke="#dc2626" strokeWidth={3} dot={{ r: 4, fill: "#dc2626" }} activeDot={{ r: 6 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}
