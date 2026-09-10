import axios from "axios";
import type { DashboardData } from "@/types/dashboard";
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

export const AUTH_TOKEN_KEY = "financeTrackerToken";
export const AUTH_USER_KEY = "financeTrackerAuth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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
  localStorage.setItem(AUTH_TOKEN_KEY, response.data.accessToken);
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(response.data));
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
