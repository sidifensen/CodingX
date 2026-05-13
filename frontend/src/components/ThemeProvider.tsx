import React, { createContext, useContext, useEffect, useState } from "react";

/**
 * Restricts the UI theme to the supported light and dark variants.
 */
type Theme = "dark" | "light";

/**
 * Describes the props accepted by ThemeProvider.
 */
interface ThemeProviderProps {
  children: React.ReactNode;
}

/**
 * Defines the theme state and actions exposed through context.
 */
interface ThemeProviderState {
  theme: Theme;
  toggleTheme: () => void;
}

const initialState: ThemeProviderState = {
  theme: "dark",
  toggleTheme: () => null,
};

const ThemeProviderContext = createContext<ThemeProviderState>(initialState);

/**
 * Provides the current theme and synchronizes the document root theme class.
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setTheme] = useState<Theme>("dark");

  useEffect(() => {
    // Keep the root element aligned with the current theme so Tailwind variants respond correctly.
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");
    if (theme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.add("light");
    }
  }, [theme]);

  const toggleTheme = () => {
    // Flip between the only two supported theme modes.
    setTheme(prev => prev === "dark" ? "light" : "dark");
  };

  return (
    <ThemeProviderContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeProviderContext.Provider>
  );
}

/**
 * Returns the current theme context and guards against invalid usage.
 */
export const useTheme = () => {
  const context = useContext(ThemeProviderContext);
  if (context === undefined)
    throw new Error("useTheme must be used within a ThemeProvider");
  return context;
};
