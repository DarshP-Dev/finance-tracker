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
};

export type InvestmentPayload = {
  ticker: string;
  shares: number;
  purchasePrice: number;
  purchaseDate: string;
};
