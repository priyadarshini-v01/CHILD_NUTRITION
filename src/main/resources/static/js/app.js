(() => {
  const api = window.NurtureAPI;
  if (!api.token()) { location.replace('/login.html'); return; }
  const $ = selector => document.querySelector(selector);
  const $$ = selector => [...document.querySelectorAll(selector)];
  const today = new Date().toISOString().slice(0, 10);
  let children = [], activeChild = null, growthChart = null, dashboardChart = null, reportChart = null, reportData = null;
  let foodOptions = [], foodSearchTimer, foodSearchGeneration = 0, foodCategoriesLoaded = false;
  let guideFoods = [], guideTimer, dashboardNutrition = null;
  let pendingForm = null;
  let outstandingRuns = 0;
  const requestIndicator = document.createElement('div');
  requestIndicator.className = 'position-fixed top-0 end-0 m-3 px-3 py-2 bg-white rounded shadow-sm small d-none';
  requestIndicator.style.zIndex = '1090'; requestIndicator.setAttribute('role', 'status'); requestIndicator.setAttribute('aria-live', 'polite');
  requestIndicator.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Loading…';
  document.body.append(requestIndicator);
  const user = api.user() || {};

  function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch])); }
  function shortDate(value) { return value ? new Date(`${value}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—'; }
  function number(value, places = 1) { return value === null || value === undefined ? 'Unknown' : Number(value).toLocaleString(undefined, { maximumFractionDigits: places }); }
  function setEmpty(name, show) { const el = $(`#${name}`); if (el) el.classList.toggle('d-none', !show); }
  function toast(message, kind = 'success') {
    const node = document.createElement('div'); node.className = `toast align-items-center text-bg-${kind} border-0`; node.role = kind === 'danger' ? 'alert' : 'status';
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
  async function searchFoods(query, generation = ++foodSearchGeneration) {
    if (!query.trim()) { if (generation !== foodSearchGeneration) return; foodOptions = []; $('#food-options').replaceChildren(); updateMealPreview(); return; }
    const category = $('#food-category-filter').value;
    let results;
    try { results = await api.request(`/api/foods/search?q=${encodeURIComponent(query.trim())}${category ? `&category=${encodeURIComponent(category)}` : ''}`); }
    catch (error) { if (generation !== foodSearchGeneration) return; throw error; }
    if (generation !== foodSearchGeneration) return;
    foodOptions = results;
    $('#food-options').innerHTML = foodOptions.map(food => `<option value="${escapeHtml(food.foodName)}" label="${escapeHtml(food.category)} · ${escapeHtml(food.dataQuality)}"></option>`).join('');
    updateMealPreview();
  }
  async function loadFoodCategories() {
    if (foodCategoriesLoaded) return;
    const categories = await api.request('/api/foods/categories');
    $('#food-category-filter').innerHTML = '<option value="">All categories</option>' + categories.map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join('');
    foodCategoriesLoaded = true;
  }
  function showFieldErrors(form, errors) {
    Object.entries(errors || {}).forEach(([key, message]) => {
      const fieldName = key.split('.').pop(), field = form.elements.namedItem(fieldName);
      if (!field || !field.classList) return;
      field.classList.add('is-invalid'); field.setAttribute('aria-invalid', 'true');
      const label = field.closest('.form-label') || field.parentElement;
      let feedback = label.querySelector(`[data-field-error="${fieldName}"]`) || label.querySelector(`[data-error="${fieldName}"]`);
      if (!feedback) { feedback = document.createElement('div'); feedback.className = 'invalid-feedback d-block'; feedback.dataset.fieldError = fieldName; label.append(feedback); }
      feedback.textContent = message; feedback.classList.remove('d-none');
      if (feedback.id) field.setAttribute('aria-describedby', feedback.id);
      else { feedback.id = `${form.id}-${fieldName}-error`; field.setAttribute('aria-describedby', feedback.id); }
      const alert = form.querySelector('[role="alert"]');
      if (alert) { alert.textContent = 'This record could not be saved. Please correct the highlighted fields.'; alert.classList.remove('d-none'); }
    });
  }
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.dataset.pending === 'true') { event.preventDefault(); event.stopImmediatePropagation(); return; }
    if (form.id === 'child-form') {
      const name = form.elements.name;
      name.setCustomValidity(name.value.trim() ? '' : "Please enter the child's name.");
    }
    if (form.id === 'meal-form') {
      const food = form.elements.foodSearch;
      food.setCustomValidity(form.elements.foodId.value ? '' : 'Select a food from the suggestions.');
    }
    form.classList.add('was-validated');
    if (form.id !== 'growth-form' && !form.checkValidity()) { event.preventDefault(); event.stopImmediatePropagation(); form.reportValidity(); return; }
    pendingForm = form;
  }, true);
  document.addEventListener('input', event => {
    const field = event.target;
    if (!field.form) return;
    field.setCustomValidity?.('');
    field.classList.remove('is-invalid'); field.removeAttribute('aria-invalid');
    const feedback = field.closest('.form-label')?.querySelector(`[data-field-error="${field.name}"]`);
    if (feedback) feedback.remove();
    const predefined = field.closest('.form-label')?.querySelector(`[data-error="${field.name}"]`);
    if (predefined) { predefined.textContent = ''; predefined.classList.add('d-none'); }
    const alert = field.form.querySelector('[role="alert"]');
    if (alert && !field.form.querySelector('.is-invalid')) { alert.textContent = ''; alert.classList.add('d-none'); }
  });
  document.addEventListener('change', event => {
    if (event.target.form) event.target.dispatchEvent(new Event('input', { bubbles: true }));
  });
  let pendingActionButton = null;
  document.addEventListener('click', event => {
    const button = event.target.closest('button, a.side-link');
    if (!button || button.disabled) return;
    pendingActionButton = button;
    setTimeout(() => { if (pendingActionButton === button) pendingActionButton = null; }, 0);
  }, true);
  async function run(action, successMessage) {
    const form = pendingForm; pendingForm = null;
    const button = form?.querySelector('button[type="submit"]') || pendingActionButton; pendingActionButton = null;
    const original = button?.innerHTML;
    outstandingRuns++; requestIndicator.classList.remove('d-none');
    if (form) form.dataset.pending = 'true';
    if (button) { button.disabled = true; button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span><span>Loading…</span>'; }
    try { const result = await action(); if (successMessage && result !== false) toast(successMessage); }
    catch (error) { if (form && error.fieldErrors) showFieldErrors(form, error.fieldErrors); toast(error.message || 'This record could not be saved. Please try again.', 'danger'); }
    finally { outstandingRuns--; if (!outstandingRuns) requestIndicator.classList.add('d-none'); if (form) delete form.dataset.pending; if (button) { button.disabled = false; button.innerHTML = original; } }
  }
  function activateView(name) {
    $$('.view').forEach(v => v.classList.toggle('active', v.id === `view-${name}`));
    $$('.side-link[data-view]').forEach(a => a.classList.toggle('active', a.dataset.view === name));
    $('#sidebar').classList.remove('show');
    if (name === 'food-guide') { run(loadFoodGuide); return; }
    if (name === 'nutrition-assistant') return;
    if (!activeChild) return;
    if (name === 'dashboard') run(loadDashboard);
    if (name === 'meals') run(loadMeals);
    if (name === 'growth') run(loadGrowth);
    if (name === 'targets') run(loadTargets);
    if (name === 'insights') run(loadInsights);
    if (name === 'reports') run(loadReport);
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
    dashboardNutrition = n;
    $('#stat-calories').textContent = number(n.calories, 0); $('#stat-meals').textContent = `${n.mealCount || 0} ${n.mealCount === 1 ? 'meal' : 'meals'} logged today`;
    $('#daily-protein').textContent = `${number(n.protein)} g`; $('#daily-carbs').textContent = `${number(n.carbohydrates)} g`; $('#daily-fat').textContent = `${number(n.fat)} g`;
    const nutrientRow = $('#daily-protein').closest('.row');
    if (nutrientRow && !$('#dashboard-fiber')) nutrientRow.insertAdjacentHTML('beforeend', '<div class="col-6 col-md-3"><div class="subtle-bg"><div id="dashboard-fiber" class="fw-bold fs-5"></div><div class="small-label">Fibre</div></div></div><div class="col-6 col-md-3"><div class="subtle-bg"><div class="fw-bold fs-5">Unknown</div><div class="small-label">Calcium</div></div></div><div class="col-6 col-md-3"><div class="subtle-bg"><div class="fw-bold fs-5">Unknown</div><div class="small-label">Iron</div></div></div>');
    if ($('#dashboard-fiber')) $('#dashboard-fiber').textContent = `${number(n.fiber)} g`;
    installNutrientExplainer();
    const latest = d.growth?.latest;
    $('#stat-height').textContent = latest ? number(latest.heightCm) : '—'; $('#stat-weight').textContent = latest ? number(latest.weightKg) : '—';
    $('#stat-bmi').textContent = latest ? number(latest.bmi, 2) : '—'; $('#stat-bmi-note').textContent = 'Calculated BMI; not a diagnosis';
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
    ] }, options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 7 } } }, scales: { y: { beginAtZero: false, title: { display: true, text: 'Height (cm)' } }, y1: { beginAtZero: false, position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: 'Weight (kg)' } }, ...(!compact ? { y2: { beginAtZero: false, position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: 'BMI (kg/m^2)' } } } : {}) } } };
    const canvas = document.getElementById(canvasId); if (!canvas || !window.Chart) return;
    const prior = canvasId === 'growth-chart' ? growthChart : dashboardChart; if (prior) prior.destroy();
    const chart = new Chart(canvas, config); if (canvasId === 'growth-chart') growthChart = chart; else dashboardChart = chart;
  }
  async function loadGrowth() {
    const rows = await api.request(`/api/children/${activeChild.id}/growth`);
    $('#growth-table').innerHTML = rows.map(r => `<tr><td>${shortDate(r.recordDate)}</td><td>${number(r.heightCm)} cm</td><td>${number(r.weightKg)} kg</td><td>${number(r.bmi, 2)}</td><td><span class="badge rounded-pill badge-soft">${escapeHtml(r.bmiCategory || 'Calculated BMI')}</span></td><td><button class="btn btn-sm btn-light text-danger" data-growth-delete="${r.id}" aria-label="Delete record"><i class="bi bi-trash"></i></button></td></tr>`).join('');
    setEmpty('growth-empty', !rows.length); drawGrowthChart(rows, 'growth-chart');
  }
  const guideNutrients = [['calories','Calories','kcal'],['protein','Protein','g'],['carbohydrates','Carbohydrate','g'],['fat','Fat','g'],['fiber','Fibre','g'],['sugar','Sugar','g'],['sodium','Sodium','mg'],['calcium','Calcium','mg'],['iron','Iron','mg'],['vitaminC','Vitamin C','mg'],['folate','Folate','µg']];
  async function loadFoodGuide() {
    const categories = await api.request('/api/foods/categories');
    const category = $('#guide-category');
    if (category.options.length === 1) category.innerHTML += categories.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
    const q = $('#guide-search').value.trim();
    guideFoods = await api.request(`/api/foods${q ? `?query=${encodeURIComponent(q)}${category.value ? `&category=${encodeURIComponent(category.value)}` : ''}` : category.value ? `?category=${encodeURIComponent(category.value)}` : ''}`);
    renderGuideFoods();
  }
  function renderGuideFoods() {
    $('#guide-results').innerHTML = guideFoods.slice(0,30).map(f => `<div class="col-md-6"><article class="panel h-100"><h2 class="section-heading">${escapeHtml(f.foodName)}</h2><div class="small-label">${escapeHtml(f.category || 'Uncategorised')} · ${escapeHtml(f.dataQuality || 'Status unavailable')}</div><div class="small mt-2">Per ${escapeHtml(f.nutritionBasis || '100 g')}: ${f.calories == null ? 'calories unavailable' : `${number(f.calories)} kcal`}, ${f.protein == null ? 'protein unavailable' : `${number(f.protein)} g protein`}</div><button class="btn btn-sm btn-outline-primary mt-3" data-guide-detail="${f.id}">Food details</button></article></div>`).join('');
    setEmpty('guide-empty', !guideFoods.length);
    const options = guideFoods.map(f => `<option value="${f.id}">${escapeHtml(f.foodName)}</option>`).join('');
    ['#compare-one','#compare-two'].forEach(sel => { const old = $(sel).value; $(sel).innerHTML = options; if ([...$(sel).options].some(o => o.value === old)) $(sel).value = old; });
    compareGuideFoods();
  }
  function sourceLink(food) {
    const match = String(food.sourceReference || '').match(/https?:\/\/[^\s)]+/);
    if (match) return `<a href="${escapeHtml(match[0].replace(/[),.]$/, ''))}" target="_blank" rel="noopener">${escapeHtml(food.sourceName || 'Source reference')}</a>`;
    return escapeHtml(food.sourceName || 'Source not recorded');
  }
  async function showGuideDetail(id) {
    const f = await api.request(`/api/foods/${id}`), box = $('#guide-detail');
    box.innerHTML = `<h2 class="section-heading">${escapeHtml(f.foodName)}</h2><p class="small-label">${escapeHtml(f.dataQuality || 'Status unavailable')} · Source: ${sourceLink(f)} · Basis: ${escapeHtml(f.nutritionBasis || 'not recorded')}</p><p class="small-label">Standard serving: ${f.servingSize == null ? 'not recorded' : `${number(f.servingSize)} ${escapeHtml(f.servingUnit || (/ml/i.test(f.nutritionBasis || '') ? 'ml' : 'g'))}`} ${f.servingBasis ? `(${escapeHtml(f.servingBasis)})` : ''}</p><div id="detail-per-100" class="small mb-2"></div><label class="form-label" for="detail-amount">Serving amount (${/ml/i.test(f.nutritionBasis || '') ? 'ml' : 'g'})</label><input id="detail-amount" class="form-control mb-3" type="number" min="0.1" max="10000" step="0.1" value="${escapeHtml(f.servingSize || 100)}"><div id="detail-nutrients" class="small"></div><div class="small-label mt-3">A zero is shown as zero; a blank database value is shown as unavailable. Values are scaled from the recorded 100 g or 100 ml basis.</div>`;
    box.classList.remove('d-none');
    const update = async () => {
      const amount = Number($('#detail-amount').value);
      if (!Number.isFinite(amount) || amount <= 0 || amount > 10000) { $('#detail-nutrients').textContent = 'Enter a serving greater than zero and no more than 10000 g or ml.'; return; }
      const portion = await api.request(`/api/foods/${id}/portion?amount=${encodeURIComponent(amount)}`);
      $('#detail-per-100').innerHTML = `<strong>Per 100 ${portion.unit}</strong>: ` + guideNutrients.map(([key,label,unit]) => `${label}: ${f[key] == null ? 'unavailable' : `${number(f[key])} ${unit}`}`).join(' · ');
      $('#detail-nutrients').innerHTML = guideNutrients.map(([key,label,unit]) => `<div class="d-flex justify-content-between border-bottom py-1"><span>${label}</span><strong>${portion.nutrients[key === 'fiber' ? 'fiberG' : key === 'protein' ? 'proteinG' : key === 'carbohydrates' ? 'carbohydratesG' : key === 'fat' ? 'fatG' : key === 'sugar' ? 'sugarG' : key === 'sodium' ? 'sodiumMg' : key === 'calcium' ? 'calciumMg' : key === 'iron' ? 'ironMg' : key === 'vitaminC' ? 'vitaminCMg' : key === 'folate' ? 'folate' : 'caloriesKcal'] == null ? 'Unavailable' : `${number(portion.nutrients[key === 'fiber' ? 'fiberG' : key === 'protein' ? 'proteinG' : key === 'carbohydrates' ? 'carbohydratesG' : key === 'fat' ? 'fatG' : key === 'sugar' ? 'sugarG' : key === 'sodium' ? 'sodiumMg' : key === 'calcium' ? 'calciumMg' : key === 'iron' ? 'ironMg' : key === 'vitaminC' ? 'vitaminCMg' : key === 'folate' ? 'folate' : 'caloriesKcal'])} ${unit}`}</strong></div>`).join('');
    };
    $('#detail-amount').addEventListener('input', () => run(update)); await update();
  }
  async function compareGuideFoods() {
    const a = $('#compare-one').value, b = $('#compare-two').value, amount = Number($('#compare-amount').value), out = $('#compare-results');
    if (!a || !b) { out.textContent = 'Choose two foods from the current search results.'; return; }
    if (!Number.isFinite(amount) || amount <= 0 || amount > 10000) { out.textContent = 'Enter a valid serving amount greater than zero.'; return; }
    try {
      const [x,y] = await Promise.all([api.request(`/api/foods/${a}/portion?amount=${amount}`), api.request(`/api/foods/${b}/portion?amount=${amount}`)]);
      if (x.unit !== y.unit) { out.textContent = 'These foods use different bases (g and ml), so they cannot be compared on the same amount.'; return; }
      const keys = [['caloriesKcal','Calories','kcal'],['proteinG','Protein','g'],['carbohydratesG','Carbohydrate','g'],['fatG','Fat','g'],['fiberG','Fibre','g'],['calciumMg','Calcium','mg'],['ironMg','Iron','mg']];
      out.innerHTML = `<div class="small-label mb-2">Per ${amount} ${x.unit}</div><div class="fw-semibold">${escapeHtml(x.food.foodName)} · ${escapeHtml(y.food.foodName)}</div>` + keys.map(([k,l,u]) => `<div class="d-flex justify-content-between border-bottom py-1"><span>${l}</span><span>${x.nutrients[k] == null ? 'Unavailable' : `${number(x.nutrients[k])} ${u}`} · ${y.nutrients[k] == null ? 'Unavailable' : `${number(y.nutrients[k])} ${u}`}</span></div>`).join('');
    } catch (error) { out.textContent = error.message || 'Unable to compare these foods right now.'; }
  }
  function installNutrientExplainer() {
    const panel = $('#daily-protein')?.closest('.panel'); if (!panel || $('#dashboard-nutrient-explainer')) return;
    const wrap = document.createElement('div'); wrap.id='dashboard-nutrient-explainer'; wrap.className='mt-3';
    wrap.innerHTML='<div class="small-label mb-2">Explain this value</div><div class="d-flex flex-wrap gap-2"></div><p class="small mt-2 mb-0" aria-live="polite"></p>';
    const definitions = { calories:['Calories measure energy in food. The intake shown is only from foods logged today; it is not an individualized target.','calories','kcal'],protein:['Protein supports growth and tissue repair. Logged intake is not a diagnosis or a complete-day assessment.','protein','g'],carbohydrates:['Carbohydrates provide energy. Logged intake only includes foods recorded in this app.','carbohydrates','g'],fat:['Fat provides energy and supports normal body functions. Logged intake may be incomplete.','fat','g'],fibre:['Fibre is found in plant foods and supports digestion. A daily total is unavailable in this summary.','fiber','g'],calcium:['Calcium supports bones and teeth. Calcium is not included in this daily summary; unavailable does not mean zero.','calcium','mg'],iron:['Iron helps the body make haemoglobin. Iron is not included in this daily summary; unavailable does not mean zero.','iron','mg'] };
    const buttons=wrap.querySelector('div.d-flex');
    Object.entries(definitions).forEach(([label,[description,key,unit]])=>{const button=document.createElement('button');button.type='button';button.className='btn btn-sm btn-outline-secondary';button.textContent=label[0].toUpperCase()+label.slice(1);button.addEventListener('click',()=>{const intake=dashboardNutrition?.[key];wrap.querySelector('p').textContent=`${description} Recorded today: ${intake == null ? 'unavailable' : `${number(intake)} ${unit}`}. No age-specific target is inferred here.`;});buttons.append(button);});
    panel.append(wrap);
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
    const period = $('#report-period').value, month = $('#report-month').value, date=$('#report-date').value || today;
    const dateInput=$('#report-date'), monthInput=$('#report-month'); dateInput.classList.toggle('d-none',period==='monthly'); monthInput.classList.toggle('d-none',period!=='monthly');
    if (period === 'daily') {
      $('#export-report').disabled=true;
      const daily=await api.request(`/api/children/${activeChild.id}/reports/daily?date=${date}`);
      $('#report-range').textContent=`Daily summary for ${shortDate(daily.date)}${daily.childAgeYears == null ? '' : ` · age ${daily.childAgeYears}`} · ${escapeHtml(daily.childSex || 'sex not recorded')}`;
      $('#daily-report-panel').classList.remove('d-none'); renderDailyReport(daily);
      $('#report-table').replaceChildren(); setEmpty('report-empty',true);
      if(reportChart){reportChart.destroy();reportChart=null;} return;
    }
    $('#export-report').disabled=false;
    $('#daily-report-panel').classList.add('d-none');
    const url = period === 'weekly' ? `/api/children/${activeChild.id}/reports/weekly?date=${date}` : `/api/children/${activeChild.id}/reports/monthly${month ? `?month=${month}` : ''}`;
    reportData = await api.request(url); $('#report-range').textContent = `${shortDate(reportData.start)} — ${shortDate(reportData.end)}`;
    const days = reportData.days || []; $('#report-table').innerHTML = days.map(day => `<tr><td>${shortDate(day.date)}</td><td>${day.nutrition.mealCount || 0}</td><td>${number(day.nutrition.calories, 0)} kcal</td><td>${number(day.nutrition.protein)} g</td><td>${number(day.nutrition.carbohydrates)} g</td><td>${number(day.nutrition.fat)} g</td><td>${number(day.nutrition.saturatedFat)} g</td><td>${number(day.nutrition.sugar)} g</td><td>${number(day.nutrition.fiber)} g</td><td>${number(day.nutrition.sodium)} mg</td><td>${day.incomplete ? 'Incomplete food data' : 'Recorded values complete'}</td></tr>`).join('');
    setEmpty('report-empty', days.every(day => !day.nutrition.mealCount));
    if (window.Chart) { if (reportChart) reportChart.destroy(); reportChart = new Chart($('#report-chart'), { type: 'bar', data: { labels: days.map(day => new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })), datasets: [{ label: 'Calories', data: days.map(day => day.nutrition.calories == null ? null : Number(day.nutrition.calories)), backgroundColor: '#79a989', borderRadius: 7 }, { label: 'Protein (g)', data: days.map(day => day.nutrition.protein == null ? null : Number(day.nutrition.protein)), backgroundColor: '#e6bd73', borderRadius: 7 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } } }); }
  }
  function renderDailyReport(report) {
    const panel=$('#daily-report-panel');
    const cards=report.nutrients.map(n=>{
      const actual=n.actual==null?'Unknown (food data missing)':`${number(n.actual)} ${n.unit}`;
      const reference=n.reference?`${number(n.reference.amount)} ${n.reference.unit} · ${escapeHtml(n.reference.name)} · ${escapeHtml(n.reference.ageRange)} · ${escapeHtml(n.reference.version)}`:'Reference unavailable';
      const progress=n.percentReached==null?'<div class="small-label">Progress percentage unavailable because no validated age- and sex-specific reference is configured.</div>':`<div class="progress" role="progressbar" aria-label="${escapeHtml(n.name)} daily reference progress" aria-valuenow="${Math.min(100,Number(n.percentReached))}" aria-valuemin="0" aria-valuemax="100"><div class="progress-bar" style="width:${Math.min(100,Number(n.percentReached))}%"></div></div><div class="small">${number(n.percentReached)}% reached · ${n.difference==null?'':`${number(n.difference)} ${n.unit} ${n.difference>=0?'above':'remaining'}`}</div>`;
      const examples=(n.foodsThatContainNutrient||[]).map(f=>`${escapeHtml(f.name)} (${escapeHtml(f.dataQuality)}, ${escapeHtml(f.sourceName)})`).join(', ');
      return `<div class="col-md-6 col-xl-4"><article class="panel h-100"><h3 class="section-heading">${escapeHtml(n.name)}</h3><p class="mb-1"><strong>Recorded intake:</strong> ${actual}</p><p class="small mb-2"><strong>Daily reference:</strong> ${reference}</p>${progress}<p class="small mt-2 mb-1">${escapeHtml(n.explanation)}</p><div class="small-label">${n.complete?'Food values complete for this nutrient.':'Incomplete: at least one logged food has no value or an unsupported unit.'}</div><div class="small mt-2"><strong>Foods that contain this nutrient:</strong> ${examples||'No matching values recorded in the visible food catalogue.'}</div></article></div>`;
    }).join('');
    const groups=(report.mealBreakdown||[]).map(g=>`<tr><td>${escapeHtml(g.mealType)}</td><td>${g.mealCount}</td><td>${number(g.nutrition.calories,0)} kcal</td><td>${number(g.nutrition.protein)} g</td><td>${number(g.nutrition.carbohydrates)} g</td><td>${number(g.nutrition.fat)} g</td></tr>`).join('');
    const gaps=(report.nutrients||[]).filter(n=>n.possibleGap).map(n=>`<li><strong>${escapeHtml(n.name)}:</strong> ${number(n.actual)} ${escapeHtml(n.unit)} recorded vs ${number(n.reference?.amount)} ${escapeHtml(n.unit)} reference; difference ${number(n.difference)} ${escapeHtml(n.unit)}. This may be a gap in today's food log, not proof of deficiency. Foods that contain it: ${(n.foodsThatContainNutrient||[]).map(f=>escapeHtml(f.name)).join(', ')||'none found'}.</li>`).join('');
    panel.innerHTML=`<div class="panel mb-3"><h2 class="section-heading">Daily intake and reference</h2><p class="small-label">A reference is shown only when a validated age- and sex-specific value is available. No versioned reference set is configured, so this report does not calculate percentages or label gaps. See the <a href="https://www.nin.res.in/RDA_Full_Report_2024.html" target="_blank" rel="noopener">ICMR–NIN RDA and EAR 2020 reference</a>. Family-entered targets are not clinical references.</p><div class="row g-3">${cards}</div></div><div class="panel mb-3"><h2 class="section-heading">What this means</h2><p>${escapeHtml(report.whatThisMeans)}</p>${report.incomplete?`<div class="alert alert-warning" role="status"><strong>Report incomplete.</strong> ${(report.incompletenessReasons||[]).map(escapeHtml).join(' ')}</div>`:'<p class="small-label">All values shown have recorded data. This still reflects only foods entered in the app.</p>'}<p>${gaps?`Possible gaps in the recorded log:<ul>${gaps}</ul>`:'No validated reference-based gap can be calculated from the available reference data.'}</p></div><div class="panel mb-3"><h2 class="section-heading">Meal-wise breakdown</h2><div class="table-responsive"><table class="table"><thead><tr><th>Meal</th><th>Entries</th><th>Calories</th><th>Protein</th><th>Carbohydrate</th><th>Fat</th></tr></thead><tbody>${groups||'<tr><td colspan="6">No meals recorded for this day.</td></tr>'}</tbody></table></div></div><div class="panel"><h2 class="section-heading">Foods that may help</h2><p class="small-label">General educational examples from the visible food database; these are not prescriptions and do not imply a deficiency.</p><p>Protein: ${escapeHtml((report.nutrients.find(n=>n.name==='Protein')?.foodsThatContainNutrient||[]).map(f=>f.name).join(', ')||'No recorded examples')}. Iron: ${escapeHtml((report.nutrients.find(n=>n.name==='Iron')?.foodsThatContainNutrient||[]).map(f=>f.name).join(', ')||'No recorded examples')}. Calcium: ${escapeHtml((report.nutrients.find(n=>n.name==='Calcium')?.foodsThatContainNutrient||[]).map(f=>f.name).join(', ')||'No recorded examples')}.</p><p class="small-label mb-0">For concerns about growth, poor intake, allergies, or possible deficiency, speak with a pediatrician or qualified dietitian. Do not use this report to decide on supplements.</p></div>`;
  }

  $('#today-label').textContent = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
  $('#child-form').elements.dateOfBirth.max = today;
  $('#meal-form').elements.mealDate.max = today;
  $('#meal-date-filter').max = today;
  $('#growth-form').elements.recordDate.max = today;
  $('#meal-form').elements.quantity.max = '10000';
  $('#custom-food-form').elements.servingSize.max = '10000';
  ['calories','protein','carbohydrates','fat','saturatedFat','sugar','fiber','sodium'].forEach(name => { $('#custom-food-form').elements[name].max = '100000'; });
  const ageGroupField = $('#target-form').elements.ageGroup;
  ageGroupField.maxLength = 40; ageGroupField.pattern = '0-1|1-3|4-8|9-13|14-18';
  ageGroupField.placeholder = '0-1, 1-3, 4-8, 9-13, or 14-18';
  ageGroupField.title = 'Use one of the supported groups: 0-1, 1-3, 4-8, 9-13, or 14-18.';
  $('#report-month').max = today.slice(0, 7); $('#report-date').max=today; $('#report-date').value=today;
  ['allergies', 'dietaryPreferences'].forEach(name => { $('#child-form').elements[name].maxLength = 4000; });
  ['notes'].forEach(name => { $('#meal-form').elements[name].maxLength = 2000; $('#growth-form').elements[name].maxLength = 2000; });
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
  $('#guide-search').addEventListener('input', () => { clearTimeout(guideTimer); guideTimer=setTimeout(()=>run(loadFoodGuide),250); });
  $('#guide-category').addEventListener('change', () => run(loadFoodGuide));
  $('#guide-results').addEventListener('click', event => { const button=event.target.closest('[data-guide-detail]'); if(button) run(()=>showGuideDetail(button.dataset.guideDetail)); });
  $('#compare-one').addEventListener('change', compareGuideFoods); $('#compare-two').addEventListener('change', compareGuideFoods); $('#compare-amount').addEventListener('input', compareGuideFoods);
  $('#assistant-suggestions').addEventListener('click', event => { const button=event.target.closest('button'); if(button) { $('#assistant-question').value=button.textContent; $('#assistant-form').requestSubmit(); } });
  $('#assistant-form').addEventListener('submit', event => {
    event.preventDefault(); const form=event.currentTarget, question=$('#assistant-question').value.trim(); if(!question || form.dataset.pending === 'true') return;
    const chat=$('#assistant-chat'); const history=[...chat.querySelectorAll('[data-chat-role]')].slice(-10).map(node=>({role:node.dataset.chatRole,content:node.dataset.chatRole==='user'?node.textContent:node.querySelector('.chat-answer')?.textContent||''})).filter(turn=>turn.content);
    const userMessage=document.createElement('div'); userMessage.dataset.chatRole='user'; userMessage.className='alert alert-primary ms-auto mb-0'; userMessage.style.maxWidth='85%'; userMessage.textContent=question; chat.append(userMessage); form.elements[0].value='';
    run(async()=>{
      const response=await api.request('/api/nutrition-assistant/ask',{method:'POST',body:JSON.stringify({question,childId:activeChild?.id||null,history,reportDate:$('#report-date')?.value||null})});
      const article=document.createElement('article'); article.dataset.chatRole='assistant'; article.className='alert alert-light border me-auto mb-0'; article.style.maxWidth='95%';
      const answer=document.createElement('p'); answer.className='mb-2 chat-answer'; answer.textContent=response.answer||'I could not answer that question.'; article.append(answer);
      if(response.foods?.length){const foods=document.createElement('div');foods.className='small mb-2';foods.textContent=response.foods.map(f=>`${f.foodName}: ${f.dataQuality||'status unavailable'}; ${f.sourceName||'source not recorded'}`).join(' \u00b7 ');article.append(foods);}
      if(response.dataNote){const note=document.createElement('div');note.className='small-label';note.textContent=response.dataNote;article.append(note);}
      if(response.references?.length){const list=document.createElement('div');list.className='small mt-2';response.references.forEach(ref=>{const span=document.createElement('span');span.className='me-3';if(/^https:\/\//.test(ref.url||'')){const a=document.createElement('a');a.href=ref.url;a.target='_blank';a.rel='noopener';a.textContent=ref.name;span.append(a);}else span.textContent=ref.name+' (link unavailable)';list.append(span);});article.append(list);}
      chat.append(article); chat.scrollTop=chat.scrollHeight;
    },null);
  });
  $('#meal-form').elements.mealDate.value = today;
  $('#meal-form').elements.foodSearch.addEventListener('input', event => { const generation = ++foodSearchGeneration; clearTimeout(foodSearchTimer); foodSearchTimer = setTimeout(() => run(() => searchFoods(event.target.value, generation)), 180); });
  $('#meal-form').elements.foodSearch.addEventListener('change', () => { $('#meal-form').elements.foodSearch.setCustomValidity(''); updateMealPreview(); });
  $('#food-category-filter').addEventListener('change', event => { clearTimeout(foodSearchTimer); const generation = ++foodSearchGeneration; run(() => searchFoods($('#meal-form').elements.foodSearch.value, generation)); });
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
  const growthForm = $('#growth-form'), growthAlert = $('#growth-validation-alert');
  const bmiCard = document.createElement('section'); bmiCard.id = 'bmi-result-card'; bmiCard.className = 'panel mt-3 d-none'; bmiCard.setAttribute('aria-live', 'polite'); growthForm.after(bmiCard);
  const growthWarning = document.createElement('div'); growthWarning.className = 'alert alert-warning d-none'; growthWarning.setAttribute('role', 'alert'); growthAlert.after(growthWarning);
  let growthChallenge = null;
  function clearGrowthChallenge() { growthChallenge = null; growthWarning.classList.add('d-none'); growthWarning.replaceChildren(); }
  function showGrowthChallenge(error, body) {
    growthChallenge = { body, token: error.confirmationToken };
    growthWarning.replaceChildren();
    const message = document.createElement('p'); message.className = 'mb-2'; message.textContent = error.message || 'Please verify these measurements before saving.';
    const review = document.createElement('button'); review.type = 'button'; review.className = 'btn btn-sm btn-outline-secondary me-2'; review.textContent = 'Review measurements';
    review.addEventListener('click', () => { clearGrowthChallenge(); growthForm.elements.heightCm.focus(); });
    const confirmSave = document.createElement('button'); confirmSave.type = 'button'; confirmSave.className = 'btn btn-sm btn-warning'; confirmSave.textContent = 'Confirm and save';
    confirmSave.addEventListener('click', () => {
      if (!validateGrowth()) return;
      const pending = growthChallenge; if (!pending) return;
      pendingForm = growthForm;
      run(async () => {
        const payload = { ...pending.body, confirmationToken: pending.token };
        try { await api.request(`/api/children/${activeChild.id}/growth`, { method: 'POST', body: JSON.stringify(payload) }); }
        catch (error) { if (error.status === 409 && error.confirmationToken) { showGrowthChallenge(error, pending.body); return false; } growthAlert.textContent = error.message; growthAlert.classList.remove('d-none'); throw error; }
        clearGrowthChallenge(); growthForm.reset(); growthForm.elements.recordDate.value = today; validateGrowth(); await loadGrowth();
      }, 'Growth measurement saved.');
    });
    growthWarning.append(message, review, confirmSave); growthWarning.classList.remove('d-none');
  }
  growthForm.elements.recordDate.value = today;
  function validateGrowth() {
    const height = Number(growthForm.elements.heightCm.value), weight = Number(growthForm.elements.weightKg.value);
    const recordDate = growthForm.elements.recordDate.value, dob = activeChild?.dateOfBirth;
    const age = dob && recordDate ? calendarAge(dob, recordDate) : -1;
    const band = age < 1 ? [40,100,1,30] : age < 3 ? [50,120,2,40] : age < 6 ? [60,150,5,80] : age < 13 ? [80,210,10,180] : [100,230,20,250];
    const problems = {};
    if (!growthForm.elements.heightCm.value || !Number.isFinite(height) || height <= 0 || height < 30 || height > 250) problems.heightCm = 'Enter a positive height in centimeters (30 to 250 cm).';
    else if (age >= 0 && age < 18 && (height < band[0] || height > band[1])) problems.heightCm = 'Please verify the height. The entered value may use the wrong unit.';
    if (!growthForm.elements.weightKg.value || !Number.isFinite(weight) || weight <= 0 || weight < .5 || weight > 300) problems.weightKg = 'Enter a positive weight in kilograms (0.5 to 300 kg).';
    else if (age >= 0 && age < 18 && (weight < band[2] || weight > band[3])) problems.weightKg = 'Please verify the weight in kilograms; it may use the wrong unit.';
    if (!recordDate) problems.form = 'Please enter a measurement date.';
    else if (recordDate > today) problems.form = 'The measurement date cannot be in the future.';
    else if (!dob || !/^\d{4}-\d{2}-\d{2}$/.test(dob)) problems.form = 'Please add or correct the child\'s date of birth before recording this measurement.';
    else if (recordDate < dob) problems.form = 'The measurement date cannot be before the child\'s date of birth.';
    else if (age < 0) problems.form = 'Please add or correct the child\'s date of birth before recording this measurement.';
    ['heightCm', 'weightKg'].forEach(name => { const input = growthForm.elements[name], feedback = growthForm.querySelector(`[data-error="${name}"]`); input.classList.toggle('is-invalid', !!problems[name]); if (feedback) { feedback.textContent = problems[name] || ''; feedback.classList.toggle('d-none', !problems[name]); } });
    growthAlert.textContent = problems.form || (problems.heightCm && problems.weightKg ? 'Invalid height or weight. Please enter valid positive values in the expected units.' : problems.heightCm || problems.weightKg || '');
    growthAlert.classList.toggle('d-none', !growthAlert.textContent);
    const validNumbers = !problems.heightCm && !problems.weightKg;
    $('#bmi-preview').textContent = validNumbers ? `Calculated BMI: ${(weight / ((height / 100) ** 2)).toFixed(2)}. This is a measurement, not a diagnosis.` : 'Enter valid height and weight to preview calculated BMI.';
    renderBmiAssessment(validNumbers ? weight / ((height / 100) ** 2) : null, age, height);
    return Object.keys(problems).length === 0;
  }
  function renderBmiAssessment(bmi, age, heightCm) {
    if (bmi == null) { bmiCard.classList.add('d-none'); return; }
    const adult = age >= 18; let status, explanation, recommendation, percent, badge = 'bg-secondary';
    if (!adult) { status = 'BMI-for-age reference required'; explanation = 'Children and adolescents need an age- and sex-specific growth reference for interpretation.'; recommendation = 'Discuss the growth pattern with a pediatrician; do not prescribe weight loss from one BMI reading.'; percent = 50; }
    else if (bmi < 18.5) { status = 'Below the standard range'; explanation = 'This BMI is below the standard adult screening range.'; recommendation = 'Choose nutrient-dense meals and seek professional advice for unexplained weight loss or difficulty gaining weight.'; percent = 18; badge = 'bg-info'; }
    else if (bmi < 25) { status = 'Within the standard range'; explanation = 'This BMI is within the standard adult screening range.'; recommendation = 'Continue balanced meals, regular activity, sufficient sleep and healthy habits.'; percent = 42; badge = 'bg-success'; }
    else if (bmi < 30) { status = 'Above the standard range'; explanation = 'This BMI is above the standard adult screening range.'; recommendation = 'Choose balanced eating and enjoyable physical activity without judgment or extreme dieting.'; percent = 65; badge = 'bg-warning text-dark'; }
    else { status = 'High BMI range'; explanation = 'This BMI is in the high adult screening range.'; recommendation = 'Consider discussing your overall health and sustainable options with a qualified professional.'; percent = 88; badge = 'bg-danger'; }
    const m = heightCm / 100, min = 18.5 * m * m, max = 24.9 * m * m;
    bmiCard.innerHTML = `<div class="d-flex justify-content-between align-items-start"><div><div class="small-label">BMI result</div><div class="display-4 fw-bold">${bmi.toFixed(2)}</div></div><span class="badge rounded-pill ${badge}">${status}</span></div><div class="progress mt-3" style="height:12px"><div class="progress-bar ${badge}" style="width:${percent}%"></div></div><div class="d-flex justify-content-between small-label"><span>Below</span><span>Standard</span><span>Above</span><span>High</span></div><hr><h3 class="section-heading">What this means</h3><p class="small">${explanation}</p><h3 class="section-heading">Recommended next steps</h3><p class="small">${recommendation}</p>${adult ? `<div class="small bg-light rounded p-2"><strong>General adult reference range:</strong> ${min.toFixed(1)}–${max.toFixed(1)} kg at this height. This is not a personalized ideal weight.</div>` : ''}<p class="small-label mt-3 mb-0">BMI is a screening measure, not a diagnosis.</p><a class="btn btn-sm btn-outline-primary mt-3" href="#growth-table">View measurement history</a>`;
    bmiCard.classList.remove('d-none');
  }
  function calendarAge(dob, date) {
    const birth = new Date(`${dob}T00:00:00`), at = new Date(`${date}T00:00:00`);
    if (Number.isNaN(birth.getTime()) || Number.isNaN(at.getTime())) return -1;
    let years = at.getFullYear() - birth.getFullYear();
    if (at.getMonth() < birth.getMonth() || (at.getMonth() === birth.getMonth() && at.getDate() < birth.getDate())) years--;
    return years;
  }
  ['heightCm','weightKg','recordDate'].forEach(name => growthForm.elements[name].addEventListener('input', () => { clearGrowthChallenge(); validateGrowth(); }));
  growthForm.addEventListener('submit', event => { event.preventDefault(); if (!validateGrowth()) { pendingForm = null; return; } run(async () => { const form = event.currentTarget, body = formBody(form, ['recordDate', 'heightCm', 'weightKg', 'notes']); try { await api.request(`/api/children/${activeChild.id}/growth`, { method: 'POST', body: JSON.stringify(body) }); } catch (error) { if (error.status === 409 && error.confirmationToken) { showGrowthChallenge(error, body); return false; } growthAlert.textContent = error.message; growthAlert.classList.remove('d-none'); throw error; } clearGrowthChallenge(); form.reset(); form.elements.recordDate.value = today; validateGrowth(); await loadGrowth(); }, 'Growth measurement saved.'); });
  $('#growth-table').addEventListener('click', event => { const button = event.target.closest('[data-growth-delete]'); if (button && confirm('Delete this growth record?')) run(async () => { await api.request(`/api/children/${activeChild.id}/growth/${button.dataset.growthDelete}`, { method: 'DELETE' }); await loadGrowth(); }, 'Growth record deleted.'); });
  $('#target-form').addEventListener('submit', event => { event.preventDefault(); run(async () => { const body = formBody(event.currentTarget, ['ageGroup', 'calorieTarget', 'proteinTarget', 'carbohydrateTarget', 'fatTarget']); await api.request(`/api/children/${activeChild.id}/nutrition-targets`, { method: 'POST', body: JSON.stringify(body) }); event.currentTarget.reset(); await loadTargets(); }, 'Nutrition target saved.'); });
  $('#targets-list').addEventListener('click', event => { const button = event.target.closest('[data-target-delete]'); if (button && confirm('Delete this nutrition target?')) run(async () => { await api.request(`/api/children/${activeChild.id}/nutrition-targets/${button.dataset.targetDelete}`, { method: 'DELETE' }); await loadTargets(); }, 'Nutrition target deleted.'); });
  $('#alerts-list').addEventListener('click', event => { const button = event.target.closest('[data-alert-read]'); if (button) run(async () => { await api.request(`/api/children/${activeChild.id}/alerts/${button.dataset.alertRead}/read`, { method: 'PATCH' }); await loadInsights(); }, 'Alert marked as read.'); });
  $('#report-period').addEventListener('change', () => run(loadReport)); $('#report-date').addEventListener('change', () => run(loadReport)); $('#report-month').addEventListener('change', () => run(loadReport));
  $('#load-report').addEventListener('click', () => run(loadReport));
  $('#export-report').addEventListener('click', () => {
    if (!reportData) return;
    const rows = [['Date', 'Meals', 'Calories (kcal)', 'Protein (g)', 'Carbohydrates (g)', 'Fat (g)', 'Saturated fat (g)', 'Sugar (g)', 'Fibre (g)', 'Sodium (mg)'], ...(reportData.days || []).map(d => [d.date, d.nutrition.mealCount, d.nutrition.calories, d.nutrition.protein, d.nutrition.carbohydrates, d.nutrition.fat, d.nutrition.saturatedFat, d.nutrition.sugar, d.nutrition.fiber, d.nutrition.sodium])];
    const csv = rows.map(row => row.map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n'); const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); link.download = `nurture-report-${reportData.start}-${reportData.end}.csv`; link.click(); URL.revokeObjectURL(link.href);
  });
  run(loadChildren).catch(error => toast(error.message, 'danger'));
})();


