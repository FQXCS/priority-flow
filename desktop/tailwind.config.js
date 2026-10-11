/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: {
          light: '#f6f7f9',
          dark: '#17181c',
        },
      },
    },
  },
  plugins: [],
};
