import { CircleAlert, Info, TrendingUp } from "lucide-react";
import { formatCategory, formatDate } from "@/components/transactions/formatters";
import type { FinancialInsight, FinancialInsightSeverity, FinancialInsightType } from "@/types/financial-insights";

const typeLabels: Record<FinancialInsightType, string> = {
  SPENDING: "Spending",
  BUDGET: "Budget",
  SAVINGS: "Savings",
  INCOME: "Cash Flow",
  RECURRING: "Recurring",
  FORECAST: "Forecast",
  INVESTMENT: "Investments",
};

const severityStyles: Record<FinancialInsightSeverity, { label: string; className: string; icon: typeof Info }> = {
  INFO: { label: "Insight", className: "bg-[#f3f2f5] text-[#46404b]", icon: Info },
  POSITIVE: { label: "Positive", className: "bg-[#ecfdf3] text-[#027a48]", icon: TrendingUp },
  WARNING: { label: "Warning", className: "bg-[#fffaeb] text-[#93370d]", icon: CircleAlert },
};

export function FinancialInsightCard({ insight, compact = false }: { insight: FinancialInsight; compact?: boolean }) {
  const severity = severityStyles[insight.severity];
  const Icon = severity.icon;
  const period = insight.from && insight.to
    ? insight.from === insight.to ? formatDate(insight.from) : `${formatDate(insight.from)} – ${formatDate(insight.to)}`
    : null;

  return (
    <article className={`min-w-0 rounded-xl border border-[#e4e0e7] bg-white ${compact ? "p-4" : "p-5"}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium text-[#77717d]">{typeLabels[insight.type]}</span>
        <span className={`insight-severity inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${severity.className}`} data-severity={insight.severity}>
          <Icon size={14} aria-hidden="true" />
          {severity.label}
        </span>
      </div>
      <h3 className="mt-3 break-words text-sm font-semibold text-[#151515] [overflow-wrap:anywhere]">{insight.title}</h3>
      <p className={`mt-2 break-words text-sm leading-6 text-[#46404b] [overflow-wrap:anywhere] ${compact ? "line-clamp-3" : ""}`}>
        {insight.message}
      </p>
      {!compact && (insight.category || period) && (
        <div className="mt-4 flex flex-wrap gap-x-3 gap-y-1 text-xs leading-5 text-[#77717d]">
          {insight.category && <span>{formatCategory(insight.category)}</span>}
          {period && <span>{period}</span>}
        </div>
      )}
    </article>
  );
}
