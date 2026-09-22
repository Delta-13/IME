import palettes from './palettes.json';

export type ThemeMode = 'system' | 'light' | 'dark';
export type ThemeColors = typeof palettes.light;
export const themes: Record<'light' | 'dark', ThemeColors> = palettes;
