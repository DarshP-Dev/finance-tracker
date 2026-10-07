export type Investment = {
  id: number;
  ticker: string;
  shares: number;
  purchasePrice: number;
  purchaseDate: string;
  amountInvested: number;
  createdAt: string;
};

export type InvestmentHolding = {
  ticker: string;
  totalShares: number;
  totalInvested: number;
  averagePurchasePrice: number;
  purchaseCount: number;
  currentPrice?: number | null;
  marketValue?: number | null;
  gainLoss?: number | null;
  returnPercentage?: number | null;
  allocationPercentage?: number | null;
  quoteStatus?: "AVAILABLE" | "STALE" | "DISABLED" | "UNAVAILABLE" | "UNKNOWN_SYMBOL" | "RATE_LIMITED" | "INVALID_SYMBOL" | "UNSUPPORTED_CURRENCY" | "UNSUPPORTED_ASSET";
  lastUpdated?: string | null;
  marketTimestamp?: string | null;
};

export type Portfolio = {
  summary: {
    totalCostBasis: number;
    totalMarketValue: number | null;
    totalGainLoss: number | null;
    totalReturnPercentage: number | null;
    holdingCount: number;
    quotedHoldingCount: number;
    currency: string;
    status: "AVAILABLE" | "STALE" | "PARTIAL" | "UNAVAILABLE" | "DISABLED" | "EMPTY";
    lastUpdated: string | null;
  };
  holdings: InvestmentHolding[];
};

export type InvestmentPayload = {
  ticker: string;
  shares: number;
  purchasePrice: number;
  purchaseDate: string;
};

export type PortfolioQuotePolicy = "ON_DEMAND" | "CACHE_ONLY" | "MISSING_ONLY";
export type PortfolioRefreshResponse = {
  portfolio: Portfolio;
  status: "ACCEPTED" | "COOLDOWN";
  retryAfterSeconds: number;
};
