'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import Logo from '@/components/Logo';
import type { MapLocation } from '@/components/MapPicker';
import { registerUser } from '@/lib/api';

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

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export default function SetupPage() {
  const router = useRouter();
  const [location, setLocation] = useState<MapLocation | null>(null);
  const [name, setName] = useState('');
  const [emergencyContact, setEmergencyContact] = useState('');
  const [peopleCount, setPeopleCount] = useState('1');
  const [idPhoto, setIdPhoto] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // GPS state
  const [gpsLoading, setGpsLoading] = useState(false);
  const [gpsError, setGpsError] = useState('');

  // Manual coordinate inputs
  const [latInput, setLatInput] = useState('');
  const [lngInput, setLngInput] = useState('');
  const [manualError, setManualError] = useState('');

  const applyLocation = (lat: number, lng: number) => {
    setLocation({ lat, lng });
    setLatInput(lat.toFixed(6));
    setLngInput(lng.toFixed(6));
    setGpsError('');
    setManualError('');
  };

  const handleUseMyLocation = () => {
    if (!navigator.geolocation) {
      setGpsError('خدمة الموقع غير متاحة — Geolocation not supported on this device');
      return;
    }
    setGpsLoading(true);
    setGpsError('');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        applyLocation(pos.coords.latitude, pos.coords.longitude);
        setGpsLoading(false);
      },
      () => {
        setGpsError('تعذّر الوصول إلى الموقع — Could not get your location. Check permissions.');
        setGpsLoading(false);
      },
      { timeout: 10_000, enableHighAccuracy: true }
    );
  };

  const handleSetManualLocation = () => {
    const lat = parseFloat(latInput);
    const lng = parseFloat(lngInput);
    if (isNaN(lat) || isNaN(lng)) {
      setManualError('أدخل أرقاماً صحيحة — Enter valid numbers');
      return;
    }
    if (lat < -90 || lat > 90) {
      setManualError('خط العرض بين -90 و 90 — Latitude must be between -90 and 90');
      return;
    }
    if (lng < -180 || lng > 180) {
      setManualError('خط الطول بين -180 و 180 — Longitude must be between -180 and 180');
      return;
    }
    applyLocation(clamp(lat, -90, 90), clamp(lng, -180, 180));
  };

  const handleSave = async () => {
    if (!name.trim()) {
      setError('الرجاء إدخال اسمك الكامل — Please enter your full name');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const phoneNumber = localStorage.getItem('salmak_phone') ?? '';
      const formattedContact = emergencyContact
        ? `+961${emergencyContact.replace(/^0+/, '').replace(/\s/g, '')}`
        : null;

      await registerUser({
        phoneNumber,
        name: name.trim(),
        lat: location?.lat ?? null,
        lng: location?.lng ?? null,
        emergencyContact: formattedContact,
        peopleInHouse: parseInt(peopleCount),
      });

      localStorage.setItem(
        'salmak_user_data',
        JSON.stringify({ name: name.trim(), emergencyContact: formattedContact, location })
      );
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
              Pin your home location on the map
            </p>

            {/* Map tile */}
            <div className="rounded-2xl overflow-hidden border-2 border-gray-100">
              <MapPicker
                location={location}
                onLocationSelect={(lat, lng) => applyLocation(lat, lng)}
              />
            </div>

            {/* Pin status */}
            {location ? (
              <p className="text-xs text-sky-500 mt-2 text-center font-medium">
                ✓ موقع محدد — {location.lat.toFixed(5)}, {location.lng.toFixed(5)}
              </p>
            ) : (
              <p className="text-xs text-gray-300 mt-2 text-center">
                اضغط على الخريطة لتحديد الموقع / Tap the map to place a pin
              </p>
            )}

            {/* ── Option 1: GPS ── */}
            <button
              type="button"
              onClick={handleUseMyLocation}
              disabled={gpsLoading}
              className="mt-4 w-full flex items-center justify-center gap-2.5 border-2 border-sky-300 text-sky-600 bg-sky-50 hover:bg-sky-100 active:bg-sky-200 disabled:opacity-60 font-semibold py-3 rounded-2xl transition-colors duration-200 text-sm"
            >
              {gpsLoading ? (
                <>
                  <span className="w-4 h-4 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
                  <span>جارٍ تحديد الموقع... — Getting location...</span>
                </>
              ) : (
                <>
                  <span className="text-base">📡</span>
                  <span>
                    <span dir="rtl" className="block leading-tight">استخدم موقعي الحالي</span>
                    <span className="block text-xs font-normal text-sky-400 leading-tight">
                      Use my current location
                    </span>
                  </span>
                </>
              )}
            </button>

            {gpsError && (
              <p className="text-red-400 text-xs mt-2 text-center leading-relaxed">{gpsError}</p>
            )}

            {/* ── Divider ── */}
            <div className="flex items-center gap-3 my-4">
              <div className="flex-1 h-px bg-gray-100" />
              <span className="text-gray-300 text-xs font-medium">أو / or</span>
              <div className="flex-1 h-px bg-gray-100" />
            </div>

            {/* ── Option 2: Manual coordinates ── */}
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-2 text-center" dir="rtl">
                إدخال الإحداثيات يدوياً
                <span className="font-normal text-gray-400"> — Enter coordinates manually</span>
              </p>
              <div className="flex gap-2">
                <div className="flex-1">
                  <label className="block text-xs text-gray-400 mb-1 text-center">
                    خط العرض / Lat
                  </label>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={latInput}
                    onChange={(e) => { setLatInput(e.target.value); setManualError(''); }}
                    placeholder="33.8938"
                    className="w-full border-2 border-gray-200 rounded-xl px-3 py-2.5 text-center text-sm font-mono outline-none focus:border-sky-400 transition-colors text-gray-700"
                    dir="ltr"
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-xs text-gray-400 mb-1 text-center">
                    خط الطول / Lng
                  </label>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={lngInput}
                    onChange={(e) => { setLngInput(e.target.value); setManualError(''); }}
                    placeholder="35.5018"
                    className="w-full border-2 border-gray-200 rounded-xl px-3 py-2.5 text-center text-sm font-mono outline-none focus:border-sky-400 transition-colors text-gray-700"
                    dir="ltr"
                  />
                </div>
              </div>

              {manualError && (
                <p className="text-red-400 text-xs mt-1.5 text-center">{manualError}</p>
              )}

              <button
                type="button"
                onClick={handleSetManualLocation}
                className="mt-2.5 w-full border-2 border-gray-200 text-gray-600 bg-gray-50 hover:bg-gray-100 font-semibold py-2.5 rounded-xl transition-colors duration-200 text-sm"
              >
                <span dir="rtl">تحديد الموقع</span>
                <span className="text-gray-400 font-normal"> — Set Location</span>
              </button>
            </div>
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
