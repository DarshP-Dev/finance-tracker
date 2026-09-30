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

export const recurringFrequencyLabels: Record<RecurringFrequency, string> = {
  WEEKLY: "Weekly",
  BIWEEKLY: "Biweekly",
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
};
