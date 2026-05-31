import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './pages/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
    './app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        arabic: ['var(--font-arabic)', 'system-ui', 'sans-serif'],
      },
      colors: {
        'salmak-blue': '#EBF5FB',
        'salmak-blue-mid': '#AED6F1',
      },
      keyframes: {
        'ring-pulse': {
          '0%': { transform: 'scale(0.3)', opacity: '0.8' },
          '100%': { transform: 'scale(1.6)', opacity: '0' },
        },
      },
      animation: {
        'ring-pulse': 'ring-pulse 2.2s ease-out infinite',
      },
    },
  },
  plugins: [],
};

export default config;
