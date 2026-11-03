// Applies the stored theme before first paint to avoid a flash of the wrong theme.
// A separate file (not an inline script) so the Content-Security-Policy can be script-src 'self'.
(function () {
  try {
    var t = localStorage.getItem("cf-theme");
    var dark = t === "dark" || ((t === null || t === "system") && matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.classList.toggle("dark", dark);
  } catch (e) {
    // Storage blocked: fall back to the light theme until the app starts.
  }
})();
