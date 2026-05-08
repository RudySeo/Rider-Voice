"use client";

import { AlertTriangle } from "lucide-react";

interface ErrorPanelProps {
  title: string;
  message: string;
  action: string;
}

export function ErrorPanel({ title, message, action }: ErrorPanelProps) {
  return (
    <section
      className="border border-rose-200 bg-rose-50 p-5 text-rose-950"
      role="alert"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle aria-hidden="true" className="mt-0.5 text-rose-600" size={20} />
        <div>
          <h2 className="text-base font-semibold">{title}</h2>
          <p className="mt-2 text-sm leading-6">{message}</p>
          <p className="mt-3 text-sm font-medium">{action}</p>
        </div>
      </div>
    </section>
  );
}
