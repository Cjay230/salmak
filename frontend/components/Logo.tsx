interface LogoProps {
  className?: string;
  variant?: 'default' | 'light';
}

export default function Logo({ className = '', variant = 'default' }: LogoProps) {
  const arabicColor = variant === 'light' ? 'text-white' : 'text-sky-700';
  const latinColor = variant === 'light' ? 'text-sky-200' : 'text-sky-500';

  return (
    <div className={`text-center select-none ${className}`}>
      <h1
        className={`text-6xl font-bold leading-tight ${arabicColor}`}
        dir="rtl"
        lang="ar"
      >
        سلمك
      </h1>
      <p className={`text-sm font-semibold tracking-[0.35em] uppercase mt-0.5 ${latinColor}`}>
        Salmak
      </p>
    </div>
  );
}
