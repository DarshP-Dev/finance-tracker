import axios from "axios";
import type { Notification, NotificationPage } from "@/types/notifications";
import type { Budget, BudgetPayload } from "@/types/budgets";
import type { AnalyticsData, AnalyticsPeriod } from "@/types/analytics";
import type { DashboardData } from "@/types/dashboard";
import type { FinancialInsightsResponse, FinancialInsightsSummaryResponse } from "@/types/financial-insights";
import type { Investment, InvestmentHolding, InvestmentPayload, Portfolio, PortfolioQuotePolicy, PortfolioRefreshResponse } from "@/types/investments";
import type { RecurringForecast, RecurringForecastRange, RecurringTransaction, RecurringTransactionPayload, UpcomingRecurringTransaction } from "@/types/recurring-transactions";
import type { Transaction, TransactionFilters, TransactionPayload } from "@/types/transactions";

export type AuthMode = "login" | "register";

export type { AuthResponse, AuthPayload } from "./auth-session";
import { createSessionClient, type AuthResponse } from "./auth-session";

export type UpdateProfilePayload = {
  username: string;
  email: string;
  currentPassword: string;
};

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ??
  (process.env.NODE_ENV === "development" ? "http://localhost:8080" : "");
const session = createSessionClient(API_BASE_URL);
export const { api, authenticate, registerAccount, restoreSession, logoutSession,
  storeAuth, getStoredAuth, clearStoredAuth } = session;
export const subscribeAuth = session.subscribe;

const notifiedResponses = new WeakSet<object>();
api.interceptors.response.use(response => {
  if (typeof window !== "undefined" && ["post", "put", "patch", "delete"].includes(response.config.method ?? "")
      && /^\/api\/(transactions|budgets|recurring-transactions)(\/|$)/.test(response.config.url ?? "")
      && !notifiedResponses.has(response)) {
    notifiedResponses.add(response);
    window.dispatchEvent(new Event("finance-tracker:notifications-changed"));
  }
  return response;
}, error => {
  if (axios.isAxiosError(error) && !error.response && error.code === "ERR_NETWORK") {
    error.message = "Cannot connect to the server. Make sure the backend is running, then try again.";
  }
  return Promise.reject(error);
});

export async function fetchNotifications(page = 0, signal?: AbortSignal) {
  return (await api.get<NotificationPage>("/api/notifications", { params: { page, size: 20 }, signal })).data;
}
export async function fetchNotificationUnreadCount(signal?: AbortSignal) {
  return (await api.get<{ count: number }>("/api/notifications/unread-count", { signal })).data.count;
}
export async function markNotificationRead(id: number) {
  return (await api.patch<Notification>(`/api/notifications/${id}/read`)).data;
}
export async function markAllNotificationsRead() { await api.patch("/api/notifications/read-all"); }
export async function deleteNotification(id: number) { await api.delete(`/api/notifications/${id}`); }

export async function fetchTransactions(filters: TransactionFilters) {
  const params = Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== ""),
  );
  const response = await api.get<Transaction[]>("/api/transactions", { params });
  return response.data;
}

export async function createTransaction(payload: TransactionPayload) {
  const response = await api.post<Transaction>("/api/transactions", payload);
  return response.data;
}

export async function updateTransaction(id: number, payload: TransactionPayload) {
  const response = await api.put<Transaction>(`/api/transactions/${id}`, payload);
  return response.data;
}

export async function deleteTransaction(id: number) {
  await api.delete(`/api/transactions/${id}`);
}

export async function getRecurringTransactions() {
  const response = await api.get<RecurringTransaction[]>("/api/recurring-transactions");
  return response.data;
}

export async function getUpcomingRecurringTransactions(range?: RecurringForecastRange) {
  const response = await api.get<UpcomingRecurringTransaction[]>("/api/recurring-transactions/upcoming", { params: range });
  return response.data;
}

export async function getRecurringForecast(range?: RecurringForecastRange) {
  const response = await api.get<RecurringForecast>("/api/recurring-transactions/forecast", { params: range });
  return response.data;
}

export async function getRecurringTransaction(id: number) {
  const response = await api.get<RecurringTransaction>(`/api/recurring-transactions/${id}`);
  return response.data;
}

