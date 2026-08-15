// Blocking boot script, inlined into <head> on both the landing page and the
// Starlight docs (via the ThemeProvider override) so theme state is shared
// across the whole site and there is never a flash of the wrong theme.
// Precedence: localStorage > OS preference (prefers-color-scheme) > light.
export const THEME_BOOT_SCRIPT = `(function () {
  var KEY = 'grove-theme';
  var stored = null;
  try { stored = localStorage.getItem(KEY); } catch (e) {}
  var theme = stored === 'dark' || stored === 'light'
    ? stored
    : (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', theme);
})();`;
