export default function Home() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-10 text-slate-50">
      <section className="mx-auto flex min-h-[calc(100vh-5rem)] w-full max-w-5xl flex-col justify-center">
        <p className="text-sm font-medium uppercase tracking-[0.2em] text-cyan-300">
          YouTube Channel Insight
        </p>
        <h1 className="mt-4 max-w-3xl text-4xl font-semibold leading-tight sm:text-5xl">
          채널 URL 기반 인사이트 대시보드 준비 중
        </h1>
        <p className="mt-6 max-w-2xl text-base leading-7 text-slate-300">
          이 화면은 프로젝트 골격 검증을 위한 임시 placeholder입니다. 실제
          채널 입력, 수집, 분석 flow는 이후 step에서 구현합니다.
        </p>
      </section>
    </main>
  );
}
