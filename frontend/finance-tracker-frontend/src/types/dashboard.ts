import type { Transaction, TransactionCategory } from "@/types/transactions";
import type { Portfolio } from "@/types/investments";

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

export type CashFlowTrend = {
  date: string;
  income: number;
  expenses: number;
};

export type IncomeVsExpenses = {
  income: number;
  expenses: number;
};

export type DashboardData = {
  portfolio?: Portfolio;
  summary: DashboardSummary;
  categorySpending: CategorySpending[];
  monthlySpending: MonthlySpending[];
  cashFlowTrend: CashFlowTrend[];
  incomeVsExpenses: IncomeVsExpenses;
  recentTransactions: Transaction[];
};
