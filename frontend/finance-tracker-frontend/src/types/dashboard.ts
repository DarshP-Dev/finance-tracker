import type { Transaction, TransactionCategory } from "@/types/transactions";

export type DashboardSummary = {
  totalIncome: number;
  totalExpenses: number;
  totalSavings: number;
  investmentValue: number;
};

export type CategorySpending = {
  category: TransactionCategory;
  total: number;
};

export type MonthlySpending = {
  month: string;
  total: number;
};

export type IncomeVsExpenses = {
  income: number;
  expenses: number;
};

export type DashboardData = {
  summary: DashboardSummary;
  categorySpending: CategorySpending[];
  monthlySpending: MonthlySpending[];
  incomeVsExpenses: IncomeVsExpenses;
  recentTransactions: Transaction[];
};
