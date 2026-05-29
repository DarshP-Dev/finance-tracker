"use client";

import { FormEvent, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { TransactionDashboard } from "@/components/transactions/TransactionDashboard";
import {
  authenticate,
  clearStoredAuth,
  getStoredAuth,
  registerAccount,
  type AuthMode,
  type AuthResponse,
} from "@/lib/api";

export default function Home() {
  const [mode, setMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState("");
  const [auth, setAuth] = useState<AuthResponse | null>(null);
  const [hasHydrated, setHasHydrated] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setAuth(getStoredAuth());
      setHasHydrated(true);
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("");
    setIsSubmitting(true);

    try {
      if (mode === "register") {
        await registerAccount({ username, email, password });
        clearStoredAuth();
        window.alert("Account created. Please log in to continue.");
        setMode("login");
        setUsername("");
        setPassword("");
        setStatus("Account created. Please log in.");
        return;
      }

      const response = await authenticate(mode, { email, password });
      setAuth(response);
      setStatus("");
    } catch (error) {
      setStatus(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!hasHydrated) {
    return <main className="min-h-screen bg-[#f5f7fb]" />;
  }

  if (auth) {
    return <TransactionDashboard auth={auth} onSignOut={() => setAuth(null)} />;
  }

  return (
    <main className="min-h-screen bg-[#f5f7fb] text-[#172033]">
      <section className="mx-auto grid min-h-screen w-full max-w-6xl grid-cols-1 lg:grid-cols-[1fr_420px]">
        <div className="flex flex-col justify-between px-6 py-8 sm:px-10 lg:py-12">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-[#195b4d]">
              Finance Tracker
            </p>
            <h1 className="mt-12 max-w-2xl text-4xl font-semibold leading-tight sm:text-5xl">
              Track cash flow with secure, user-scoped transactions.
            </h1>
            <p className="mt-5 max-w-xl text-base leading-7 text-[#5e6b80]">
              Sign in to manage income, expenses, filters, and account-protected records from one workspace.
            </p>
          </div>

          <div className="mt-12 grid max-w-2xl grid-cols-1 gap-3 sm:grid-cols-3">
            <Metric label="API" value="REST" accent="border-[#195b4d]" />
            <Metric label="Auth" value="JWT" accent="border-[#d4a72c]" />
            <Metric label="Data" value="PostgreSQL" accent="border-[#4d6fb3]" />
          </div>
        </div>

        <div className="flex items-center px-6 py-8 sm:px-10">
          <div className="w-full rounded-lg border border-[#dde4ef] bg-white p-6 shadow-sm">
            <div className="grid grid-cols-2 rounded-md bg-[#eef2f7] p-1">
              <button
                type="button"
                onClick={() => setMode("login")}
                className={mode === "login" ? activeTabClass : inactiveTabClass}
              >
                Login
              </button>
              <button
                type="button"
                onClick={() => setMode("register")}
                className={mode === "register" ? activeTabClass : inactiveTabClass}
              >
                Register
              </button>
            </div>

            <h2 className="mt-8 text-2xl font-semibold">{mode === "login" ? "Welcome Back" : "Create Account"}</h2>

            <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
              {mode === "register" && (
                <Field label="Username">
                  <Input
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    minLength={3}
                    maxLength={50}
                    required
                    placeholder="darsh"
                  />
                </Field>
              )}

              <Field label="Email">
                <Input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  placeholder="you@example.com"
                />
              </Field>

              <Field label="Password">
                <Input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  minLength={8}
                  required
                  placeholder="At least 8 characters"
                />
              </Field>

              <Button type="submit" disabled={isSubmitting} className="mt-2">
                {isSubmitting ? "Working" : mode === "login" ? "Sign in" : "Register"}
              </Button>
            </form>

            {status && (
              <p className="mt-4 rounded-md bg-[#fff4ed] px-3 py-2 text-sm text-[#9a3412]">
                {status}
              </p>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}

const activeTabClass = "h-10 rounded px-3 text-sm font-semibold bg-white text-[#172033] shadow-sm transition";
const inactiveTabClass = "h-10 rounded px-3 text-sm font-semibold text-[#647187] transition";

type MetricProps = {
  label: string;
  value: string;
  accent: string;
};

function Metric({ label, value, accent }: MetricProps) {
  return (
    <div className={`border-l-4 ${accent} bg-white px-4 py-3 shadow-sm`}>
      <p className="text-xs font-medium uppercase text-[#6b7688]">{label}</p>
      <p className="mt-1 font-semibold">{value}</p>
    </div>
  );
}

function getErrorMessage(error: unknown) {
  if (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    typeof error.response === "object" &&
    error.response !== null &&
    "data" in error.response
  ) {
    const data = error.response.data as { message?: string };
    return data.message ?? "Authentication failed.";
  }

  return error instanceof Error ? error.message : "Authentication failed.";
}
