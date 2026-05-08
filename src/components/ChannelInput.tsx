"use client";

import { Search } from "lucide-react";
import type { FormEvent } from "react";

interface ChannelInputProps {
  value: string;
  disabled?: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

export function ChannelInput({
  value,
  disabled = false,
  onChange,
  onSubmit,
}: ChannelInputProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <form
      className="flex w-full flex-col gap-3 sm:flex-row"
      aria-label="YouTube 채널 분석 입력"
      onSubmit={handleSubmit}
    >
      <label className="sr-only" htmlFor="channel-url">
        YouTube 채널 URL
      </label>
      <input
        id="channel-url"
        className="min-h-12 flex-1 border border-slate-300 bg-white px-4 text-base text-slate-950 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-200 disabled:bg-slate-100"
        value={value}
        disabled={disabled}
        placeholder="https://www.youtube.com/@handle"
        autoComplete="off"
        onChange={(event) => onChange(event.target.value)}
      />
      <button
        className="inline-flex min-h-12 items-center justify-center gap-2 bg-slate-950 px-5 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
        type="submit"
        disabled={disabled}
      >
        <Search aria-hidden="true" size={18} />
        분석 시작
      </button>
    </form>
  );
}
