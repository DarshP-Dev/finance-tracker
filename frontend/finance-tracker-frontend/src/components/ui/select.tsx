import { SelectHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type SelectProps = SelectHTMLAttributes<HTMLSelectElement>;

export function Select({ className, ...props }: SelectProps) {
  return (
    <select
      className={cn(
        "h-10 w-full rounded-md border border-[#cfd8e6] bg-white px-3 text-sm text-[#172033] outline-none transition focus:border-[#195b4d] focus:ring-2 focus:ring-[#195b4d]/15",
        className,
      )}
      {...props}
    />
  );
}
