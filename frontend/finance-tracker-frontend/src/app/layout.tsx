import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Personal Finance Tracker",
  description: "Track income, expenses, and cash flow with secure account access.",
  icons: {
    icon: "/personal-finance-logo.png",
    shortcut: "/personal-finance-logo.png",
    apple: "/personal-finance-logo.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
