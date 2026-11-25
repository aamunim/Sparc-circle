import type { Metadata } from "next";
import { Inter, Noto_Sans_Bengali } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const notoBn = Noto_Sans_Bengali({
  subsets: ["bengali"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-noto-bn",
});

export const metadata: Metadata = {
  title: "SPARC Study Circle — Admin & Supervisor Portal (v2.0)",
  description:
    "বই পড়তে পড়তে লেখা শেখা — SPARC Study Circle একাডেমিক অ্যাডমিন, সুপারভাইজার ও প্রতিনিধি প্যানেল",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="bn">
      <body className={`${inter.variable} ${notoBn.variable} font-sans antialiased`}>
        {children}
      </body>
    </html>
  );
}
