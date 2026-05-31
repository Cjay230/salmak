import type { Metadata } from 'next';
import { Noto_Sans_Arabic } from 'next/font/google';
import './globals.css';

const notoSansArabic = Noto_Sans_Arabic({
  subsets: ['arabic'],
  weight: ['400', '600', '700'],
  variable: '--font-arabic',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'سلمك — Salmak',
  description: 'Lebanese Emergency Alert System | نظام الإنذار المبكر اللبناني',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ar" className={notoSansArabic.variable}>
      <body className="font-arabic antialiased">{children}</body>
    </html>
  );
}
