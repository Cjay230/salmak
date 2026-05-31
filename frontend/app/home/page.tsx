'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Logo from '@/components/Logo';
import { getAlert } from '@/lib/api';

export default function HomePage() {
  const router = useRouter();
  const [lastChecked, setLastChecked] = useState<Date | null>(null);
  const [phone, setPhone] = useState('');
  const [checking, setChecking] = useState(false);
  const [countdown, setCountdown] = useState(30);

  const checkAlert = useCallback(
    async (phoneNumber: string) => {
      setChecking(true);
      try {
        const data = await getAlert(phoneNumber);
        setLastChecked(new Date());
        setCountdown(30);
        if (data.hasAlert) {
          localStorage.setItem('salmak_alert_data', JSON.stringify(data));
          router.push('/alert');
        }
      } catch {
        setLastChecked(new Date());
        setCountdown(30);
      } finally {
        setChecking(false);
      }
    },
    [router]
  );

  useEffect(() => {
    const storedPhone = localStorage.getItem('salmak_phone');
    if (!storedPhone) {
      router.push('/');
      return;
    }
    setPhone(storedPhone);
    checkAlert(storedPhone);

    const pollInterval = setInterval(() => checkAlert(storedPhone), 30_000);
    const countdownInterval = setInterval(() => {
      setCountdown((c) => (c > 0 ? c - 1 : 30));
    }, 1_000);

    return () => {
      clearInterval(pollInterval);
      clearInterval(countdownInterval);
    };
  }, [router, checkAlert]);

  const formatTime = (date: Date) =>
    date.toLocaleTimeString('ar-LB', { hour: '2-digit', minute: '2-digit' });

  const userData = (() => {
    try {
      const raw = localStorage.getItem('salmak_user_data');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  })();

  return (
    <main className="min-h-screen bg-sky-50 flex flex-col items-center px-5 pt-14 pb-8">
      <div className="w-full max-w-sm flex flex-col min-h-[calc(100vh-5rem)]">
        <Logo className="mb-10" />

        {/* Greeting */}
        {userData?.name && (
          <p className="text-center text-sky-600 text-sm font-medium mb-5" dir="rtl">
            أهلاً، {userData.name} 👋
          </p>
        )}

        {/* Safe status card */}
        <div className="bg-white rounded-3xl shadow-xl shadow-sky-100 p-8 mb-5 text-center">
          <div className="w-20 h-20 bg-green-50 rounded-full flex items-center justify-center mx-auto mb-5 border-4 border-green-100">
            <span className="text-4xl">✓</span>
          </div>

          <div className="inline-flex items-center gap-2.5 bg-green-500 text-white px-7 py-2.5 rounded-full mb-4 shadow-md shadow-green-200">
            <span className="font-bold text-base tracking-wide">آمن — SAFE</span>
          </div>

          <p className="text-gray-600 font-medium text-sm" dir="rtl">
            لا تحذيرات في منطقتك
          </p>
          <p className="text-gray-400 text-xs mt-0.5">No warnings in your area</p>

          {lastChecked && (
            <p className="text-gray-300 text-xs mt-5">
              آخر تحقق {formatTime(lastChecked)} — Last check
            </p>
          )}
        </div>

        {/* Polling status */}
        <div className="flex items-center justify-center gap-2 mb-8">
          {checking ? (
            <>
              <span className="w-2 h-2 bg-sky-400 rounded-full animate-ping" />
              <p className="text-gray-400 text-xs">جارٍ التحقق... — Checking...</p>
            </>
          ) : (
            <>
              <span className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
              <p className="text-gray-400 text-xs">
                التحقق التالي خلال {countdown}ث — Next check in {countdown}s
              </p>
            </>
          )}
        </div>

        {/* Action buttons — pushed to bottom */}
        <div className="space-y-3 mt-auto">
          <a
            href="tel:+9611140140"
            className="flex items-center justify-center gap-3 w-full bg-red-500 hover:bg-red-600 active:bg-red-700 text-white font-semibold py-4 rounded-2xl text-center transition-colors duration-200 shadow-lg shadow-red-100"
          >
            <span className="text-xl">📞</span>
            <span>
              <span className="block text-sm" dir="rtl">اتصل للمساعدة</span>
              <span className="block text-xs font-normal opacity-80">Call for Help</span>
            </span>
          </a>

          <button
            disabled
            className="flex items-center justify-center gap-3 w-full bg-gray-100 text-gray-400 font-semibold py-4 rounded-2xl cursor-not-allowed"
          >
            <span className="text-xl opacity-50">🏠</span>
            <span>
              <span className="block text-sm" dir="rtl">إيجاد مسكن</span>
              <span className="block text-xs font-normal">Find Housing — قريباً</span>
            </span>
          </button>
        </div>
      </div>
    </main>
  );
}
