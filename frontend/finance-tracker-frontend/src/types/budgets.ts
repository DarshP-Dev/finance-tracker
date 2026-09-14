import type { TransactionCategory } from "@/types/transactions";

export type BudgetCategory = Extract<
  TransactionCategory,
  | "HOUSING"
  | "UTILITIES"
  | "GROCERIES"
  | "DINING"
  | "TRANSPORTATION"
  | "HEALTHCARE"
  | "INSURANCE"
  | "DEBT_PAYMENT"
  | "ENTERTAINMENT"
  | "SHOPPING"
  | "EDUCATION"
  | "TRAVEL"
  | "OTHER"
>;

export type Budget = {
  id: number;
  category: BudgetCategory;
  monthlyLimit: number;
  month: string;
  amountSpent: number;
  remaining: number;
  percentUsed: number;
  overBudget: boolean;
  createdAt: string;
};

export type BudgetPayload = {
  category: BudgetCategory;
  monthlyLimit: number;
  month: string;
};

export const budgetCategories: BudgetCategory[] = [
  "HOUSING",
  "UTILITIES",
  "GROCERIES",
  "DINING",
  "TRANSPORTATION",
  "HEALTHCARE",
  "INSURANCE",
  "DEBT_PAYMENT",
  "ENTERTAINMENT",
  "SHOPPING",
  "EDUCATION",
  "TRAVEL",
  "OTHER",
];
