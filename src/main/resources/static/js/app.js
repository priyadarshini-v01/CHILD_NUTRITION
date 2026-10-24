(() => {
  const api = window.NurtureAPI;
  if (!api.token()) { location.replace('/login.html'); return; }
  const $ = selector => document.querySelector(selector);
  const $$ = selector => [...document.querySelectorAll(selector)];
  const today = new Date().toISOString().slice(0, 10);
  let children = [], activeChild = null, growthChart = null, dashboardChart = null, reportChart = null, reportData = null;
  let foodOptions = [], foodSearchTimer, foodCategoriesLoaded = false;
  const user = api.user() || {};

  function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch])); }
  function shortDate(value) { return value ? new Date(`${value}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—'; }
  function number(value, places = 1) { return value === null || value === undefined ? 'Unknown' : Number(value).toLocaleString(undefined, { maximumFractionDigits: places }); }
  function setEmpty(name, show) { const el = $(`#${name}`); if (el) el.classList.toggle('d-none', !show); }
  function toast(message, kind = 'success') {
    const node = document.createElement('div'); node.className = `toast align-items-center text-bg-${kind} border-0`; node.role = 'status';
    node.innerHTML = `<div class="d-flex"><div class="toast-body">${escapeHtml(message)}</div><button class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button></div>`;
    $('#toast-wrap').append(node); const instance = new bootstrap.Toast(node, { delay: 3600 }); instance.show(); node.addEventListener('hidden.bs.toast', () => node.remove());
  }
  function formBody(form, fields) {
    const data = new FormData(form), body = {};
    fields.forEach(key => { const raw = data.get(key); body[key] = raw === null || raw === '' ? null : raw; });
    ['quantity', 'servingSize', 'calories', 'protein', 'carbohydrates', 'fat', 'saturatedFat', 'sugar', 'fiber', 'sodium', 'heightCm', 'weightKg', 'calorieTarget', 'proteinTarget', 'carbohydrateTarget', 'fatTarget'].forEach(k => {
      if (k in body && body[k] !== null) body[k] = Number(body[k]);
    });
    return body;
  }
  function updateMealPreview() {
    const form = $('#meal-form'), selected = foodOptions.find(food => food.foodName === form.elements.foodSearch.value);
    form.elements.foodId.value = selected ? selected.id : '';
    form.elements.foodCategory.value = selected?.category || '';
    $('#portion-unit').textContent = selected?.servingUnit || 'g';
    $('#food-provenance').textContent = selected ? `${selected.dataQuality || 'Source status unavailable'} · ${selected.sourceReference || selected.sourceName || 'Source not recorded'}${selected.sourceRetrievedAt ? ` · retrieved ${selected.sourceRetrievedAt}` : ''}` : '';
    const grams = Number(form.elements.quantity.value || 0);
    [['previewCalories', 'calories'], ['previewProtein', 'protein'], ['previewCarbohydrates', 'carbohydrates'], ['previewFat', 'fat']].forEach(([field, nutrient]) => {
      form.elements[field].value = !selected || grams <= 0 ? '' : selected[nutrient] == null ? 'Unknown' : (Number(selected[nutrient]) * grams / 100).toFixed(2).replace(/\.00$/, '');
    });
  }
  async function searchFoods(query) {
    if (!query.trim()) { foodOptions = []; $('#food-options').replaceChildren(); updateMealPreview(); return; }
    const category = $('#food-category-filter').value;
    foodOptions = await api.request(`/api/foods/search?q=${encodeURIComponent(query.trim())}${category ? `&category=${encodeURIComponent(category)}` : ''}`);
    $('#food-options').innerHTML = foodOptions.map(food => `<option value="${escapeHtml(food.foodName)}" label="${escapeHtml(food.category)} · ${escapeHtml(food.dataQuality)}"></option>`).join('');
    updateMealPreview();
  }
  async function loadFoodCategories() {
    if (foodCategoriesLoaded) return;
    const categories = await api.request('/api/foods/categories');
    $('#food-category-filter').innerHTML = '<option value="">All categories</option>' + categories.map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join('');
    foodCategoriesLoaded = true;
  }
  async function run(action, successMessage) {
    try { await action(); if (successMessage) toast(successMessage); }
    catch (error) { toast(error.message, 'danger'); }
  }
  function activateView(name) {
    $$('.view').forEach(v => v.classList.toggle('active', v.id === `view-${name}`));
    $$('.side-link[data-view]').forEach(a => a.classList.toggle('active', a.dataset.view === name));
    $('#sidebar').classList.remove('show');
    if (!activeChild) return;
    if (name === 'dashboard') loadDashboard();
    if (name === 'meals') loadMeals();
    if (name === 'growth') loadGrowth();
    if (name === 'targets') loadTargets();
    if (name === 'insights') loadInsights();
    if (name === 'reports') loadReport();
  }
  function selectChild(id) {
    activeChild = children.find(c => String(c.id) === String(id)) || null;
    $('#child-select').value = activeChild ? String(activeChild.id) : '';
    $('#dashboard-content').classList.toggle('d-none', !activeChild);
    $('#no-child').classList.toggle('d-none', !!activeChild || children.length > 0);
    if (!activeChild && children.length) $('#no-child').classList.add('d-none');
    if (activeChild) activateView($('.view.active')?.id.replace('view-', '') || 'dashboard');
  }
  function renderChildSelect() {
    const previous = activeChild?.id;
    $('#child-select').innerHTML = '<option value="">Choose a child</option>' + children.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
    $('#child-count').textContent = `${children.length} ${children.length === 1 ? 'profile' : 'profiles'}`;
    const next = children.find(c => c.id === previous) || children[0] || null;
    selectChild(next?.id || '');
    renderChildrenTable();
  }
  function renderChildrenTable() {
    $('#children-table').innerHTML = children.map(c => `<tr><td class="fw-semibold">${escapeHtml(c.name)}</td><td>${shortDate(c.dateOfBirth)}</td><td>${escapeHtml(c.gender)}</td><td class="text-end text-nowrap"><button class="btn btn-sm btn-light" data-child-edit="${c.id}" aria-label="Edit ${escapeHtml(c.name)}"><i class="bi bi-pencil"></i></button> <button class="btn btn-sm btn-light text-danger" data-child-delete="${c.id}" aria-label="Delete ${escapeHtml(c.name)}"><i class="bi bi-trash"></i></button></td></tr>`).join('');
    setEmpty('children-empty', !children.length);
  }
  async function loadChildren() {
    children = await api.request('/api/children'); renderChildSelect();
    if (!children.length) { $('#no-child').classList.remove('d-none'); $('#dashboard-content').classList.add('d-none'); }
  }
  function recommendationMarkup(items, compact = false) {
    if (!items?.length) return `<div class="${compact ? 'small-label' : 'empty-state'}">${compact ? 'No new suggestions today. Keep up the good work!' : 'Recommendations will appear as your child’s profile grows.'}</div>`;
    return items.map(item => `<div class="recommendation"><span class="rec-mark"><i class="bi bi-check2"></i></span><div class="small">${escapeHtml(item)}</div></div>`).join('');
  }
  function alertMarkup(items, compact = false) {
    if (!items?.length) return `<div class="${compact ? 'small-label' : 'empty-state'}">${compact ? 'No alerts. You’re all caught up.' : 'You’re all caught up.'}</div>`;
    return items.map(a => `<div class="alert-row"><div class="d-flex justify-content-between gap-2"><div><span class="alert-dot ${String(a.severity).toLowerCase() === 'high' ? 'high' : ''}"></span><strong class="small">${escapeHtml(a.alertType || 'Nutrition update')}</strong><div class="small mt-1">${escapeHtml(a.message)}</div><div class="small-label mt-1">${shortDate((a.createdAt || '').slice(0, 10))} · ${escapeHtml(a.severity || 'Info')}</div></div>${a.read ? '<span class="badge text-bg-light align-self-start">Read</span>' : `<button class="btn btn-sm btn-outline-primary align-self-start" data-alert-read="${a.id}">Mark read</button>`}</div></div>`).join('');
  }
  async function loadDashboard() {
    if (!activeChild) return;
    const d = await api.request(`/api/children/${activeChild.id}/dashboard?date=${today}`);
    $('#dash-greeting').textContent = `A healthy day for ${d.child.name} starts with little wins.`;
    const n = d.dailyNutrition || {};
    $('#stat-calories').textContent = number(n.calories, 0); $('#stat-meals').textContent = `${n.mealCount || 0} ${n.mealCount === 1 ? 'meal' : 'meals'} logged today`;
    $('#daily-protein').textContent = `${number(n.protein)} g`; $('#daily-carbs').textContent = `${number(n.carbohydrates)} g`; $('#daily-fat').textContent = `${number(n.fat)} g`;
    const latest = d.growth?.latest;
    $('#stat-height').textContent = latest ? number(latest.heightCm) : '—'; $('#stat-weight').textContent = latest ? number(latest.weightKg) : '—';
    $('#stat-bmi').textContent = latest ? number(latest.bmi, 2) : '—'; $('#stat-bmi-note').textContent = latest?.bmiCategory || 'Based on latest record';
    $('#stat-height-note').textContent = latest ? `${shortDate(latest.recordDate)} · ${number(d.growth.heightChangeCm)} cm change` : 'Add a growth record';
    $('#stat-weight-note').textContent = latest ? `${shortDate(latest.recordDate)} · ${number(d.growth.weightChangeKg)} kg change` : 'Add a growth record';
    $('#dash-recommendations').innerHTML = recommendationMarkup(d.recommendations, true); $('#dash-alerts').innerHTML = alertMarkup(d.alerts?.slice(0, 3), true);
    const history = await api.request(`/api/children/${activeChild.id}/growth`);
    drawGrowthChart(history, 'dash-growth-chart', true);
  }
  function drawGrowthChart(records, canvasId, compact = false) {
    const rows = [...records].reverse(), labels = rows.map(r => shortDate(r.recordDate));
    const config = { type: 'line', data: { labels, datasets: [
      { label: 'Height (cm)', data: rows.map(r => Number(r.heightCm)), borderColor: '#3f795f', backgroundColor: '#3f795f', tension: .32, yAxisID: 'y' },
      { label: 'Weight (kg)', data: rows.map(r => Number(r.weightKg)), borderColor: '#e1a951', backgroundColor: '#e1a951', tension: .32, yAxisID: 'y1' },
      ...(!compact ? [{ label: 'BMI', data: rows.map(r => Number(r.bmi)), borderColor: '#6387a5', backgroundColor: '#6387a5', tension: .32, yAxisID: 'y2' }] : [])
    ] }, options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 7 } } }, scales: { y: { beginAtZero: false, title: { display: true, text: 'cm' } }, y1: { beginAtZero: false, position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: 'kg' } }, ...(!compact ? { y2: { display: false } } : {}) } } };
    const canvas = document.getElementById(canvasId); if (!canvas || !window.Chart) return;
    const prior = canvasId === 'growth-chart' ? growthChart : dashboardChart; if (prior) prior.destroy();
    const chart = new Chart(canvas, config); if (canvasId === 'growth-chart') growthChart = chart; else dashboardChart = chart;
  }
  async function loadGrowth() {
    const rows = await api.request(`/api/children/${activeChild.id}/growth`);
    $('#growth-table').innerHTML = rows.map(r => `<tr><td>${shortDate(r.recordDate)}</td><td>${number(r.heightCm)} cm</td><td>${number(r.weightKg)} kg</td><td>${number(r.bmi, 2)}</td><td><span class="badge rounded-pill badge-soft">${escapeHtml(r.bmiCategory || '—')}</span></td><td><button class="btn btn-sm btn-light text-danger" data-growth-delete="${r.id}" aria-label="Delete record"><i class="bi bi-trash"></i></button></td></tr>`).join('');
    setEmpty('growth-empty', !rows.length); drawGrowthChart(rows, 'growth-chart');
  }
  async function loadMeals() {
    await loadFoodCategories();
    const date = $('#meal-date-filter').value || today; $('#meal-date-filter').value = date;
    const [rows, totals] = await Promise.all([api.request(`/api/children/${activeChild.id}/meals?date=${date}`), api.request(`/api/children/${activeChild.id}/nutrition/daily?date=${date}`)]);
    $('#meal-total-calories').textContent = number(totals.calories, 0); $('#meal-total-protein').textContent = `${number(totals.protein)} g`; $('#meal-total-carbs').textContent = `${number(totals.carbohydrates)} g`; $('#meal-total-fat').textContent = `${number(totals.fat)} g`;
    $('#meal-total-sugar').textContent = `${number(totals.sugar)} g`; $('#meal-total-fiber').textContent = `${number(totals.fiber)} g`; $('#meal-total-sodium').textContent = `${number(totals.sodium)} mg`; $('#meal-total-saturated-fat').textContent = `${number(totals.saturatedFat)} g`;
    $('#meals-table').innerHTML = rows.map(m => `<tr><td class="fw-semibold">${escapeHtml(m.foodName)}</td><td><span class="badge rounded-pill badge-soft">${escapeHtml(m.mealType)}</span></td><td>${escapeHtml((m.mealTime || '').slice(0, 5) || '—')}</td><td>${number(m.calories, 0)} kcal</td><td><button class="btn btn-sm btn-light text-danger" data-meal-delete="${m.id}" aria-label="Delete meal"><i class="bi bi-trash"></i></button></td></tr>`).join(''); setEmpty('meals-empty', !rows.length);
  }
  async function loadTargets() {
    const rows = await api.request(`/api/children/${activeChild.id}/nutrition-targets`);
    $('#targets-list').innerHTML = rows.map(t => `<div class="subtle-bg mb-3"><div class="d-flex justify-content-between"><strong>${escapeHtml(t.ageGroup)}</strong><button class="btn btn-sm btn-light text-danger" data-target-delete="${t.id}" aria-label="Delete target"><i class="bi bi-trash"></i></button></div><div class="row mt-2 g-2 small"><div class="col-6">Energy <strong>${number(t.calorieTarget, 0)} kcal</strong></div><div class="col-6">Protein <strong>${number(t.proteinTarget)} g</strong></div><div class="col-6">Carbohydrates <strong>${number(t.carbohydrateTarget)} g</strong></div><div class="col-6">Fat <strong>${number(t.fatTarget)} g</strong></div></div></div>`).join(''); setEmpty('targets-empty', !rows.length);
  }
  async function loadInsights() {
    const base = `/api/children/${activeChild.id}`; const [recommendations, alerts] = await Promise.all([api.request(`${base}/recommendations?date=${today}`), api.request(`${base}/alerts?date=${today}`)]);
    $('#recommendations-list').innerHTML = recommendationMarkup(recommendations); $('#alerts-list').innerHTML = alertMarkup(alerts); setEmpty('recommendations-empty', !recommendations.length); setEmpty('alerts-empty', !alerts.length);
  }
  async function loadReport() {
    const period = $('#report-period').value, month = $('#report-month').value;
    const url = period === 'weekly' ? `/api/children/${activeChild.id}/reports/weekly?date=${today}` : `/api/children/${activeChild.id}/reports/monthly${month ? `?month=${month}` : ''}`;
    reportData = await api.request(url); $('#report-range').textContent = `${shortDate(reportData.start)} — ${shortDate(reportData.end)}`;
    const days = reportData.days || []; $('#report-table').innerHTML = days.map(day => `<tr><td>${shortDate(day.date)}</td><td>${day.nutrition.mealCount || 0}</td><td>${number(day.nutrition.calories, 0)} kcal</td><td>${number(day.nutrition.protein)} g</td><td>${number(day.nutrition.carbohydrates)} g</td><td>${number(day.nutrition.fat)} g</td><td>${number(day.nutrition.saturatedFat)} g</td><td>${number(day.nutrition.sugar)} g</td><td>${number(day.nutrition.fiber)} g</td><td>${number(day.nutrition.sodium)} mg</td></tr>`).join('');
    setEmpty('report-empty', days.every(day => !day.nutrition.mealCount));
    if (window.Chart) { if (reportChart) reportChart.destroy(); reportChart = new Chart($('#report-chart'), { type: 'bar', data: { labels: days.map(day => new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })), datasets: [{ label: 'Calories', data: days.map(day => day.nutrition.calories == null ? null : Number(day.nutrition.calories)), backgroundColor: '#79a989', borderRadius: 7 }, { label: 'Protein (g)', data: days.map(day => day.nutrition.protein == null ? null : Number(day.nutrition.protein)), backgroundColor: '#e6bd73', borderRadius: 7 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } } }); }
  }

  $('#today-label').textContent = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
  $('#user-name').textContent = user.name || 'Parent'; $('#user-initial').textContent = (user.name || 'P').trim().charAt(0).toUpperCase();
  $('#logout').addEventListener('click', () => { api.clearSession(); location.replace('/login.html'); });
  $('#menu-toggle').addEventListener('click', () => $('#sidebar').classList.toggle('show'));
  $$('.side-link[data-view]').forEach(link => link.addEventListener('click', () => activateView(link.dataset.view)));
  $$('[data-view-button]').forEach(btn => btn.addEventListener('click', () => activateView(btn.dataset.viewButton)));
  $('#child-select').addEventListener('change', event => selectChild(event.target.value));
  $$('[data-action="new-child"]').forEach(btn => btn.addEventListener('click', () => { activateView('children'); $('#child-form').reset(); $('#child-form').elements.id.value = ''; $('#child-form-title').textContent = 'Add a child'; $('#child-cancel').classList.add('d-none'); }));
  $('#child-form').addEventListener('submit', event => { event.preventDefault(); run(async () => {
    const form = event.currentTarget, id = form.elements.id.value; const body = formBody(form, ['name', 'dateOfBirth', 'gender', 'allergies', 'dietaryPreferences']);
    await api.request(`/api/children${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) }); form.reset(); form.elements.id.value = ''; $('#child-form-title').textContent = 'Add a child'; $('#child-cancel').classList.add('d-none'); await loadChildren();
  }, 'Child profile saved.'); });
  $('#child-cancel').addEventListener('click', () => { $('#child-form').reset(); $('#child-form').elements.id.value = ''; $('#child-form-title').textContent = 'Add a child'; $('#child-cancel').classList.add('d-none'); });
  $('#children-table').addEventListener('click', event => {
    const edit = event.target.closest('[data-child-edit]'), del = event.target.closest('[data-child-delete]');
    if (edit) { const child = children.find(c => String(c.id) === edit.dataset.childEdit); const form = $('#child-form'); Object.entries(child).forEach(([k, v]) => { if (form.elements[k]) form.elements[k].value = v || ''; }); form.elements.id.value = child.id; $('#child-form-title').textContent = 'Edit child profile'; $('#child-cancel').classList.remove('d-none'); form.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
    if (del) { const child = children.find(c => String(c.id) === del.dataset.childDelete); if (confirm(`Delete ${child.name} and their associated records?`)) run(async () => { await api.request(`/api/children/${child.id}`, { method: 'DELETE' }); await loadChildren(); }, 'Child profile deleted.'); }
  });
  $('#meal-date-filter').value = today; $('#meal-date-filter').addEventListener('change', () => run(loadMeals));
  $('#meal-form').elements.mealDate.value = today;
  $('#meal-form').elements.foodSearch.addEventListener('input', event => { clearTimeout(foodSearchTimer); foodSearchTimer = setTimeout(() => run(() => searchFoods(event.target.value)), 180); });
  $('#meal-form').elements.foodSearch.addEventListener('change', updateMealPreview);
  $('#food-category-filter').addEventListener('change', event => { clearTimeout(foodSearchTimer); run(() => searchFoods($('#meal-form').elements.foodSearch.value)); });
  $('#meal-form').elements.quantity.addEventListener('input', updateMealPreview);
  $('#meal-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { const form = event.currentTarget; updateMealPreview(); if (!form.elements.foodId.value) throw new Error('Select a food from the dataset suggestions.'); const body = formBody(form, ['mealDate', 'mealTime', 'mealType', 'foodId', 'quantity', 'notes']); body.foodId = Number(body.foodId); await api.request(`/api/children/${activeChild.id}/meals`, { method: 'POST', body: JSON.stringify(body) }); form.reset(); form.elements.mealDate.value = today; form.elements.quantity.value = '100'; foodOptions = []; $('#food-options').replaceChildren(); updateMealPreview(); await loadMeals(); }, 'Meal added to the journal.'); });
  $('#custom-food-toggle').addEventListener('click', () => $('#custom-food-form').classList.toggle('d-none'));
  $('#custom-food-form').addEventListener('submit', event => { event.preventDefault(); run(async () => {
    const form = event.currentTarget;
    const body = formBody(form, ['foodName', 'servingSize', 'servingUnit', 'calories', 'protein', 'carbohydrates', 'fat', 'saturatedFat', 'sugar', 'fiber', 'sodium', 'sourceReference']);
    const saved = await api.request('/api/foods/custom', { method: 'POST', body: JSON.stringify(body) });
    form.reset(); $('#custom-food-form').classList.add('d-none');
    const mealForm = $('#meal-form'); mealForm.elements.foodSearch.value = saved.foodName;
    await searchFoods(saved.foodName); mealForm.elements.quantity.value = String(saved.servingSize || 100);
    updateMealPreview();
  }, 'Custom food saved. It is now available in food search.'); });
  $('#meals-table').addEventListener('click', event => { const button = event.target.closest('[data-meal-delete]'); if (button && confirm('Remove this meal from the journal?')) run(async () => { await api.request(`/api/children/${activeChild.id}/meals/${button.dataset.mealDelete}`, { method: 'DELETE' }); await loadMeals(); }, 'Meal removed.'); });
  $('#growth-form').elements.recordDate.value = today;
  $('#growth-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { const form = event.currentTarget, body = formBody(form, ['recordDate', 'heightCm', 'weightKg', 'notes']); await api.request(`/api/children/${activeChild.id}/growth`, { method: 'POST', body: JSON.stringify(body) }); form.reset(); form.elements.recordDate.value = today; await loadGrowth(); }, 'Growth measurement saved.'); });
  $('#growth-table').addEventListener('click', event => { const button = event.target.closest('[data-growth-delete]'); if (button && confirm('Delete this growth record?')) run(async () => { await api.request(`/api/children/${activeChild.id}/growth/${button.dataset.growthDelete}`, { method: 'DELETE' }); await loadGrowth(); }, 'Growth record deleted.'); });
  $('#target-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { const body = formBody(event.currentTarget, ['ageGroup', 'calorieTarget', 'proteinTarget', 'carbohydrateTarget', 'fatTarget']); await api.request(`/api/children/${activeChild.id}/nutrition-targets`, { method: 'POST', body: JSON.stringify(body) }); event.currentTarget.reset(); await loadTargets(); }, 'Nutrition target saved.'); });
  $('#targets-list').addEventListener('click', event => { const button = event.target.closest('[data-target-delete]'); if (button && confirm('Delete this nutrition target?')) run(async () => { await api.request(`/api/children/${activeChild.id}/nutrition-targets/${button.dataset.targetDelete}`, { method: 'DELETE' }); await loadTargets(); }, 'Nutrition target deleted.'); });
  $('#alerts-list').addEventListener('click', event => { const button = event.target.closest('[data-alert-read]'); if (button) run(async () => { await api.request(`/api/children/${activeChild.id}/alerts/${button.dataset.alertRead}/read`, { method: 'PATCH' }); await loadInsights(); }, 'Alert marked as read.'); });
  $('#report-period').addEventListener('change', event => $('#report-month').classList.toggle('d-none', event.target.value !== 'monthly'));
  $('#load-report').addEventListener('click', () => run(loadReport));
  $('#export-report').addEventListener('click', () => {
    if (!reportData) return;
    const rows = [['Date', 'Meals', 'Calories (kcal)', 'Protein (g)', 'Carbohydrates (g)', 'Fat (g)', 'Saturated fat (g)', 'Sugar (g)', 'Fiber (g)', 'Sodium (mg)'], ...(reportData.days || []).map(d => [d.date, d.nutrition.mealCount, d.nutrition.calories, d.nutrition.protein, d.nutrition.carbohydrates, d.nutrition.fat, d.nutrition.saturatedFat, d.nutrition.sugar, d.nutrition.fiber, d.nutrition.sodium])];
    const csv = rows.map(row => row.map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n'); const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); link.download = `nurture-report-${reportData.start}-${reportData.end}.csv`; link.click(); URL.revokeObjectURL(link.href);
  });
  run(loadChildren).catch(error => toast(error.message, 'danger'));
})();





