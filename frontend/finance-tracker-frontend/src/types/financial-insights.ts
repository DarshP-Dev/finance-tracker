import type { TransactionCategory } from "@/types/transactions";

export type FinancialInsightType =
  | "SPENDING"
  | "BUDGET"
  | "SAVINGS"
  | "INCOME"
  | "RECURRING"
  | "FORECAST"
  | "INVESTMENT";

export type FinancialInsightSeverity = "INFO" | "POSITIVE" | "WARNING";

export type FinancialInsight = {
  key: string;
  type: FinancialInsightType;
  severity: FinancialInsightSeverity;
  title: string;
  message: string;
  metricValue: number | null;
  comparisonValue: number | null;
  category: TransactionCategory | null;
  from: string | null;
  to: string | null;
  generatedAt: string;
};

export type FinancialInsightsResponse = {
  generatedAt: string;
  insights: FinancialInsight[];
};
