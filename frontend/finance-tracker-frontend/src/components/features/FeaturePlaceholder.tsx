import type { LucideIcon } from "lucide-react";
import {
  BarChart3,
  CircleHelp,
  MessageCircle,
  PieChart,
  TrendingUp,
  UsersRound,
  WalletCards,
} from "lucide-react";
import type { AppView } from "@/types/navigation";

type FeatureDetails = {
  title: string;
  description: string;
  nextStep: string;
  icon: LucideIcon;
};

const featureDetails: Record<Exclude<AppView, "dashboard" | "transactions" | "settings">, FeatureDetails> = {
  analytics: {
    title: "Analytics",
    description: "Explore trends, comparisons, and deeper insights across your financial activity.",
    nextStep: "Custom reports and time-period comparisons are planned for a future update.",
    icon: BarChart3,
  },
  customer: {
    title: "Customer",
    description: "Organize customer records and connect them with relevant financial activity.",
    nextStep: "Customer profiles and transaction associations will be added incrementally.",
    icon: UsersRound,
  },
  chat: {
    title: "Chat",
    description: "Get contextual help and discuss your financial data from one workspace.",
    nextStep: "Conversation tools are planned and will be developed as a separate feature.",
    icon: MessageCircle,
  },
  wallet: {
    title: "Wallet",
    description: "View and manage the accounts that make up your available balance.",
    nextStep: "Account connections and balance tracking will be added in a future update.",
    icon: WalletCards,
  },
  members: {
    title: "Members",
    description: "Manage the people who can participate in your finance workspace.",
    nextStep: "Invitations, permissions, and shared access are planned for later development.",
    icon: UsersRound,
  },
  budgets: {
    title: "Budgets",
    description: "Plan monthly spending limits and track progress across your expense categories.",
    nextStep: "Budget creation and progress tracking will be developed as a separate feature.",
    icon: PieChart,
  },
  investments: {
    title: "Investments",
    description: "Organize holdings and review the long-term performance of your portfolio.",
    nextStep: "Investment tracking and portfolio insights will be developed as a separate feature.",
    icon: TrendingUp,
  },
  help: {
    title: "Help",
    description: "Find guidance for using and troubleshooting the Personal Finance Tracker.",
    nextStep: "Guides and support resources will be added as the application grows.",
    icon: CircleHelp,
  },
};

type FeaturePlaceholderProps = {
  view: keyof typeof featureDetails;
};

export function FeaturePlaceholder({ view }: FeaturePlaceholderProps) {
  const feature = featureDetails[view];
  const Icon = feature.icon;

  return (
    <div className="py-6">
      <section className="overflow-hidden rounded-2xl border border-[#e4e0e7] bg-white shadow-sm">
        <div className="h-1 bg-[#ff5a1f]" />
        <div className="px-6 py-10 sm:px-10 sm:py-14">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#fff3ed] text-[#ff5a1f]">
            <Icon size={22} aria-hidden="true" />
          </div>
          <div className="mt-6 max-w-2xl">
            <div className="inline-flex rounded-full border border-[#ffd8c7] bg-[#fff7f2] px-3 py-1 text-xs font-semibold uppercase tracking-[0.12em] text-[#c2410c]">
              Coming soon
            </div>
            <h1 className="mt-4 text-3xl font-semibold tracking-normal text-[#151515]">{feature.title}</h1>
            <p className="mt-3 text-base leading-7 text-[#667085]">{feature.description}</p>
            <p className="mt-6 border-l-2 border-[#ff5a1f] pl-4 text-sm leading-6 text-[#77717d]">
              {feature.nextStep}
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
