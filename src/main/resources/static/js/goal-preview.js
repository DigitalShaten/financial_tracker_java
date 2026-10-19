// Предпросмотр расчёта цели - та же формула, что в GoalCalculator.java.
// Окончательный расчёт всё равно делает сервер при сохранении.
(() => {
  const form = document.getElementById('goalPlanForm');
  if (!form) return;
  const saved = Number(form.dataset.saved);
  const freeCash = Number(form.dataset.freeCash);
  const cur = form.dataset.currency;
  const [startYear, startMonth] = form.dataset.month.split('-').map(Number);
  const fmt = n => Math.round(n).toLocaleString('ru-RU');
  const money = n => fmt(n) + ' ' + cur;
  const plural = (n, one, few, many) => {
    const a = n % 10, b = n % 100;
    if (b >= 11 && b <= 14) return many;
    if (a === 1) return one;
    if (a >= 2 && a <= 4) return few;
    return many;
  };
  const formats = {
    money: v => money(Number(v)),
    years: v => v + ' ' + plural(Number(v), 'год', 'года', 'лет'),
    percent: v => String(v).replace('.', ',') + '%'
  };
  const monthName = date => date.toLocaleDateString('ru-RU', { month: 'long' }) + ' ' + date.getFullYear();
  const set = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };

  const recalc = () => {
    const price = Number(form.price.value);
    const months = Number(form.years.value) * 12;
    const inflation = Number(form.inflation.value);
    const rate = Number(form.rate.value);

    const future = price * Math.pow(1 + inflation / 100, months / 12);
    const r = rate / 100 / 12;
    const growth = Math.pow(1 + r, months);
    let payment = r === 0 ? (future - saved) / months
                          : (future - saved * growth) * r / (growth - 1);
    payment = Math.max(payment, 0);
    const contributions = payment * months;
    const interest = Math.max(future - saved - contributions, 0);

    set('gPmt', fmt(payment));
    set('gFuture', money(future));
    set('gContrib', money(contributions));
    set('gInterest', '+ ' + money(interest));
    set('gInfl', '+ ' + money(future - price));
    const target = new Date(startYear, startMonth - 1 + months, 1);
    set('gMonths', `${months} ${plural(months, 'ежемесячный взнос', 'ежемесячных взноса', 'ежемесячных взносов')}, до: ${monthName(target)}`);
    form.querySelectorAll('output[data-for]').forEach(o => {
      o.textContent = formats[o.dataset.format](form[o.dataset.for].value);
    });

    // Реалистичность - те же пороги, что в GoalService.assessRealism
    const status = document.getElementById('gStatus');
    let ok, title, text;
    if (freeCash <= 0) {
      ok = false; title = 'Свободных денег в последние месяцы не оставалось';
      text = 'За три последних месяца расходы не меньше доходов — сначала стоит сократить траты.';
    } else {
      const ratio = payment / freeCash, percent = Math.round(ratio * 100);
      if (ratio <= 0.75) {
        ok = true; title = 'Цель достижима';
        text = `Взнос — ${percent}% от среднего свободного остатка за 3 месяца (${money(freeCash)} в месяц).`;
      } else if (ratio <= 1) {
        ok = false; title = 'Получится, но впритык';
        text = `Взнос забирает ${percent}% свободных денег. Любые непредвиденные траты собьют план — подумайте о сроке чуть длиннее.`;
      } else {
        ok = false; title = 'При текущих тратах не успеть';
        text = `Не хватает ${money(payment - freeCash)} в месяц. Увеличьте срок или сократите расходы на эту сумму.`;
      }
    }
    status.className = 'status ' + (ok ? 'ok' : 'warn');
    status.querySelector('b').textContent = title;
    status.querySelector('span').textContent = text;
  };

  form.addEventListener('input', recalc);
  // Подписи ползунков сразу в человеческом формате
  form.querySelectorAll('output[data-for]').forEach(o => {
    o.textContent = formats[o.dataset.format](form[o.dataset.for].value);
  });
})();
