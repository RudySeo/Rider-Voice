"use client";

import { ListChecks } from "lucide-react";
import type { ActionChecklistItem } from "@/types/analysis";

interface ActionChecklistProps {
  items: ActionChecklistItem[];
}

export function ActionChecklist({ items }: ActionChecklistProps) {
  return (
    <section aria-labelledby="checklist-heading" className="space-y-4">
      <div>
        <p className="text-sm font-semibold text-cyan-700">실행 체크리스트</p>
        <h2 id="checklist-heading" className="mt-1 text-2xl font-semibold">
          이번 주 액션
        </h2>
      </div>

      <div className="grid gap-3">
        {items.slice(0, 7).map((item, index) => (
          <article
            className="grid gap-4 border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-[120px_1fr]"
            key={`${item.task}-${index}`}
          >
            <div className="flex items-center gap-2 text-cyan-700">
              <ListChecks aria-hidden="true" size={18} />
              <span className="text-sm font-semibold">{priorityLabel[item.priority]}</span>
            </div>
            <div>
              <h3 className="text-base font-semibold leading-6 text-slate-950">{item.task}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-700">{item.reason}</p>
              <p className="mt-2 text-sm font-medium leading-6 text-slate-900">
                기대 효과: {item.expectedImpact}
              </p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

const priorityLabel = {
  high: "높음",
  medium: "보통",
  low: "낮음",
} as const;
