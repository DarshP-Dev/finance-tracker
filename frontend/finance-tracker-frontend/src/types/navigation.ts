export type AppView =
  | "dashboard"
  | "analytics"
  | "transactions"
  | "customer"
  | "chat"
  | "wallet"
  | "members"
  | "help"
  | "settings";

export const appViewRoutes: Record<AppView, string> = {
  dashboard: "/",
  analytics: "/analytics",
  transactions: "/transactions",
  customer: "/customer",
  chat: "/chat",
  wallet: "/wallet",
  members: "/members",
  help: "/help",
  settings: "/settings",
};

export function getAppView(pathname: string): AppView {
  const route = pathname === "/" ? "/" : pathname.replace(/\/$/, "");
  const match = Object.entries(appViewRoutes).find(([, path]) => path === route);
  return (match?.[0] as AppView | undefined) ?? "dashboard";
}
