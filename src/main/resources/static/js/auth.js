(() => {
  const api = window.NurtureAPI;
  if (api.token() && location.pathname.endsWith('login.html')) location.replace('/');
  const alertBox = document.getElementById('auth-alert');
  function showError(message) { alertBox.textContent = message; alertBox.className = 'alert alert-danger'; }
  function setBusy(form, busy) { const button = form.querySelector('button[type="submit"]'); button.disabled = busy; button.textContent = busy ? 'Please wait…' : form.id === 'login-form' ? 'Sign in →' : 'Create account →'; }
  const login = document.getElementById('login-form');
  if (login) login.addEventListener('submit', async event => {
    event.preventDefault(); alertBox.className = 'alert d-none'; setBusy(login, true);
    const fields = new FormData(login);
    try { api.saveSession(await api.login({ email: fields.get('email').trim(), password: fields.get('password') })); location.replace('/'); }
    catch (error) { showError(error.message); setBusy(login, false); }
  });
  const register = document.getElementById('register-form');
  if (register) register.addEventListener('submit', async event => {
    event.preventDefault(); alertBox.className = 'alert d-none'; setBusy(register, true);
    const fields = new FormData(register);
    try {
      api.saveSession(await api.register({ name: fields.get('name').trim(), email: fields.get('email').trim(), password: fields.get('password') }));
      location.replace('/');
    } catch (error) { showError(error.message); setBusy(register, false); }
  });
})();
