(() => {
  const TOKEN_KEY = 'nurture.session';
  const USER_KEY = 'nurture.user';
  function token() { return sessionStorage.getItem(TOKEN_KEY); }
  function saveSession(auth) {
    if (!auth?.token) throw new Error('The server did not return an authentication token.');
    sessionStorage.setItem(TOKEN_KEY, auth.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify({ id: auth.id, name: auth.name, email: auth.email, role: auth.role }));
  }
  function clearSession() { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(USER_KEY); sessionStorage.removeItem('nurture.authNotice'); }
  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const jwt = token();
    if (jwt) headers.set('Authorization', `Bearer ${jwt}`);
    const controller = new AbortController(), timeout = setTimeout(() => controller.abort('timeout'), 15000);
    if (options.signal) options.signal.addEventListener('abort', () => controller.abort(), { once: true });
    let response, text;
    try {
      response = await fetch(path, { ...options, headers, signal: controller.signal });
      if (response.status === 204) return null;
      text = await response.text();
    }
    catch (error) {
      if (controller.signal.aborted && controller.signal.reason === 'timeout') throw new Error('The request timed out. Please try again.');
      throw new Error('Could not reach the server. Check your connection and try again.');
    } finally { clearTimeout(timeout); }
    if (response.status === 401) {
      if (jwt && !path.startsWith('/api/auth/')) { clearSession(); sessionStorage.setItem('nurture.authNotice', 'Your session has expired. Please log in again.'); location.replace('/login.html'); throw new Error('Your session has expired. Please log in again.'); }
      throw new Error(path.endsWith('/login') ? 'Email or password is incorrect.' : 'Authentication is required.');
    }
    if (response.status === 403) throw new Error('This action is not permitted.');
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!response.ok) {
      const message = data && typeof data === 'object' && typeof data.message === 'string' ? data.message :
        response.status >= 500 ? 'The request could not be completed. Please try again.' : `Request failed (${response.status}).`;
      const error = new Error(message);
      error.status = response.status;
      error.fieldErrors = data && typeof data === 'object' && data.errors && typeof data.errors === 'object' ? data.errors : {};
      error.code = data && typeof data === 'object' ? data.code : undefined;
      error.confirmationToken = data && typeof data === 'object' ? data.confirmationToken : undefined;
      error.previous = data && typeof data === 'object' ? data.previous : undefined;
      error.entered = data && typeof data === 'object' ? data.entered : undefined;
      throw error;
    }
    if (text && data === null) throw new Error('The server returned an invalid response. Please try again.');
    return data;
  }
  window.NurtureAPI = {
    request, token, saveSession, clearSession,
    user() { try { return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'); } catch (_) { return null; } },
    login: body => request('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),
    register: body => request('/api/auth/register', { method: 'POST', body: JSON.stringify(body) })
  };
})();
