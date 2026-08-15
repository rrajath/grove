// Shared read/write/toggle for Grove's theme state. Used by the landing
// page's buffer-bar pill and the Starlight docs ThemeSelect override, so
// both toggles manipulate the exact same localStorage key + attribute set
// by theme-boot.js.
export const GROVE_THEME_KEY = 'grove-theme';

export function getGroveTheme() {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

export function setGroveTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  try {
    localStorage.setItem(GROVE_THEME_KEY, theme);
  } catch (e) {
    // localStorage unavailable (private browsing, etc.) — theme still applies for this page view.
  }
}

export function toggleGroveTheme() {
  const next = getGroveTheme() === 'light' ? 'dark' : 'light';
  setGroveTheme(next);
  return next;
}

// Wires up every `[data-grove-theme-toggle]` button on the page: label reads
// "grove-<destination>-theme", click flips the shared theme. Used by both
// the landing page's buffer bar and the Starlight ThemeSelect override.
export function wireThemeTogglePills(selector = '[data-grove-theme-toggle]') {
  function syncLabel(button) {
    const label = button.querySelector('[data-grove-theme-label]');
    const destination = getGroveTheme() === 'light' ? 'dark' : 'light';
    if (label) label.textContent = `grove-${destination}-theme`;
  }

  document.querySelectorAll(selector).forEach((button) => {
    syncLabel(button);
    button.addEventListener('click', () => {
      toggleGroveTheme();
      syncLabel(button);
    });
  });
}
