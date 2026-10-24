(() => {
  const TOKEN_KEY = 'nurture.session';
  const USER_KEY = 'nurture.user';
  function token() { return sessionStorage.getItem(TOKEN_KEY); }
  function saveSession(auth) {
    if (!auth?.token) throw new Error('The server did not return an authentication token.');
    sessionStorage.setItem(TOKEN_KEY, auth.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: auth.id, name: auth.name, email: auth.email, role: auth.role }));
  }
  function clearSession() { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(USER_KEY); }
  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const jwt = token();
    if (jwt) headers.set('Authorization', `Bearer ${jwt}`);
    let response;
    try { response = await fetch(path, { ...options, headers }); }
    catch (_) { throw new Error('Could not reach the server. Make sure the application is running.'); }
    if (response.status === 401 || response.status === 403) {
      if (jwt && !path.startsWith('/api/auth/')) { clearSession(); location.replace('/login.html'); }
      throw new Error(response.status === 401 ? 'Email or password is incorrect.' : 'This action is not permitted.');
    }
    if (response.status === 204) return null;
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
    if (!response.ok) {
      const message = data && typeof data === 'object' ? (data.detail || data.message || data.error) : null;
      throw new Error(message || `Request failed (${response.status}).`);
    }
    return data;
  }
  window.NurtureAPI = {
    request, token, saveSession, clearSession,
    user() { try { return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'); } catch (_) { return null; } },
    login: body => request('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),
    register: body => request('/api/auth/register', { method: 'POST', body: JSON.stringify(body) })
  };
})();
