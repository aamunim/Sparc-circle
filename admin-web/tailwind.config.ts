import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', '"Noto Sans Bengali"', 'system-ui', '-apple-system', 'sans-serif'],
        bengali: ['"Noto Sans Bengali"', 'Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        sparc: {
          emerald: '#047857',
          emeraldLight: '#ECFDF5',
          indigo: '#3730A3',
          indigoLight: '#EEF2FF',
          slateInk: '#0F172A',
          paper: '#F8FAFC',
        },
      },
    },
  },
  plugins: [],
};

export default config;
