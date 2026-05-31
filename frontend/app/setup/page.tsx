'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import Logo from '@/components/Logo';

const MapPicker = dynamic(() => import('@/components/MapPicker'), {
  ssr: false,
  loading: () => (
    <div className="h-60 bg-sky-50 rounded-2xl flex flex-col items-center justify-center border-2 border-dashed border-sky-200">
      <span className="text-2xl mb-2">🗺️</span>
      <p className="text-gray-400 text-sm">جارٍ تحميل الخريطة...</p>
      <p className="text-gray-300 text-xs">Loading map...</p>
    </div>
  ),
});

export default function SetupPage() {
  const router = useRouter();
  const [location, setLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [name, setName] = useState('');
  const [emergencyContact, setEmergencyContact] = useState('');
  const [peopleCount, setPeopleCount] = useState('1');
  const [idPhoto, setIdPhoto] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSave = async () => {
    if (!name.trim()) {
      setError('الرجاء إدخال اسمك الكامل — Please enter your full name');
      return;
    }
    setLoading(true);
    try {
      const userData = {
        name: name.trim(),
        emergencyContact: emergencyContact
          ? `+961${emergencyContact.replace(/^0+/, '').replace(/\s/g, '')}`
          : null,
        peopleCount: parseInt(peopleCount),
        location,
      };
      localStorage.setItem('salmak_user_data', JSON.stringify(userData));
      router.push('/home');
    } catch {
      setError('فشل حفظ البيانات — Failed to save. Please try again.');
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-sky-50 py-10 px-5">
      <div className="max-w-sm mx-auto">
        <Logo className="mb-8" />

        <div className="mb-5 text-center">
          <h2 className="text-gray-700 font-bold text-lg" dir="rtl">
            أخبرنا عنك
          </h2>
          <p className="text-gray-400 text-sm mt-0.5">Tell us about yourself</p>
        </div>

        <div className="bg-white rounded-3xl shadow-xl shadow-sky-100 p-7 space-y-6">
          {/* Map */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1" dir="rtl">
              📍 حدد موقع منزلك
            </label>
            <p className="text-gray-400 text-xs mb-2">
              Tap on the map to pin your home location
            </p>
            <div className="rounded-2xl overflow-hidden border-2 border-gray-100">
              <MapPicker onLocationSelect={(lat, lng) => setLocation({ lat, lng })} />
            </div>
            {location ? (
              <p className="text-xs text-sky-500 mt-2 text-center font-medium">
                ✓ موقع محدد — {location.lat.toFixed(4)}, {location.lng.toFixed(4)}
              </p>
            ) : (
              <p className="text-xs text-gray-300 mt-2 text-center">اضغط على الخريطة / Tap the map</p>
            )}
          </div>

          {/* Full name */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-2" dir="rtl">
              الاسم الكامل *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="الاسم الكامل — Full name"
              className="w-full border-2 border-gray-200 rounded-2xl px-4 py-3.5 outline-none focus:border-sky-400 transition-colors text-gray-800 text-sm"
              dir="rtl"
            />
          </div>

          {/* Emergency contact */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1" dir="rtl">
              رقم جهة الاتصال الطارئة
            </label>
            <p className="text-gray-400 text-xs mb-2">Emergency contact number</p>
            <div className="flex items-center border-2 border-gray-200 rounded-2xl overflow-hidden focus-within:border-sky-400 transition-colors">
              <span className="bg-gray-50 px-3 py-3.5 text-gray-500 text-xs border-r border-gray-200 whitespace-nowrap">
                🇱🇧 +961
              </span>
              <input
                type="tel"
                value={emergencyContact}
                onChange={(e) => setEmergencyContact(e.target.value)}
                placeholder="70 123 456"
                className="flex-1 px-4 py-3.5 outline-none bg-transparent text-gray-800 text-sm"
                dir="ltr"
              />
            </div>
          </div>

          {/* People count */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1" dir="rtl">
              عدد الأشخاص في المنزل
            </label>
            <p className="text-gray-400 text-xs mb-2">Number of people in your household</p>
            <div className="flex gap-2 flex-wrap">
              {[1, 2, 3, 4, 5, 6, 7, 8, '9+'].map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => setPeopleCount(String(n))}
                  className={`w-11 h-11 rounded-xl text-sm font-semibold transition-colors duration-150 ${
                    peopleCount === String(n)
                      ? 'bg-sky-500 text-white shadow-md shadow-sky-200'
                      : 'bg-gray-100 text-gray-600 hover:bg-sky-50'
                  }`}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>

          {/* ID photo */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1" dir="rtl">
              صورة الهوية{' '}
              <span className="font-normal text-gray-400">(اختياري)</span>
            </label>
            <p className="text-gray-400 text-xs mb-2">ID photo upload (optional)</p>
            <label className="flex flex-col items-center justify-center w-full h-20 border-2 border-dashed border-gray-200 rounded-2xl cursor-pointer hover:border-sky-300 hover:bg-sky-50 transition-all duration-200">
              <span className="text-xl mb-1">{idPhoto ? '✅' : '📎'}</span>
              <span className="text-xs text-gray-400">
                {idPhoto ? idPhoto.name : 'اضغط لرفع صورة — Tap to upload'}
              </span>
              <input
                type="file"
                accept="image/*"
                capture="environment"
                className="hidden"
                onChange={(e) => setIdPhoto(e.target.files?.[0] ?? null)}
              />
            </label>
          </div>

          {error && (
            <p className="text-red-500 text-xs text-center leading-relaxed">{error}</p>
          )}

          <button
            onClick={handleSave}
            disabled={loading}
            className="w-full bg-sky-500 hover:bg-sky-600 active:bg-sky-700 disabled:bg-sky-300 text-white font-semibold py-4 rounded-2xl transition-colors duration-200 text-base"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                جارٍ الحفظ...
              </span>
            ) : (
              'حفظ — Save'
            )}
          </button>
        </div>
      </div>
    </main>
  );
}
