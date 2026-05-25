"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartCard } from "@/components/dashboard/ChartCard";
import { formatCurrency } from "@/components/transactions/formatters";
import type { IncomeVsExpenses } from "@/types/dashboard";

type IncomeExpenseChartProps = {
  data: IncomeVsExpenses;
};

export function IncomeExpenseChart({ data }: IncomeExpenseChartProps) {
  const chartData = [
    { name: "Income", amount: data.income, fill: "#027a48" },
    { name: "Expenses", amount: data.expenses, fill: "#c2410c" },
  ];

  return (
    <ChartCard title="Income vs Expenses" description="Total cash in compared with cash out" isEmpty={data.income === 0 && data.expenses === 0}>
      <div className="h-[280px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="#e7edf5" vertical={false} />
            <XAxis dataKey="name" tickLine={false} axisLine={false} tick={{ fill: "#667085", fontSize: 12 }} />
            <YAxis tickLine={false} axisLine={false} tick={{ fill: "#667085", fontSize: 12 }} tickFormatter={(value) => `$${value}`} />
            <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            <Bar dataKey="amount" radius={[6, 6, 0, 0]} fill="#195b4d" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}
