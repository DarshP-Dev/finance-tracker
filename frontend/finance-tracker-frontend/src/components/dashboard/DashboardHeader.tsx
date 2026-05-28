"use client";

import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import type { AuthResponse } from "@/lib/api";

type DashboardHeaderProps = {
  auth: AuthResponse;
  activeView: "dashboard" | "transactions";
  onViewChange: (view: "dashboard" | "transactions") => void;
  onSignOut: () => void;
  children: ReactNode;
};

export function DashboardHeader({ auth, activeView, onViewChange, onSignOut, children }: DashboardHeaderProps) {
  return (
    <div className="min-h-screen bg-[#e7e5ea] p-4 text-[#151515] lg:p-5">
      <div className="mx-auto grid min-h-[calc(100vh-40px)] max-w-[1440px] overflow-hidden rounded-sm border border-[#d8d5dc] bg-[#fbfbfc] lg:grid-cols-[250px_1fr]">
        <aside className="hidden border-r border-[#e5e1e8] bg-white px-5 py-6 lg:flex lg:flex-col">
          <div className="flex h-12 items-center gap-3 rounded-xl border border-[#e4e0e7] px-4">
            <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-[#ff5a1f] text-sm font-black">
              S
            </div>
            <span className="font-semibold">SmartTracker</span>
          </div>

          <SidebarSection label="Main Menu">
            <SidebarButton active={activeView === "dashboard"} onClick={() => onViewChange("dashboard")} label="Dashboard" icon="D" />
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Analytics" icon="A" />
            <SidebarButton active={activeView === "transactions"} onClick={() => onViewChange("transactions")} label="Transaction" icon="T" />
            <SidebarButton active={false} onClick={() => onViewChange("transactions")} label="Customer" icon="C" />
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Chat" icon="M" />
          </SidebarSection>

          <SidebarSection label="Account">
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Wallet" icon="W" />
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Members" icon="U" />
          </SidebarSection>

          <SidebarSection label="Setting">
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Setting" icon="S" />
            <SidebarButton active={false} onClick={() => onViewChange("dashboard")} label="Report" icon="R" />
          </SidebarSection>

          <button
            type="button"
            onClick={onSignOut}
            className="mt-auto flex h-11 items-center gap-3 rounded-xl border border-[#e4e0e7] px-4 text-sm font-semibold text-[#c83218] transition hover:border-[#ff5a1f] hover:bg-[#fff2ec]"
          >
            <span className="w-4 text-center">L</span>
            Logout
          </button>
        </aside>

        <section className="min-w-0 bg-[#fbfbfc] px-4 py-4 sm:px-6 lg:px-8">
          <header className="flex flex-col gap-4 border-b border-[#e5e1e8] pb-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-4">
              <span className="hidden text-xl font-semibold lg:inline">&gt;&gt;</span>
              <div className="grid grid-cols-2 rounded-xl border border-[#e4e0e7] bg-white p-1 lg:hidden">
                <button type="button" onClick={() => onViewChange("dashboard")} className={activeView === "dashboard" ? activeTabClass : inactiveTabClass}>
                  Dashboard
                </button>
                <button type="button" onClick={() => onViewChange("transactions")} className={activeView === "transactions" ? activeTabClass : inactiveTabClass}>
                  Transaction
                </button>
              </div>
              <button className="hidden h-10 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#ff5a1f] lg:block">
                {activeView === "dashboard" ? "Dashboard" : "Transaction"}
              </button>
            </div>

            <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center lg:max-w-[760px]">
              <label className="relative flex-1">
                <span className="absolute left-4 top-1/2 -translate-y-1/2 text-[#86818c]">Search</span>
                <input
                  className="h-11 w-full rounded-xl border border-[#ece8ef] bg-white pl-20 pr-4 text-sm outline-none transition focus:border-[#ff5a1f] focus:ring-2 focus:ring-[#ff5a1f]/10"
                  aria-label="Search"
                />
              </label>
              <button className="h-11 w-11 rounded-xl border border-[#ece8ef] bg-white text-sm font-semibold transition hover:border-[#ff5a1f]">
                !
              </button>
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#2c1a14] text-sm font-bold text-white">
                  {auth.username.slice(0, 1).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold">{auth.username}</div>
                  <div className="truncate text-xs text-[#74707a]">{auth.email}</div>
                </div>
                <Button type="button" variant="ghost" className="h-9 px-2 lg:hidden" onClick={onSignOut}>
                  Logout
                </Button>
              </div>
            </div>
          </header>

          {children}
        </section>
      </div>
    </div>
  );
}

type SidebarSectionProps = {
  label: string;
  children: ReactNode;
};

function SidebarSection({ label, children }: SidebarSectionProps) {
  return (
    <div className="mt-8 border-b border-[#eeeaf1] pb-6 last:border-b-0">
      <div className="mb-3 px-4 text-xs font-medium uppercase text-[#8d8792]">{label}</div>
      <div className="grid gap-1">{children}</div>
    </div>
  );
}

type SidebarButtonProps = {
  active: boolean;
  label: string;
  icon: string;
  onClick: () => void;
};

function SidebarButton({ active, label, icon, onClick }: SidebarButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? "relative flex h-11 items-center gap-3 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#ff5a1f] shadow-sm before:absolute before:-left-5 before:h-7 before:w-1 before:bg-[#ff5a1f]"
          : "flex h-11 items-center gap-3 rounded-xl px-4 text-sm font-semibold text-[#151515] transition hover:bg-[#fff2ec] hover:text-[#ff5a1f]"
      }
    >
      <span className="w-4 text-center text-xs">{icon}</span>
      {label}
    </button>
  );
}

const activeTabClass = "h-9 rounded-lg px-3 text-sm font-semibold bg-white text-[#ff5a1f] shadow-sm transition";
const inactiveTabClass = "h-9 rounded-lg px-3 text-sm font-semibold text-[#67616d] transition hover:text-[#151515]";