export async function createRecurringTransaction(payload: RecurringTransactionPayload) {
  const response = await api.post<RecurringTransaction>("/api/recurring-transactions", payload);
  return response.data;
}

export async function updateRecurringTransaction(id: number, payload: RecurringTransactionPayload) {
  const response = await api.put<RecurringTransaction>(`/api/recurring-transactions/${id}`, payload);
  return response.data;
}

export async function deleteRecurringTransaction(id: number) {
  await api.delete(`/api/recurring-transactions/${id}`);
}

export async function pauseRecurringTransaction(id: number) {
  const response = await api.patch<RecurringTransaction>(`/api/recurring-transactions/${id}/pause`);
  return response.data;
}

export async function resumeRecurringTransaction(id: number) {
  const response = await api.patch<RecurringTransaction>(`/api/recurring-transactions/${id}/resume`);
  return response.data;
}

export async function generateRecurringTransaction(id: number) {
  const response = await api.post<Transaction>(`/api/recurring-transactions/${id}/generate`);
  return response.data;
}

export async function fetchDashboard(filters?: { startDate?: string; endDate?: string }) {
  const response = await api.get<DashboardData>("/api/dashboard", { params: filters });
  return response.data;
}

export async function fetchAnalytics(period: AnalyticsPeriod, range?: { startDate: string; endDate: string }) {
  const response = await api.get<AnalyticsData>("/api/analytics", { params: { period, ...range } });
  return response.data;
}

export type FinancialInsightsQuery = { period: AnalyticsPeriod; startDate?: string; endDate?: string };

export async function getFinancialInsights(signal?: AbortSignal, params?: FinancialInsightsQuery) {
  const response = await api.get<FinancialInsightsResponse>("/api/financial-insights", { signal, params });
  return response.data;
}

export async function getFinancialInsightsSummary(signal?: AbortSignal, params?: FinancialInsightsQuery) {
  const response = await api.get<FinancialInsightsSummaryResponse>("/api/financial-insights/summary", { signal, params, timeout: 35000 });
  return response.data;
}

export async function fetchBudgets(month: string) {
  const response = await api.get<Budget[]>("/api/budgets", { params: { month } });
  return response.data;
}

export async function createBudget(payload: BudgetPayload) {
  const response = await api.post<Budget>("/api/budgets", payload);
  return response.data;
}

export async function updateBudget(id: number, payload: BudgetPayload) {
  const response = await api.put<Budget>(`/api/budgets/${id}`, payload);
  return response.data;
}

export async function deleteBudget(id: number) {
  await api.delete(`/api/budgets/${id}`);
}

export async function fetchInvestments() {
  const response = await api.get<Investment[]>("/api/investments");
  return response.data;
}

export async function fetchInvestmentHoldings() {
  const response = await api.get<InvestmentHolding[]>("/api/investments/holdings");
  return response.data;
}

export async function fetchInvestmentPortfolio(signal?: AbortSignal, quotePolicy: PortfolioQuotePolicy = "ON_DEMAND") {
  const response = await api.get<Portfolio>("/api/investments/portfolio", { signal, params: { quotePolicy } });
  return response.data;
}

export async function refreshInvestmentPortfolio() {
  const response = await api.post<PortfolioRefreshResponse>("/api/investments/portfolio/refresh");
  return response.data;
}

export async function createInvestment(payload: InvestmentPayload) {
  const response = await api.post<Investment>("/api/investments", payload);
  return response.data;
}

export async function updateInvestment(id: number, payload: InvestmentPayload) {
  const response = await api.put<Investment>(`/api/investments/${id}`, payload);
  return response.data;
}

export async function deleteInvestment(id: number) {
  await api.delete(`/api/investments/${id}`);
}

export async function verifyCurrentPassword(password: string) {
  await api.post("/api/account/verify-password", { password });
}

export async function updateProfile(payload: UpdateProfilePayload) {
  const response = await api.patch<AuthResponse>("/api/account/profile", payload);
  storeAuth(response.data);
  return response.data;
}

export async function changePassword(payload: ChangePasswordPayload) {
  await api.put("/api/account/password", payload);
  clearStoredAuth("Password changed. Please sign in again.", true);
}
