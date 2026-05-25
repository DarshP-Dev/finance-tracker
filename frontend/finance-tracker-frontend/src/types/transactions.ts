export type TransactionType = "INCOME" | "EXPENSE";

export type TransactionCategory =
  | "FOOD"
  | "RENT"
  | "HOUSING"
  | "UTILITIES"
  | "GROCERIES"
  | "DINING"
  | "TRANSPORTATION"
  | "HEALTHCARE"
  | "HEALTH"
  | "INSURANCE"
  | "DEBT_PAYMENT"
  | "ENTERTAINMENT"
  | "SHOPPING"
  | "EDUCATION"
  | "TRAVEL"
  | "SALARY"
  | "INVESTMENT_INCOME"
  | "TRANSFER"
  | "OTHER";

export type Transaction = {
  id: number;
  amount: number;
  category: TransactionCategory;
  type: TransactionType;
  description: string | null;
  date: string;
  merchant: string | null;
  createdAt: string;
};

export type TransactionPayload = {
  amount: number;
  category: TransactionCategory;
  type: TransactionType;
  description?: string;
  date: string;
  merchant?: string;
};

export type TransactionFilters = {
  date?: string;
  startDate?: string;
  endDate?: string;
  category?: TransactionCategory | "";
  minAmount?: string;
  maxAmount?: string;
};

export const transactionCategories: TransactionCategory[] = [
  "FOOD",
  "RENT",
  "TRANSPORTATION",
  "ENTERTAINMENT",
  "UTILITIES",
  "SHOPPING",
  "HEALTH",
  "GROCERIES",
  "DINING",
  "HOUSING",
  "HEALTHCARE",
  "INSURANCE",
  "DEBT_PAYMENT",
  "EDUCATION",
  "TRAVEL",
  "SALARY",
  "INVESTMENT_INCOME",
  "TRANSFER",
  "OTHER",
];
