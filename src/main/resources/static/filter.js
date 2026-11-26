(() => {
  'use strict';

  const toggle = document.getElementById('real-only');
  if (!toggle) return;
  const hint = document.getElementById('real-only-hint');
  const empty = document.getElementById('real-only-empty');

  function hasDemoBadge(node) {
    if (!node) return false;
    return Array.from(node.querySelectorAll('.badge'))
        .some(badge => /^ДЕМО/i.test(badge.textContent.trim()));
  }

  function apply() {
    const onlyReal = toggle.checked;
    let rows = 0;
    let visibleRows = 0;
    let columns = 0;
    let specColumns = 0;

    document.querySelectorAll('table.build-offer-table').forEach(table => {
      table.querySelectorAll('tbody tr').forEach(row => {
        const hide = onlyReal && (row.classList.contains('is-demo') || hasDemoBadge(row));
        row.hidden = hide;
        if (hide) rows++; else visibleRows++;
      });
    });

    const spec = document.querySelector('table.spec-table');
    if (spec) {
      const demoColumns = new Set();
      spec.querySelectorAll('thead th').forEach((th, index) => {
        specColumns++;
        if (hasDemoBadge(th)) demoColumns.add(index);
      });
      spec.querySelectorAll('thead th').forEach((th, index) => {
        th.hidden = onlyReal && demoColumns.has(index);
      });
      spec.querySelectorAll('tbody tr').forEach(row => {
        Array.from(row.children).forEach((cell, index) => {
          cell.hidden = onlyReal && demoColumns.has(index);
        });
      });
      columns = demoColumns.size;
    }

    if (hint) {
      hint.textContent = onlyReal
          ? 'Показаны только проверяемые цены: скрыто демо-строк ' + rows
              + ', демо-колонок ' + columns + '.'
          : 'Демо-цены видны и помечены бейджем ДЕМО; ссылка ведёт на реальный поиск.';
    }
    if (empty) {
      const nothingLeft = onlyReal
          && ((rows > 0 && visibleRows === 0) || (specColumns > 0 && columns === specColumns));
      empty.hidden = !nothingLeft;
    }
  }

  toggle.addEventListener('change', apply);
  apply();
})();
