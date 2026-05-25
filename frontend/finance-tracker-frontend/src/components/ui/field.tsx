import { ReactNode } from "react";

type FieldProps = {
  label: string;
  children: ReactNode;
};

export function Field({ label, children }: FieldProps) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-medium text-[#344054]">
      {label}
      {children}
    </label>
  );
}
