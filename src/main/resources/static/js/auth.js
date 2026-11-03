(() => {
  const api = window.NurtureAPI;
  if (api.token() && location.pathname.endsWith('login.html')) location.replace('/');
  const alertBox = document.getElementById('auth-alert');
  function showError(message) { alertBox.textContent = message; alertBox.className = 'alert alert-danger'; }
  const authNotice = sessionStorage.getItem('nurture.authNotice');
  if (authNotice) { showError(authNotice); sessionStorage.removeItem('nurture.authNotice'); }
  function showFieldErrors(form, errors) {
    Object.entries(errors || {}).forEach(([name, message]) => {
      const field = form.elements.namedItem(name);
      if (!field) return;
      field.classList.add('is-invalid'); field.setAttribute('aria-invalid', 'true');
      const label = field.closest('.form-label');
      let feedback = label.querySelector('.invalid-feedback');
      if (!feedback) { feedback = document.createElement('div'); feedback.className = 'invalid-feedback'; label.append(feedback); }
      feedback.textContent = message;
    });
  }
  function setBusy(form, busy) {
    const button = form.querySelector('button[type="submit"]');
    if (busy) { button.dataset.original = button.innerHTML; button.disabled = true; button.textContent = 'Please wait…'; }
    else { button.disabled = false; button.innerHTML = button.dataset.original || (form.id === 'login-form' ? 'Sign in →' : 'Create account →'); }
  }
  document.addEventListener('input', event => {
    const field = event.target;
    field.classList.remove('is-invalid'); field.removeAttribute('aria-invalid');
    field.closest('.form-label')?.querySelector('.invalid-feedback')?.remove();
    alertBox.className = 'alert d-none';
  });
  const login = document.getElementById('login-form');
  if (login) login.addEventListener('submit', async event => {
    event.preventDefault(); if (!login.reportValidity()) return;
    alertBox.className = 'alert d-none'; setBusy(login, true);
    const fields = new FormData(login);
    try { api.saveSession(await api.login({ email: fields.get('email').trim(), password: fields.get('password') })); location.replace('/'); }
    catch (error) { showError(error.message); showFieldErrors(login, error.fieldErrors); setBusy(login, false); }
  });
  const register = document.getElementById('register-form');
  if (register) register.addEventListener('submit', async event => {
    event.preventDefault(); if (!register.reportValidity()) return;
    alertBox.className = 'alert d-none'; setBusy(register, true);
    const fields = new FormData(register);
    try {
      api.saveSession(await api.register({ name: fields.get('name').trim(), email: fields.get('email').trim(), password: fields.get('password') }));
      location.replace('/');
    } catch (error) { showError(error.message); showFieldErrors(register, error.fieldErrors); setBusy(register, false); }
  });
})();
