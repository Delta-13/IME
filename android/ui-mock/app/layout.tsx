import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ToneIME Android UI Mock",
  description: "ToneIME Android 圆角界面交互原型",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
