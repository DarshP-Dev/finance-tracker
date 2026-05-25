import { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

const variants: Record<ButtonVariant, string> = {
  primary: "bg-[#195b4d] text-white hover:bg-[#12473c] disabled:bg-[#9eb8af]",
  secondary: "border border-[#cfd8e6] bg-white text-[#172033] hover:bg-[#f5f7fb]",
  danger: "bg-[#b42318] text-white hover:bg-[#8f1d14] disabled:bg-[#e2aaa5]",
  ghost: "text-[#4d5a6d] hover:bg-[#eef2f7]",
};

export function Button({ className, variant = "primary", ...props }: ButtonProps) {
  return (
    <button
      className={cn(
        "inline-flex h-10 items-center justify-center rounded-md px-4 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-[#195b4d]/20 disabled:cursor-not-allowed",
        variants[variant],
        className,
      )}
      {...props}
    />
  );
}
