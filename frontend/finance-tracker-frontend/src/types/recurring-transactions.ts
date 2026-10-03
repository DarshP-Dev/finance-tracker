import type { TransactionCategory, TransactionType } from "@/types/transactions";

export type RecurringFrequency = "WEEKLY" | "BIWEEKLY" | "MONTHLY" | "YEARLY";

export type RecurringTransaction = {
  id: number;
  amount: number;
  category: TransactionCategory;
  type: TransactionType;
  description: string;
  merchant: string | null;
  frequency: RecurringFrequency;
  startDate: string;
  nextOccurrence: string;
  endDate: string | null;
  active: boolean;
  createdAt: string;
};

export type RecurringTransactionPayload = {
  amount: number;
  category: TransactionCategory;
  type: TransactionType;
  description: string;
  merchant?: string;
  frequency: RecurringFrequency;
  startDate: string;
  endDate: string | null;
};

export type UpcomingRecurringTransaction = {
  recurringTransactionId: number;
  description: string;
  merchant: string | null;
  amount: number;
  category: TransactionCategory;
  type: TransactionType;
  frequency: RecurringFrequency;
  scheduledDate: string;
};

export type RecurringForecast = {
  from: string;
  to: string;
  expectedIncome: number;
  expectedExpenses: number;
  netCashFlow: number;
  incomeOccurrenceCount: number;
  expenseOccurrenceCount: number;
};

export type RecurringForecastRange = { from?: string; to?: string };

export const recurringFrequencyLabels: Record<RecurringFrequency, string> = {
  WEEKLY: "Weekly",
  BIWEEKLY: "Biweekly",
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
};
