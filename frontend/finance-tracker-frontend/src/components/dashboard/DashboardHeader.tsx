"use client";

import Image from "next/image";
import { useState, type ReactNode } from "react";
import {
  BarChart3,
  Bell,
  ChevronDown,
  CircleHelp,
  Home,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageCircle,
  PieChart,
  Search,
  Settings,
  TrendingUp,
  WalletCards,
} from "lucide-react";
import type { AuthResponse } from "@/lib/api";
import type { AppView } from "@/types/navigation";

type DashboardHeaderProps = {
  auth: AuthResponse;
  activeView: AppView;
  isDarkMode: boolean;
  onViewChange: (view: AppView) => void;
  onSignOut: () => void;
  children: ReactNode;
};

export function DashboardHeader({ auth, activeView, isDarkMode, onViewChange, onSignOut, children }: DashboardHeaderProps) {
  const [isAccountOpen, setIsAccountOpen] = useState(false);
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  function navigateTo(view: AppView) {
    setIsAccountOpen(false);
    setIsMobileNavOpen(false);
    setIsNotificationsOpen(false);
    setIsSearchOpen(false);
    onViewChange(view);
  }

  function handleSettingsClick() {
    navigateTo("settings");
  }

  return (
    <div className={isDarkMode ? "dark-shell min-h-screen bg-black p-4 text-[#ff8a3d] lg:p-5" : "min-h-screen bg-[#e7e5ea] p-4 text-[#151515] lg:p-5"}>
      <div className={isDarkMode ? "mx-auto grid min-h-[calc(100vh-40px)] max-w-[1680px] overflow-hidden rounded-sm border border-[#2b292f] bg-[#17171a] lg:grid-cols-[220px_minmax(0,1fr)]" : "mx-auto grid min-h-[calc(100vh-40px)] max-w-[1680px] overflow-hidden rounded-sm border border-[#d8d5dc] bg-[#fbfbfc] lg:grid-cols-[220px_minmax(0,1fr)]"}>
        <aside className={isDarkMode ? "hidden border-r border-[#2b292f] bg-[#1f1f23] px-4 py-5 lg:flex lg:flex-col" : "hidden border-r border-[#e5e1e8] bg-white px-4 py-5 lg:flex lg:flex-col"}>
          <div className={isDarkMode ? "flex min-h-12 items-center gap-2 rounded-xl border border-[#343139] px-4" : "flex min-h-12 items-center gap-2 rounded-xl border border-[#e4e0e7] px-4"}>
            <Image
              src="/personal-finance-logo.png"
              alt="Personal Finance Tracker logo"
              width={32}
              height={32}
              className="h-8 w-8 rounded-lg object-cover"
            />
            <span className="text-sm font-semibold leading-tight">Personal Finance Tracker</span>
          </div>

          <SidebarSection label="Main Menu">
            <SidebarButton active={activeView === "dashboard"} onClick={() => navigateTo("dashboard")} label="Dashboard" icon={<LayoutDashboard size={17} />} />
            <SidebarButton active={activeView === "transactions"} onClick={() => navigateTo("transactions")} label="Transactions" icon={<WalletCards size={17} />} />
            <SidebarButton active={activeView === "analytics"} onClick={() => navigateTo("analytics")} label="Analytics" icon={<BarChart3 size={17} />} />
            <SidebarButton active={activeView === "chat"} onClick={() => navigateTo("chat")} label="Chat" icon={<MessageCircle size={17} />} />
          </SidebarSection>

          <SidebarSection label="Financial Planning">
            <SidebarButton active={activeView === "budgets"} onClick={() => navigateTo("budgets")} label="Budgets" icon={<PieChart size={17} />} />
            <SidebarButton active={activeView === "investments"} onClick={() => navigateTo("investments")} label="Investments" icon={<TrendingUp size={17} />} />
          </SidebarSection>

          <div className="mt-auto grid gap-2">
            <SidebarButton active={activeView === "help"} onClick={() => navigateTo("help")} label="Help" icon={<CircleHelp size={17} />} />
            <SidebarButton active={activeView === "settings"} onClick={() => navigateTo("settings")} label="Settings" icon={<Settings size={17} />} />
            <button
              type="button"
              onClick={onSignOut}
              className={isDarkMode ? "flex h-10 items-center gap-3 rounded-xl border border-[#343139] px-4 text-sm font-semibold text-[#ff8a68] transition hover:border-[#ff5a1f] hover:bg-[#2a211f]" : "flex h-10 items-center gap-3 rounded-xl border border-[#e4e0e7] px-4 text-sm font-semibold text-[#c83218] transition hover:border-[#ff5a1f] hover:bg-[#fff2ec]"}
            >
              <LogOut size={17} />
              Logout
            </button>
          </div>
        </aside>

        <section className={isDarkMode ? "min-w-0 bg-[#17171a] px-4 py-4 sm:px-6 lg:px-6" : "min-w-0 bg-[#fbfbfc] px-4 py-4 sm:px-6 lg:px-6"}>
          <header className={isDarkMode ? "flex flex-col gap-4 border-b border-[#2b292f] pb-4 lg:flex-row lg:items-center" : "flex flex-col gap-4 border-b border-[#e5e1e8] pb-4 lg:flex-row lg:items-center"}>
            <div className="relative flex w-full items-center justify-between gap-4 lg:w-auto lg:flex-none lg:justify-start">
              <button
                type="button"
                onClick={() => navigateTo("dashboard")}
                className={isDarkMode ? "inline-flex h-10 items-center gap-2 rounded-xl border border-[#343139] bg-[#1f1f23] px-4 text-sm font-semibold text-[#ff8a68] transition hover:border-[#ff5a1f]" : "inline-flex h-10 items-center gap-2 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#ff5a1f] transition hover:border-[#ff5a1f]"}
              >
                <Home size={16} />
                Home
              </button>
              <button
                type="button"
                aria-expanded={isMobileNavOpen}
                aria-controls="mobile-navigation"
                onClick={() => setIsMobileNavOpen((current) => !current)}
                className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-[#e4e0e7] bg-white text-[#ff5a1f] lg:hidden"
              >
                <Menu size={18} />
                <span className="sr-only">Open navigation</span>
              </button>
            </div>

            <div className="flex min-w-0 flex-1 flex-col justify-end gap-3 sm:flex-row sm:items-center">
              <div className={isDarkMode ? "relative flex h-10 min-w-0 flex-1 items-center rounded-xl border border-[#343139] bg-[#1f1f23] px-4" : "relative flex h-10 min-w-0 flex-1 items-center rounded-xl border border-[#ece8ef] bg-white px-4"}>
                <Search size={17} className="shrink-0 text-[#8d8792]" />
                <input
                  type="search"
                  aria-label="Search"
                  placeholder="Search features"
                  value={searchQuery}
                  onFocus={() => {
                    setIsSearchOpen(true);
                    setIsNotificationsOpen(false);
                    setIsAccountOpen(false);
                  }}
                  onChange={(event) => {
                    setSearchQuery(event.target.value);
                    setIsSearchOpen(true);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Escape") {
                      setIsSearchOpen(false);
                      event.currentTarget.blur();
                    }
                  }}
                  className={isDarkMode ? "min-w-0 flex-1 bg-transparent px-3 text-sm text-white outline-none placeholder:text-[#8d8792]" : "min-w-0 flex-1 bg-transparent px-3 text-sm text-[#151515] outline-none placeholder:text-[#8d8792]"}
                />
                {isSearchOpen && (
                  <div className="absolute left-0 right-0 top-[calc(100%+10px)] z-50 rounded-2xl border border-[#e4e0e7] bg-white p-4 text-sm shadow-xl">
                    <p className="font-semibold text-[#151515]">Search is being prepared</p>
                    <p className="mt-1 leading-5 text-[#77717d]">
                      {searchQuery.trim()
                        ? `Results for “${searchQuery.trim()}” will be available when global search is added.`
                        : "Global results will be added as each section becomes searchable."}
                    </p>
                    <button type="button" onClick={() => setIsSearchOpen(false)} className="mt-3 text-xs font-semibold text-[#ff5a1f]">
                      Close
                    </button>
                  </div>
                )}
              </div>
              <div className="relative">
                <button
                  type="button"
                  aria-label="Notifications"
                  aria-expanded={isNotificationsOpen}
                  aria-haspopup="dialog"
                  onClick={() => {
                    setIsNotificationsOpen((current) => !current);
                    setIsAccountOpen(false);
                    setIsSearchOpen(false);
                  }}
                  className="h-10 w-10 rounded-xl border border-[#ece8ef] bg-white text-sm font-semibold transition hover:border-[#ff5a1f]"
                >
                  <Bell className="mx-auto" size={17} />
                </button>
                {isNotificationsOpen && (
                  <div role="dialog" aria-label="Notifications" className="absolute right-0 top-[calc(100%+10px)] z-50 w-72 rounded-2xl border border-[#e4e0e7] bg-white p-4 shadow-xl">
                    <p className="font-semibold text-[#151515]">Notifications</p>
                    <div className="mt-4 rounded-xl bg-[#f8fafc] px-4 py-5 text-center">
                      <Bell className="mx-auto text-[#a49eaa]" size={20} />
                      <p className="mt-2 text-sm font-medium text-[#46404b]">No notifications yet.</p>
                    </div>
                  </div>
                )}
              </div>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => {
                    setIsAccountOpen((current) => !current);
                    setIsNotificationsOpen(false);
                    setIsSearchOpen(false);
                  }}
                  className="flex items-center gap-3 rounded-xl px-2 py-1.5 transition hover:bg-[#fff3ed]"
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#2c1a14] text-sm font-bold text-white">
                    {auth.username.slice(0, 1).toUpperCase()}
                  </div>
                  <div className="hidden min-w-0 text-left sm:block">
                    <div className="truncate text-sm font-semibold">{auth.username}</div>
                    <div className="truncate text-xs text-[#74707a]">{auth.email}</div>
                  </div>
                  <ChevronDown size={16} className="text-[#74707a]" />
                </button>

                {isAccountOpen && (
                  <div className="absolute right-0 top-[calc(100%+10px)] z-50 w-48 rounded-2xl border border-[#e4e0e7] bg-white p-2 shadow-xl">
                    <button
                      type="button"
                      onClick={() => navigateTo("help")}
                      className="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-[#151515] transition hover:bg-[#fff3ed] hover:text-[#ff5a1f]"
                    >
                      <CircleHelp size={16} />
                      Help
                    </button>
                    <button
                      type="button"
                      onClick={handleSettingsClick}
                      className="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-[#151515] transition hover:bg-[#fff3ed] hover:text-[#ff5a1f]"
                    >
                      <Settings size={16} />
                      Settings
                    </button>
                    <button
                      type="button"
                      onClick={onSignOut}
                      className="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm font-semibold text-[#c83218] transition hover:bg-[#fff3ed]"
                    >
                      <LogOut size={16} />
                      Logout
                    </button>
                  </div>
                )}
              </div>
            </div>
          </header>

          {isMobileNavOpen && (
            <nav id="mobile-navigation" aria-label="Mobile navigation" className="mt-4 grid grid-cols-2 gap-2 rounded-2xl border border-[#e4e0e7] bg-white p-3 lg:hidden">
              <MobileNavButton active={activeView === "dashboard"} label="Dashboard" onClick={() => navigateTo("dashboard")} />
              <MobileNavButton active={activeView === "transactions"} label="Transactions" onClick={() => navigateTo("transactions")} />
              <MobileNavButton active={activeView === "analytics"} label="Analytics" onClick={() => navigateTo("analytics")} />
              <MobileNavButton active={activeView === "chat"} label="Chat" onClick={() => navigateTo("chat")} />
              <MobileNavButton active={activeView === "budgets"} label="Budgets" onClick={() => navigateTo("budgets")} />
              <MobileNavButton active={activeView === "investments"} label="Investments" onClick={() => navigateTo("investments")} />
              <MobileNavButton active={activeView === "help"} label="Help" onClick={() => navigateTo("help")} />
              <MobileNavButton active={activeView === "settings"} label="Settings" onClick={() => navigateTo("settings")} />
            </nav>
          )}

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
    <div className="mt-6 border-b border-[#eeeaf1] pb-4 last:border-b-0">
      <div className="mb-3 px-4 text-xs font-medium uppercase text-[#8d8792]">{label}</div>
      <div className="grid gap-1">{children}</div>
    </div>
  );
}

type SidebarButtonProps = {
  active: boolean;
  label: string;
  icon: ReactNode;
  onClick: () => void;
};

function SidebarButton({ active, label, icon, onClick }: SidebarButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? "relative flex h-10 items-center gap-3 rounded-xl border border-[#e4e0e7] bg-white px-4 text-sm font-semibold text-[#ff5a1f] shadow-sm before:absolute before:-left-5 before:h-7 before:w-1 before:bg-[#ff5a1f]"
          : "flex h-10 items-center gap-3 rounded-xl px-4 text-sm font-semibold text-[#151515] transition hover:bg-[#fff2ec] hover:text-[#ff5a1f]"
      }
    >
      <span className="flex w-4 items-center justify-center">{icon}</span>
      {label}
    </button>
  );
}

function MobileNavButton({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={active ? "rounded-xl bg-[#fff3ed] px-3 py-2.5 text-left text-sm font-semibold text-[#ff5a1f]" : "rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-[#46404b] hover:bg-[#fff7f2]"}
    >
      {label}
    </button>
  );
}
