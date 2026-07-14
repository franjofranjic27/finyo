/* FOUC guard: apply the theme class before the app bundle loads.
   External file (not inline) so the CSP can stay at script-src 'self'. */
(function () {
  var stored = null;
  try {
    stored = localStorage.getItem('finyo-theme');
  } catch (error) {
    /* localStorage can be unavailable (e.g. blocked storage) — fall back to system. */
  }
  var dark =
    stored === 'dark' ||
    (stored !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.add(dark ? 'dark' : 'light');
})();
