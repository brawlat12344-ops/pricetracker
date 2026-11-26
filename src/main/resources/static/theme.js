(() => {
  'use strict';

  const root = document.documentElement;
  const button = document.getElementById('theme-toggle');
  const metaColor = document.getElementById('theme-color');
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const KEY = 'theme';

  let stored = null;
  try {
    const value = localStorage.getItem(KEY);
    if (value === 'light' || value === 'dark') stored = value;
  } catch (error) {
    // Приватный режим или file:// — тема продолжает работать без хранилища.
  }

  // Явный выбор пользователя важнее системной темы.
  let hasExplicitChoice = stored !== null;

  function apply(theme, save) {
    root.dataset.theme = theme;
    const dark = theme === 'dark';

    button.setAttribute('aria-pressed', String(dark));
    button.setAttribute('aria-label', dark ? 'Включить светлую тему' : 'Включить тёмную тему');
    button.title = dark ? 'Светлая тема' : 'Тёмная тема';
    metaColor.setAttribute('content', dark ? '#0f1115' : '#f6f7fb');

    if (save) {
      hasExplicitChoice = true;
      try { localStorage.setItem(KEY, theme); } catch (error) { /* без хранилища */ }
    }
  }

  apply(stored || (media.matches ? 'dark' : 'light'), false);

  button.addEventListener('click', () => {
    apply(root.dataset.theme === 'dark' ? 'light' : 'dark', true);
  });

  const onSystemChange = (event) => {
    if (!hasExplicitChoice) apply(event.matches ? 'dark' : 'light', false);
  };

  if (media.addEventListener) {
    media.addEventListener('change', onSystemChange);
  } else {
    media.addListener(onSystemChange);
  }
})();
