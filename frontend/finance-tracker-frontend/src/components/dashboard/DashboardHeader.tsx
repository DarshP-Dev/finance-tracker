"use client";

import { Button } from "@/components/ui/button";
import type { AuthResponse } from "@/lib/api";

type DashboardHeaderProps = {
  auth: AuthResponse;
  activeView: "dashboard" | "transactions";
  onViewChange: (view: "dashboard" | "transactions") => void;
  onSignOut: () => void;
};

export function DashboardHeader({ auth, activeView, onViewChange, onSignOut }: DashboardHeaderProps) {
  return (
    <header className="border-b border-[#d9e1ec] bg-white">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-5 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-semibold text-[#195b4d]">Finance Tracker</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-normal">
            {activeView === "dashboard" ? "Dashboard" : "Transactions"}
          </h1>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="grid grid-cols-2 rounded-md bg-[#eef2f7] p-1">
            <button
              type="button"
              onClick={() => onViewChange("dashboard")}
              className={activeView === "dashboard" ? activeTabClass : inactiveTabClass}
            >
              Dashboard
            </button>
            <button
              type="button"
              onClick={() => onViewChange("transactions")}
              className={activeView === "transactions" ? activeTabClass : inactiveTabClass}
            >
              Transactions
            </button>
          </div>
          <div className="text-sm text-[#667085]">
            {auth.username} <span className="text-[#98a2b3]">/</span> {auth.email}
          </div>
          <Button type="button" variant="secondary" onClick={onSignOut}>
            Sign out
          </Button>
        </div>
      </div>
    </header>
  );
}

const activeTabClass = "h-9 rounded px-3 text-sm font-semibold bg-white text-[#172033] shadow-sm transition";
const inactiveTabClass = "h-9 rounded px-3 text-sm font-semibold text-[#647187] transition hover:text-[#172033]";
