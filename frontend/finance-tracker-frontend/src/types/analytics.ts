export type AnalyticsPeriod = "THIS_MONTH" | "LAST_MONTH" | "LAST_3_MONTHS" | "LAST_6_MONTHS" | "THIS_YEAR" | "CUSTOM";

export type MonthlyAmount = { month: string; amount: number };

export type AnalyticsData = {
  period: AnalyticsPeriod;
  startDate: string;
  endDate: string;
  overview: { totalIncome: number; totalExpenses: number; netCashFlow: number; totalInvested: number };
  spendingByCategory: { category: string; amount: number; percentage: number }[];
  incomeVsExpenses: { month: string; income: number; expenses: number }[];
  spendingTrend: { startMonth: string; endMonth: string; months: MonthlyAmount[] };
  budgets: {
    month: string;
    totalBudgeted: number;
    budgetedSpending: number;
    remainingBudget: number;
    overBudgetCategories: number;
    categories: {
      category: string;
      monthlyBudget: number;
      actualSpending: number;
      remaining: number;
      percentUsed: number;
      overBudget: boolean;
    }[];
  };
  investments: {
    totalInvestedAllTime: number;
    uniqueHoldings: number;
    contributions: MonthlyAmount[];
    allocation: { ticker: string; amountInvested: number; percentage: number }[];
  };
};
