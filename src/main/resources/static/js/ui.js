// Небольшие улучшения интерфейса. Все данные приходят с сервера; без JS страницы тоже работают
// (кроме автоотправки - для неё в формах есть обычная кнопка).
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  /* ---------- Toast: показать сообщение и спрятать через пару секунд ---------- */
  const toastEl = $('#toast');
  let toastTimer;
  const toast = msg => {
    if (!toastEl) return;
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2600);
  };
  if (toastEl && toastEl.classList.contains('show')) {
    toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2600);
  }

  /* ---------- Автоотправка формы при изменении поля (фильтры, флажки видимости) ---------- */
  document.addEventListener('change', e => {
    const form = e.target.closest('form[data-autosubmit]');
    if (form) form.requestSubmit ? form.requestSubmit() : form.submit();
  });
  // Кнопка "Применить" нужна только без JS
  $$('form[data-autosubmit] [data-nojs]').forEach(el => { el.hidden = true; });

  /* ---------- Подтверждение опасных действий ---------- */
  document.addEventListener('submit', e => {
    const form = e.target.closest('form[data-confirm]');
    if (form && !window.confirm(form.dataset.confirm)) e.preventDefault();
  });

  /* ---------- Копирование ссылки-приглашения ---------- */
  $$('[data-copy]').forEach(btn => btn.addEventListener('click', () => {
    const text = btn.dataset.copy;
    const done = () => toast('Ссылка скопирована');
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(done, () => toast(text));
    } else {
      toast(text);
    }
  }));

  /* ---------- Форма операции: поля зависят от типа, подсказка видимости под счётом ---------- */
  $$('form[data-op-form]').forEach(form => {
    const typeInputs = $$('input[name="type"]', form);
    const categoryField = $('[data-field="category"]', form);
    const targetField = $('[data-field="target"]', form);
    const account = $('select[name="accountId"]', form);
    const note = $('[data-vis-note]', form);

    const applyType = () => {
      const checked = typeInputs.find(i => i.checked);
      const type = checked ? checked.value : 'EXPENSE';
      if (targetField) targetField.hidden = type !== 'TRANSFER';
      if (categoryField) {
        categoryField.hidden = type === 'TRANSFER';
        // В списке категорий показываем только подходящие по типу
        const select = $('select', categoryField);
        let firstVisible = null;
        $$('option[data-type]', select).forEach(o => {
          const show = o.dataset.type === type;
          o.hidden = !show;
          o.disabled = !show;
          if (show && !firstVisible) firstVisible = o;
        });
        const current = select.selectedOptions[0];
        if ((!current || current.disabled) && firstVisible) select.value = firstVisible.value;
      }
    };

    const applyVisibility = () => {
      if (!note || !account) return;
      const option = account.selectedOptions[0];
      const names = note.dataset.familyNames;
      if (!option || !names) { note.hidden = true; return; }
      const shared = option.dataset.shared === 'true';
      note.hidden = false;
      note.style.color = shared ? 'var(--green-dark)' : 'var(--amber-text)';
      $('span', note).textContent = shared
        ? `Операция будет видна: ${names}`
        : 'Счёт скрыт, операцию увидите только вы';
    };

    typeInputs.forEach(i => i.addEventListener('change', applyType));
    if (account) account.addEventListener('change', applyVisibility);
    form.addEventListener('reset', () => setTimeout(() => { applyType(); applyVisibility(); }));
    applyType();
    applyVisibility();
  });
})();
