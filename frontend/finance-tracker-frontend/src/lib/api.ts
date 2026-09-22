import axios from "axios";
import type { Budget, BudgetPayload } from "@/types/budgets";
import type { DashboardData } from "@/types/dashboard";
import type { Investment, InvestmentHolding, InvestmentPayload } from "@/types/investments";
import type { Transaction, TransactionFilters, TransactionPayload } from "@/types/transactions";

export type AuthMode = "login" | "register";

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  userId: number;
  username: string;
  email: string;
};

export type AuthPayload = {
  username?: string;
  email: string;
  password: string;
};

export type UpdateProfilePayload = {
  username: string;
  email: string;
  currentPassword: string;
};

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

export const AUTH_TOKEN_KEY = "financeTrackerToken";
export const AUTH_USER_KEY = "financeTrackerAuth";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  (process.env.NODE_ENV === "development" ? "http://localhost:8080" : "");

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  if (typeof window === "undefined") {
    return config;
  }

  const token = localStorage.getItem(AUTH_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && !error.response && error.code === "ERR_NETWORK") {
      error.message = "Cannot connect to the server. Make sure the backend is running, then try again.";
    }

    if (typeof window !== "undefined" && [401, 403].includes(error.response?.status)) {
      localStorage.removeItem(AUTH_TOKEN_KEY);
      localStorage.removeItem(AUTH_USER_KEY);
      window.dispatchEvent(new Event("finance-tracker:unauthorized"));
    }

    return Promise.reject(error);
  },
);

export async function authenticate(mode: AuthMode, payload: AuthPayload) {
  const endpoint = mode === "login" ? "/api/auth/login" : "/api/auth/register";
  const response = await api.post<AuthResponse>(endpoint, payload);
  storeAuth(response.data);
  return response.data;
}

export async function registerAccount(payload: AuthPayload) {
  const response = await api.post<AuthResponse>("/api/auth/register", payload);
  return response.data;
}

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

export async function fetchDashboard(filters?: { startDate?: string; endDate?: string }) {
  const response = await api.get<DashboardData>("/api/dashboard", { params: filters });
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
}

export function storeAuth(auth: AuthResponse) {
  localStorage.setItem(AUTH_TOKEN_KEY, auth.accessToken);
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(auth));
}

export function getStoredAuth() {
  if (typeof window === "undefined") {
    return null;
  }

  const rawAuth = localStorage.getItem(AUTH_USER_KEY);
  const token = localStorage.getItem(AUTH_TOKEN_KEY);
  if (!rawAuth || !token) {
    return null;
  }

  try {
    return JSON.parse(rawAuth) as AuthResponse;
  } catch {
    localStorage.removeItem(AUTH_USER_KEY);
    return null;
  }
}

export function clearStoredAuth() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
}
