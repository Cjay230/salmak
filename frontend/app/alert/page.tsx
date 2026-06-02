'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getAlert, markSafe } from '@/lib/api';

export default function AlertPage() {
  const router = useRouter();
  const [evacuationRoute, setEvacuationRoute] = useState<string>('');
  const [safeLoading, setSafeLoading] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem('salmak_alert_data');
      if (raw) {
        const data = JSON.parse(raw);
        if (data.evacuationRoute) setEvacuationRoute(data.evacuationRoute);
      }
    } catch {
      /* ignore */
    }

    // Keep checking — dismiss alert automatically once it clears
    const phone = localStorage.getItem('salmak_phone');
    if (!phone) return;

    const interval = setInterval(async () => {
      try {
        const data = await getAlert(phone);
        if (!data.hasAlert) {
          localStorage.removeItem('salmak_alert_data');
          router.push('/home');
        }
      } catch {
        /* silent */
      }
    }, 30_000);

    return () => clearInterval(interval);
  }, [router]);

  return (
    <main className="min-h-screen bg-red-600 flex flex-col items-center justify-between px-5 py-12 relative overflow-hidden">
      {/* Background pulse glow */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            'radial-gradient(circle at center, rgba(220,38,38,0.4) 0%, rgba(153,27,27,0.8) 100%)',
          animation: 'ring-pulse 3s ease-in-out infinite alternate',
        }}
      />

      <div className="relative z-10 w-full max-w-sm flex flex-col items-center gap-8 flex-1">
        {/* Header */}
        <div className="text-center pt-4">
          <p className="text-red-200 text-xs font-semibold tracking-widest uppercase mb-1">
            Salmak — سلمك
          </p>
          <p className="text-white text-sm font-bold tracking-wider">⚠ EMERGENCY ALERT ⚠</p>
        </div>

        {/* Concentric circles animation */}
        <div className="relative flex items-center justify-center" style={{ width: 280, height: 280 }}>
          {[0, 0.55, 1.1, 1.65].map((delay, i) => (
            <div
              key={i}
              className="absolute rounded-full"
              style={{
                width: `${100}%`,
                height: `${100}%`,
                border: `${3 - i * 0.5}px solid rgba(252, 165, 165, ${0.7 - i * 0.1})`,
                animation: `ring-pulse 2.2s ease-out ${delay}s infinite`,
                opacity: 0,
              }}
            />
          ))}

          {/* Center circle */}
          <div className="relative z-10 w-28 h-28 rounded-full bg-red-900 border-4 border-red-300 flex flex-col items-center justify-center shadow-2xl">
            <span className="text-white text-5xl leading-none select-none">⚠</span>
            <span className="text-red-300 text-xs font-bold tracking-widest mt-1">ALERT</span>
          </div>
        </div>

        {/* Warning text */}
        <div className="text-center max-w-xs px-2">
          <p className="text-white text-xl font-bold leading-relaxed mb-3" dir="rtl" lang="ar">
            أنت على بعد 500 متر من المبنى المستهدف
          </p>
          <p className="text-red-200 text-base leading-relaxed">
            You are within 500m from the targeted building
          </p>
          <p className="text-red-300 text-xs mt-3 font-medium" dir="rtl">
            ابتعد عن المنطقة فوراً — Evacuate the area immediately
          </p>
        </div>

        {/* Action buttons */}
        <div className="w-full space-y-3 mt-auto">
          <button
            onClick={async () => {
              const phone = localStorage.getItem('salmak_phone') ?? '';
              setSafeLoading(true);
              try {
                await markSafe(phone);
                localStorage.removeItem('salmak_alert_data');
                router.push('/home');
              } catch {
                setSafeLoading(false);
              }
            }}
            disabled={safeLoading}
            className="flex items-center justify-center gap-4 w-full bg-green-500 hover:bg-green-600 active:bg-green-700 disabled:opacity-60 text-white font-bold py-5 rounded-2xl shadow-xl transition-all duration-150"
          >
            {safeLoading ? (
              <span className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            ) : (
              <span className="text-2xl">✅</span>
            )}
            <span>
              <span className="block text-base" dir="rtl">أنا بأمان</span>
              <span className="block text-xs font-normal text-green-100">I&apos;m Safe</span>
            </span>
          </button>
          <a
            href="tel:+9611140140"
            className="flex items-center justify-center gap-4 w-full bg-white text-red-600 font-bold py-5 rounded-2xl shadow-xl hover:bg-red-50 active:scale-98 transition-all duration-150"
          >
            <span className="text-2xl">📞</span>
            <span>
              <span className="block text-base" dir="rtl">اتصل بالصليب الأحمر</span>
              <span className="block text-xs font-normal text-red-400">Call Lebanese Red Cross</span>
            </span>
          </a>

          {evacuationRoute ? (
            <a
              href={evacuationRoute}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-4 w-full bg-red-800 hover:bg-red-900 active:scale-98 text-white font-bold py-5 rounded-2xl shadow-xl transition-all duration-150"
            >
              <span className="text-2xl">🗺️</span>
              <span>
                <span className="block text-base" dir="rtl">أسرع طريق للإخلاء</span>
                <span className="block text-xs font-normal text-red-300">Find Fastest Route</span>
              </span>
            </a>
          ) : (
            <button
              disabled
              className="flex items-center justify-center gap-4 w-full bg-red-800 opacity-60 text-white font-bold py-5 rounded-2xl cursor-not-allowed"
            >
              <span className="text-2xl">🗺️</span>
              <span>
                <span className="block text-base" dir="rtl">أسرع طريق للإخلاء</span>
                <span className="block text-xs font-normal text-red-300">Find Fastest Route</span>
              </span>
            </button>
          )}
        </div>
      </div>
    </main>
  );
}
