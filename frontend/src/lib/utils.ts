import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merges conditional class names and resolves Tailwind conflicts in one call.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
