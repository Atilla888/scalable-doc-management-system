/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: 'var(--color-primary)',
        secondary: 'var(--color-secondary)',
        accent: 'var(--color-accent)',
        background: 'var(--color-background)',
        surface: 'var(--color-surface)',
        border: 'var(--border)',
        text: {
          DEFAULT: 'var(--text-primary)',
          secondary: 'var(--text-secondary)'
        },
        error: 'var(--color-error)',
        warning: 'var(--color-warning)'
      }
    },
  },
  plugins: [],
};
