import axios, { type InternalAxiosRequestConfig } from "axios";

export type AuthResponse = {
  accessToken: string; tokenType: string; userId: number; username: string; email: string; expiresIn: number;
};
export type AuthPayload = { username?: string; email: string; password: string };
type RetriableConfig = InternalAxiosRequestConfig & { _authRetried?: boolean; _sessionEpoch?: number };
const SESSION_EVENT = "finance-tracker:session-changed";
const LOGOUT_MARKER = "finance-tracker:logout";

// One instance per browser module. Exported factory also permits isolated adapter tests.
export function createSessionClient(baseURL: string) {
  const options = { baseURL, headers: { "Content-Type": "application/json" } };
  const api = axios.create(options);
  const authApi = axios.create({ ...options, withCredentials: true, timeout: 10000 });
  let current: AuthResponse | null = null;
  let epoch = 0;
  let refreshPromise: Promise<AuthResponse> | null = null;
  const listeners = new Set<(auth: AuthResponse | null, message: string) => void>();

  function legacyCleanup() {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.removeItem("financeTrackerToken");
      window.localStorage.removeItem("financeTrackerAuth");
    } catch { /* Storage may be disabled; authentication remains memory-only. */ }
  }
  function publish(message = "") {
    listeners.forEach(listener => listener(current, message));
    if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_EVENT));
  }
  function storeAuth(auth: AuthResponse) { current = auth; legacyCleanup(); publish(); }
  function clearStoredAuth(message = "", broadcast = false) {
    epoch++; current = null; legacyCleanup(); publish(message);
    if (broadcast && typeof window !== "undefined") {
      try { window.localStorage.setItem(LOGOUT_MARKER, `${Date.now()}:${Math.random()}`); } catch { /* Optional tab coordination. */ }
      channel?.postMessage("logout");
    }
  }
  // Never send tokens over storage or the channel. Both are only logout signals.
  const channel = typeof window !== "undefined" && typeof BroadcastChannel !== "undefined"
    ? new BroadcastChannel("finance-tracker:session") : null;
  if (channel) channel.onmessage = () => clearStoredAuth("You signed out in another tab.");
  if (typeof window !== "undefined") {
    legacyCleanup();
    window.addEventListener("storage", event => {
      if (event.key === LOGOUT_MARKER) clearStoredAuth("You signed out in another tab.");
    });
  }

  async function serialized<T>(operation: () => Promise<T>): Promise<T> {
    if (typeof navigator !== "undefined" && navigator.locks) {
      return navigator.locks.request("finance-tracker:refresh-session", { signal: AbortSignal.timeout(20000) }, operation);
    }
    return operation();
  }
  async function authPost<T>(path: string, body?: unknown): Promise<T> {
    // Obtain a server-provided masked CSRF token, never read the HttpOnly cookie.
    const csrf = await authApi.get<{ token: string; headerName: string }>("/api/auth/csrf");
    return (await authApi.post<T>(path, body, { headers: { [csrf.data.headerName]: csrf.data.token } })).data;
  }
  function refresh(quiet = false): Promise<AuthResponse> {
    if (refreshPromise) return refreshPromise;
    const started = epoch;
    refreshPromise = serialized(async () => {
      if (epoch !== started) throw new Error("Session changed.");
      const auth = await authPost<AuthResponse>("/api/auth/refresh");
      if (epoch !== started) throw new Error("Session changed.");
      storeAuth(auth);
      return auth;
    }).catch(error => {
      if (epoch === started) {
        const invalid = axios.isAxiosError(error) && error.response?.status === 401;
        clearStoredAuth(invalid ? (quiet ? "" : "Your session expired. Please sign in again.")
          : "Unable to restore your session. Check your connection and sign in again.");
      }
      throw error;
    }).finally(() => { refreshPromise = null; });
    return refreshPromise;
  }
  async function restoreSession() {
    if (current) return current;
    try { return await refresh(true); } catch { return null; }
  }
  async function authenticate(mode: "login" | "register", payload: AuthPayload) {
    clearStoredAuth("", true);
    const started = epoch;
    return serialized(async () => {
      if (epoch !== started) throw new Error("Session changed. Please try signing in again.");
      const auth = await authPost<AuthResponse>(`/api/auth/${mode}`, payload);
      if (epoch !== started) {
        // A logout during login must also revoke the just-issued cookie.
        await authPost("/api/auth/logout");
        throw new Error("Session changed. Please sign in again.");
      }
      storeAuth(auth);
      return auth;
    });
  }
  async function registerAccount(payload: AuthPayload) {
    return serialized(() => authPost<AuthResponse>("/api/auth/register", payload));
  }
  async function logoutSession() {
    clearStoredAuth("", true);
    try { await serialized(() => authPost("/api/auth/logout")); }
    catch {
      publish("Signed out here, but server logout could not be confirmed. Reconnect and retry logout before using a shared device.");
    }
  }

  api.interceptors.request.use(config => {
    const request = config as RetriableConfig;
    // A queued retry must never acquire a different user's token after logout/login.
    if (request._authRetried && request._sessionEpoch !== epoch) throw new Error("Session changed.");
    request._sessionEpoch = epoch;
    if (current) config.headers.Authorization = `Bearer ${current.accessToken}`;
    else config.headers.delete("Authorization");
    return config;
  });
  api.interceptors.response.use(response => {
    if ((response.config as RetriableConfig)._sessionEpoch !== epoch) throw new Error("Session changed.");
    return response;
  }, async error => {
    const request = error.config as RetriableConfig | undefined;
    // Only this security-boundary code guarantees the handler never ran.
    const rejectedBeforeHandler = error.response?.status === 401
      && error.response?.data?.code === "AUTHENTICATION_REQUIRED";
    if (!request || !rejectedBeforeHandler || request.url?.startsWith("/api/auth/")) throw error;
    if (request._sessionEpoch !== epoch) throw error;
    if (request._authRetried) {
      clearStoredAuth("Your session expired. Please sign in again.");
      throw error;
    }
    request._authRetried = true;
    const usedToken = request.headers.Authorization;
    if (!current || usedToken === `Bearer ${current.accessToken}`) await refresh();
    if (request._sessionEpoch !== epoch) throw error;
    return api.request(request);
  });
  return { api, authApi, authenticate, registerAccount, restoreSession, logoutSession, storeAuth, clearStoredAuth,
    getStoredAuth: () => current,
    subscribe: (listener: (auth: AuthResponse | null, message: string) => void) => {
      listeners.add(listener); return () => { listeners.delete(listener); };
    },
  };
}
