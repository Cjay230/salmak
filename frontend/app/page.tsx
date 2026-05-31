'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Logo from '@/components/Logo';
import { sendVerificationCode, verifyCode } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState<'phone' | 'code'>('phone');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fullPhone = `+961${phone.replace(/^0+/, '').replace(/\s/g, '')}`;

  const handleSendCode = async () => {
    if (!phone.trim()) {
      setError('الرجاء إدخال رقم الهاتف — Please enter your phone number');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await sendVerificationCode(fullPhone);
      setStep('code');
    } catch {
      setError('فشل إرسال الرمز — Failed to send code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    if (!code.trim() || code.length < 4) {
      setError('الرجاء إدخال رمز التحقق — Please enter the verification code');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const result = await verifyCode(fullPhone, code);
      localStorage.setItem('salmak_phone', fullPhone);
      if (result.isNewUser) {
        router.push('/setup');
      } else {
        router.push('/home');
      }
    } catch {
      setError('رمز غير صحيح — Invalid code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-sky-50 flex flex-col items-center justify-center p-5">
      <div className="w-full max-w-sm">
        <Logo className="mb-10" />

        <div className="bg-white rounded-3xl shadow-xl shadow-sky-100 p-8">
          {step === 'phone' ? (
            <>
              <div className="mb-7 text-center">
                <p className="text-gray-700 font-semibold text-base" dir="rtl">
                  أدخل رقم هاتفك
                </p>
                <p className="text-gray-400 text-sm mt-0.5">Enter your phone number</p>
              </div>

              <div className="mb-5">
                <div className="flex items-center border-2 border-gray-200 rounded-2xl overflow-hidden focus-within:border-sky-400 transition-colors duration-200">
                  <span className="bg-gray-50 px-4 py-4 text-gray-500 font-medium text-sm border-r border-gray-200 whitespace-nowrap">
                    🇱🇧 +961
                  </span>
                  <input
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSendCode()}
                    placeholder="70 123 456"
                    className="flex-1 px-4 py-4 text-gray-800 outline-none bg-transparent text-base"
                    dir="ltr"
                    maxLength={10}
                    autoFocus
                  />
                </div>
              </div>

              {error && (
                <p className="text-red-500 text-xs mb-4 text-center leading-relaxed">{error}</p>
              )}

              <button
                onClick={handleSendCode}
                disabled={loading}
                className="w-full bg-sky-500 hover:bg-sky-600 active:bg-sky-700 disabled:bg-sky-300 text-white font-semibold py-4 rounded-2xl transition-colors duration-200 text-base"
              >
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    جارٍ الإرسال...
                  </span>
                ) : (
                  'أرسل الرمز — Send Code'
                )}
              </button>
            </>
          ) : (
            <>
              <div className="mb-7 text-center">
                <div className="w-12 h-12 bg-sky-100 rounded-full flex items-center justify-center mx-auto mb-3">
                  <span className="text-2xl">📱</span>
                </div>
                <p className="text-gray-700 font-semibold text-base" dir="rtl">
                  أدخل رمز التحقق
                </p>
                <p className="text-gray-400 text-sm mt-0.5">
                  Enter the code sent to {fullPhone}
                </p>
              </div>

              <div className="mb-5">
                <input
                  type="text"
                  inputMode="numeric"
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  onKeyDown={(e) => e.key === 'Enter' && handleVerify()}
                  placeholder="• • • • • •"
                  className="w-full border-2 border-gray-200 rounded-2xl px-4 py-4 text-center text-3xl font-mono tracking-[0.5em] outline-none focus:border-sky-400 transition-colors duration-200 text-gray-800"
                  dir="ltr"
                  maxLength={6}
                  autoFocus
                />
              </div>

              {error && (
                <p className="text-red-500 text-xs mb-4 text-center leading-relaxed">{error}</p>
              )}

              <button
                onClick={handleVerify}
                disabled={loading}
                className="w-full bg-sky-500 hover:bg-sky-600 active:bg-sky-700 disabled:bg-sky-300 text-white font-semibold py-4 rounded-2xl transition-colors duration-200 text-base"
              >
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    جارٍ التحقق...
                  </span>
                ) : (
                  'تحقق — Verify'
                )}
              </button>

              <button
                onClick={() => {
                  setStep('phone');
                  setError('');
                  setCode('');
                }}
                className="w-full mt-3 text-sky-500 hover:text-sky-600 text-sm py-2 transition-colors"
              >
                تغيير الرقم — Change number
              </button>
            </>
          )}
        </div>

        <p className="text-center text-gray-300 text-xs mt-8 leading-relaxed">
          نظام الإنذار المبكر اللبناني
          <br />
          Lebanese Early Warning System
        </p>
      </div>
    </main>
  );
}
