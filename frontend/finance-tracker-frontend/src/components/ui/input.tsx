import { InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type InputProps = InputHTMLAttributes<HTMLInputElement>;

export function Input({ className, ...props }: InputProps) {
  return (
    <input
      className={cn(
        "h-10 w-full rounded-md border border-[#cfd8e6] bg-white px-3 text-sm text-[#172033] outline-none transition placeholder:text-[#8a96a8] focus:border-[#195b4d] focus:ring-2 focus:ring-[#195b4d]/15",
        className,
      )}
      {...props}
    />
  );
}
